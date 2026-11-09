package com.logvex.crystal.remote.controller;

import com.logvex.crystal.remote.core.LinkConfig;
import com.logvex.crystal.remote.core.LinkListener;
import com.logvex.crystal.remote.core.RemoteLink;
import com.logvex.crystal.remote.core.RemoteThreads;
import com.logvex.crystal.remote.core.RemoteTransport;
import com.logvex.crystal.remote.protocol.CommandRequest;
import com.logvex.crystal.remote.protocol.CommandResult;
import com.logvex.crystal.remote.protocol.CommandStatus;
import com.logvex.crystal.remote.protocol.InstanceSnapshot;
import com.logvex.crystal.remote.protocol.RemoteAction;
import com.logvex.crystal.remote.protocol.RemoteMessage;
import com.logvex.crystal.remote.security.AuditLog;
import com.logvex.crystal.remote.security.CommandAllowlist;
import com.logvex.crystal.remote.security.PairingRecord;
import com.logvex.crystal.remote.security.Permission;
import com.logvex.crystal.remote.security.SessionManager;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The central controller: it accepts agent connections, authenticates them, keeps a registry of
 * instances, and dispatches commands to one, several or all of them.
 *
 * <p>Design rules that the rest of the class follows:
 * <ul>
 *   <li><b>Never block the game thread.</b> Accepting, framing, decoding and the per-command
 *       timeout/retry bookkeeping all run on daemon threads. Public methods that the UI calls
 *       (registry reads, {@link #dispatch}) return immediately or complete on a worker.</li>
 *   <li><b>Never report success without an acknowledgement.</b> {@link #dispatch} records each
 *       instance as {@link CommandStatus#PENDING} until its agent answers; a missing answer
 *       becomes {@link CommandStatus#TIMEOUT} after the per-instance deadline.</li>
 *   <li><b>Capability-check before sending.</b> An instance that does not advertise the required
 *       capability is recorded {@link CommandStatus#UNSUPPORTED} locally, without a round trip,
 *       so a mixed-version fleet gets honest per-instance results.</li>
 *   <li><b>Security on the agent.</b> The controller's permission and allowlist copies exist only
 *       to grey out UI and to short-circuit obvious mismatches; the agent re-checks everything.</li>
 * </ul>
 *
 * <p>Minecraft-independent and unit testable: bind to port 0, connect a stub agent over a real
 * loopback socket, and drive the whole handshake and dispatch pipeline.
 */
public final class RemoteController implements AutoCloseable {

    private final String name;
    private final LinkConfig linkConfig;
    private final AuditLog audit;
    private final SessionManager sessions;
    private final CommandHistory history;

    private final Map<String, InstanceRecord> instances = new ConcurrentHashMap<>();
    private final Map<String, PairingRecord> pairings = new ConcurrentHashMap<>();
    private final Map<String, AgentConnection> connections = new ConcurrentHashMap<>();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong commandCounter = new AtomicLong();
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ExecutorService connectionPool;
    private java.util.concurrent.ScheduledFuture<?> maintenanceTask;
    private volatile long commandTimeoutMillis = 10_000L;
    private volatile int maxRetries = 2;
    private volatile java.util.function.BiConsumer<String, CommandResult> resultListener;
    private volatile Runnable changeListener;

    public RemoteController(String name, AuditLog audit, SessionManager sessions,
                            CommandHistory history, LinkConfig linkConfig) {
        this.name = name == null || name.isBlank() ? "controller" : name;
        this.audit = audit == null ? AuditLog.standard() : audit;
        this.sessions = sessions == null ? SessionManager.standard() : sessions;
        this.history = history == null ? CommandHistory.standard() : history;
        this.linkConfig = linkConfig == null ? LinkConfig.defaults() : linkConfig;
    }

    /** A controller with the standard audit log, sessions and history. */
    public static RemoteController standard(String name) {
        return new RemoteController(name, AuditLog.standard(), SessionManager.standard(),
                CommandHistory.standard(), LinkConfig.defaults());
    }

    public String name() {
        return name;
    }

    public AuditLog audit() {
        return audit;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public CommandHistory history() {
        return history;
    }

    /** How long to wait for a command result before declaring a timeout. */
    public void setCommandTimeoutMillis(long millis) {
        this.commandTimeoutMillis = Math.max(250L, millis);
    }

    public long commandTimeoutMillis() {
        return commandTimeoutMillis;
    }

    /** How many times a safe (idempotent) command may be retried after a timeout. */
    public void setMaxRetries(int retries) {
        this.maxRetries = Math.max(0, Math.min(5, retries));
    }

    public int maxRetries() {
        return maxRetries;
    }

    /** Notified with {@code (requestId, result)} whenever a result arrives; used by the UI. */
    public void setResultListener(java.util.function.BiConsumer<String, CommandResult> listener) {
        this.resultListener = listener;
    }

    /** Notified whenever the instance registry changes, so the UI can refresh. */
    public void setChangeListener(Runnable listener) {
        this.changeListener = listener;
    }

    // ------------------------------------------------------------- pairings

    /** Replaces the in-memory pairings (loaded from the store at startup). */
    public void setPairings(Map<String, PairingRecord> records) {
        pairings.clear();
        if (records != null) {
            pairings.putAll(records);
        }
    }

    public Map<String, PairingRecord> pairings() {
        return Collections.unmodifiableMap(pairings);
    }

    public PairingRecord pairingFor(String instanceId) {
        return instanceId == null ? null : pairings.get(instanceId);
    }

    /**
     * Pairs a new instance: generates a secret and returns the record so the caller can show the
     * pairing code. The caller persists it via the {@link com.logvex.crystal.remote.security.PairingStore}.
     */
    public PairingRecord pair(String instanceId, String name, Set<Permission> permissions) {
        PairingRecord record = PairingRecord.pair(instanceId, name, permissions);
        pairings.put(instanceId, record);
        audit.record(AuditLog.Kind.PAIRING_CREATED, instanceId, "paired as '" + record.name() + "'");
        changed();
        return record;
    }

    /** Revokes a pairing, closes any live session and drops any live connection. */
    public void revoke(String instanceId) {
        PairingRecord record = pairings.get(instanceId);
        if (record != null) {
            record.revoke();
        }
        sessions.closeForInstance(instanceId);
        AgentConnection connection = connections.get(instanceId);
        if (connection != null) {
            connection.link().sendBye("pairing revoked");
        }
        audit.record(AuditLog.Kind.TOKEN_REVOKED, instanceId, "pairing revoked", 2);
        InstanceRecord instance = instances.get(instanceId);
        if (instance != null) {
            instance.setStatus(InstanceStatus.UNAUTHORIZED);
            instance.setPaired(false);
        }
        changed();
    }

    /** Restores a revoked pairing's permissions (the user chose to trust it again). */
    public void unpair(String instanceId) {
        PairingRecord removed = pairings.remove(instanceId);
        if (removed != null) {
            audit.record(AuditLog.Kind.CONFIG_CHANGED, instanceId, "pairing removed", 1);
        }
        sessions.closeForInstance(instanceId);
        changed();
    }

    // ------------------------------------------------------------- server

    /**
     * Starts accepting connections on {@code port}.
     *
     * @return the actual bound port (useful when {@code port} is 0 in tests)
     * @throws IOException when the socket cannot be bound
     */
    public int start(int port) throws IOException {
        if (!running.compareAndSet(false, true)) {
            return serverSocket == null ? -1 : serverSocket.getLocalPort();
        }
        serverSocket = new ServerSocket(port);
        serverSocket.setReuseAddress(true);
        connectionPool = Executors.newCachedThreadPool(RemoteThreads.namedFactory("conn"));
        acceptThread = RemoteThreads.namedFactory("accept").newThread(this::acceptLoop);
        acceptThread.start();
        maintenanceTask = RemoteThreads.scheduler().scheduleAtFixedRate(this::maintenance,
                linkConfig.heartbeatIntervalMillis, linkConfig.heartbeatIntervalMillis,
                TimeUnit.MILLISECONDS);
        return serverSocket.getLocalPort();
    }

    public int port() {
        return serverSocket == null ? -1 : serverSocket.getLocalPort();
    }

    public boolean running() {
        return running.get();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                // No SO_TIMEOUT: a read timeout can fire part-way through a frame and leave the
                // stream desynced. A dead peer is detected by the heartbeat task, which closes
                // the socket and so unblocks the reader instead.
                RemoteTransport transport = new com.logvex.crystal.remote.core.SocketTransport(
                        socket, false, "tcp://" + socket.getRemoteSocketAddress());
                connectionPool.execute(() -> serve(transport));
            } catch (SocketTimeoutException e) {
                // Loop and re-check the running flag.
            } catch (IOException e) {
                if (running.get()) {
                    audit.record(AuditLog.Kind.AGENT_DISCONNECTED, "", "accept failed: " + e.getMessage(), 1);
                }
                return;
            }
        }
    }

    /** Runs one connection's handshake and traffic on its own thread. */
    private void serve(RemoteTransport transport) {
        AgentConnection[] holder = new AgentConnection[1];
        RemoteLink link = new RemoteLink(transport, linkConfig, new LinkListener() {
            @Override
            public void onMessage(RemoteMessage message) {
                // The AgentConnection is its own listener; this indirection exists only because
                // the connection needs the link to construct itself.
                AgentConnection connection = holder[0];
                if (connection != null) {
                    connection.onMessage(message);
                }
            }

            @Override
            public void onClosed(String reason, boolean fatal) {
                AgentConnection connection = holder[0];
                if (connection != null) {
                    connection.onClosed(reason, fatal);
                }
            }

            @Override
            public void onWarning(String message) {
                AgentConnection connection = holder[0];
                if (connection != null) {
                    connection.onWarning(message);
                }
            }
        });
        AgentConnection connection = new AgentConnection(this, link);
        holder[0] = connection;
        link.start();
    }

    // ------------------------------------------------------------- connection events

    void onAgentConnected(AgentConnection connection) {
        if (!connection.instanceId().isBlank()) {
            connections.put(connection.instanceId(), connection);
        }
    }

    void rejectConnection(AgentConnection connection, String reason) {
        audit.record(AuditLog.Kind.AGENT_DISCONNECTED, connection.instanceId(),
                "rejected: " + reason, 1);
        InstanceRecord record = instances.computeIfAbsent(connection.instanceId(),
                key -> new InstanceRecord(key, connection.agentName()));
        record.setStatus(InstanceStatus.INCOMPATIBLE);
        record.setLastError(reason);
        record.setPaired(false);
        changed();
    }

    /**
     * A connected agent whose instance id the controller has not paired. It is shown as awaiting
     * pairing so the user can complete the pairing from the dashboard, but it is never treated as
     * authenticated.
     */
    void onAwaitingPairing(AgentConnection connection) {
        String id = connection.instanceId();
        if (id == null || id.isBlank()) {
            return;
        }
        InstanceRecord record = instances.computeIfAbsent(id,
                key -> new InstanceRecord(key, connection.agentName()));
        record.setName(connection.agentName());
        record.setStatus(InstanceStatus.AWAITING_PAIRING);
        record.setPaired(false);
        record.setConnection(connection);
        audit.record(AuditLog.Kind.PAIRING_REJECTED, id,
                "connected without a pairing", 1);
        changed();
    }

    void onAuthFailure(AgentConnection connection, String reason) {
        audit.record(AuditLog.Kind.AUTH_FAILURE, connection.instanceId(), reason, 2);
        InstanceRecord record = instances.get(connection.instanceId());
        if (record == null) {
            record = new InstanceRecord(connection.instanceId(), connection.agentName());
            instances.put(connection.instanceId(), record);
        }
        record.setStatus(InstanceStatus.UNAUTHORIZED);
        record.setLastError(reason);
        record.setPaired(false);
        changed();
    }

    SessionManager.Session onAuthenticated(AgentConnection connection) {
        String id = connection.instanceId();
        InstanceRecord record = instances.computeIfAbsent(id,
                key -> new InstanceRecord(key, connection.agentName()));
        record.setName(connection.agentName());
        record.setStatus(InstanceStatus.ONLINE);
        record.setPaired(true);
        record.setConnection(connection);
        record.setProtocolVersion(connection.link().protocolVersion());
        record.markHeartbeat(System.currentTimeMillis());
        var session = sessions.open(id);
        audit.record(AuditLog.Kind.AUTH_SUCCESS, id, "authenticated from " + connection.remoteDescription());
        audit.record(AuditLog.Kind.AGENT_CONNECTED, id, connection.remoteDescription());
        changed();
        return session;
    }

    void onAgentDisconnected(AgentConnection connection, String reason, boolean fatal) {
        String id = connection.instanceId();
        if (id == null || id.isBlank()) {
            return;
        }
        connections.remove(id, connection);
        sessions.closeForInstance(id);
        InstanceRecord record = instances.get(id);
        if (record != null) {
            record.setConnection(null);
            if (record.status() != InstanceStatus.UNAUTHORIZED
                    && record.status() != InstanceStatus.INCOMPATIBLE) {
                record.setStatus(fatal ? InstanceStatus.DISCONNECTED : InstanceStatus.OFFLINE);
            }
            record.setLastError(reason);
        }
        audit.record(AuditLog.Kind.AGENT_DISCONNECTED, id, reason);
        changed();
    }

    void onLinkWarning(AgentConnection connection, String message) {
        audit.record(AuditLog.Kind.REPLAY_REJECTED, connection.instanceId(), message, 1);
    }

    void onAgentError(AgentConnection connection, String message) {
        audit.record(AuditLog.Kind.COMMAND_RESULT, connection.instanceId(), "agent error: " + message, 1);
    }

    /** Any inbound message from an identified agent refreshes that instance's liveness. */
    void onPeerActivity(AgentConnection connection) {
        InstanceRecord record = instances.get(connection.instanceId());
        if (record != null) {
            record.markHeartbeat(System.currentTimeMillis());
            // A live message also means the link is healthy again after a transient drop.
            if (record.status() == InstanceStatus.DISCONNECTED && connection.authenticated()) {
                record.setStatus(InstanceStatus.ONLINE);
                record.setConnection(connection);
                changed();
            }
        }
    }

    void onStatus(AgentConnection connection, InstanceSnapshot snapshot) {
        String id = connection.instanceId();
        InstanceRecord record = instances.get(id);
        if (record == null) {
            record = new InstanceRecord(id, connection.agentName());
            instances.put(id, record);
        }
        record.setSnapshot(snapshot);
        record.markHeartbeat(System.currentTimeMillis());
        if (record.status() == InstanceStatus.ONLINE) {
            // Keep the connection handle current in case of a reconnect.
            record.setConnection(connection);
        }
        changed();
    }

    void onCommandResult(AgentConnection connection, CommandResult result) {
        InstanceRecord record = instances.get(result.instanceId());
        String name = record == null ? result.instanceId() : record.name();
        history.record(result, name);
        audit.record(AuditLog.Kind.COMMAND_RESULT, result.instanceId(),
                result.status().wire() + (result.message().isBlank() ? "" : ": " + result.message()));
        java.util.function.BiConsumer<String, CommandResult> listener = resultListener;
        if (listener != null) {
            try {
                listener.accept(result.requestId(), result);
            } catch (RuntimeException ignored) {
                // The UI must never break the pipeline.
            }
        }
        changed();
    }

    // ------------------------------------------------------------- registry

    public List<InstanceRecord> instances() {
        return new ArrayList<>(instances.values());
    }

    public InstanceRecord instance(String instanceId) {
        return instanceId == null ? null : instances.get(instanceId);
    }

    /** Every distinct group name across all instances, sorted. */
    public List<String> groups() {
        java.util.TreeSet<String> all = new java.util.TreeSet<>();
        for (InstanceRecord record : instances.values()) {
            all.addAll(record.groups());
        }
        return new ArrayList<>(all);
    }

    public void rename(String instanceId, String newName) {
        InstanceRecord record = instances.get(instanceId);
        if (record != null) {
            record.setName(newName);
            PairingRecord pairing = pairings.get(instanceId);
            if (pairing != null) {
                pairing.setName(newName);
            }
            audit.record(AuditLog.Kind.CONFIG_CHANGED, instanceId, "renamed to '" + newName + "'");
            changed();
        }
    }

    public void addToGroup(String instanceId, String group) {
        InstanceRecord record = instances.get(instanceId);
        if (record != null) {
            record.addGroup(group);
            audit.record(AuditLog.Kind.CONFIG_CHANGED, instanceId, "joined group '" + group + "'");
            changed();
        }
    }

    public void removeFromGroup(String instanceId, String group) {
        InstanceRecord record = instances.get(instanceId);
        if (record != null) {
            record.removeGroup(group);
            audit.record(AuditLog.Kind.CONFIG_CHANGED, instanceId, "left group '" + group + "'");
            changed();
        }
    }

    // ------------------------------------------------------------- dispatch

    /** The result of asking the controller to run a command against a set of instances. */
    public static final class Dispatch {
        private final String requestId;
        private final CommandHistory.Entry entry;
        private final List<String> targeted;

        Dispatch(String requestId, CommandHistory.Entry entry, List<String> targeted) {
            this.requestId = requestId;
            this.entry = entry;
            this.targeted = List.copyOf(targeted);
        }

        public String requestId() {
            return requestId;
        }

        public CommandHistory.Entry entry() {
            return entry;
        }

        public List<String> targeted() {
            return targeted;
        }
    }

    /**
     * Sends one action to a set of instances.
     *
     * <p>Per instance the controller: skips (as {@link CommandStatus#UNSUPPORTED}) an instance that
     * does not advertise the capability; records {@link CommandStatus#REJECTED} for an offline or
     * unpaired instance; otherwise sends the command and leaves it {@link CommandStatus#PENDING}
     * until the agent answers or the timeout fires.
     *
     * @param instanceIds the targets
     * @param action      the action
     * @param parameters  the action's parameters
     * @return the dispatch handle (request id and the history entry to watch)
     */
    public Dispatch dispatch(List<String> instanceIds, RemoteAction action,
                             Map<String, String> parameters) {
        String requestId = "cmd-" + commandCounter.incrementAndGet() + "-"
                + com.logvex.crystal.remote.protocol.Nonces.fresh();
        CommandHistory.Entry entry = history.begin(requestId, action, action.wire(),
                System.currentTimeMillis());
        List<String> targeted = new ArrayList<>();
        if (instanceIds != null) {
            for (String id : instanceIds) {
                InstanceRecord record = instances.get(id);
                if (record == null) {
                    entry.add(new CommandHistory.Outcome(id, id, CommandStatus.REJECTED,
                            "unknown instance", System.currentTimeMillis()));
                    continue;
                }
                targeted.add(id);
                dispatchOne(requestId, entry, record, action, parameters);
            }
        }
        audit.record(AuditLog.Kind.COMMAND_SENT, "",
                action.wire() + " -> " + targeted.size() + " instance(s)");
        return new Dispatch(requestId, entry, targeted);
    }

    private void dispatchOne(String requestId, CommandHistory.Entry entry, InstanceRecord record,
                             RemoteAction action, Map<String, String> parameters) {
        if (!CapabilityMap.supports(record.snapshot(), action)) {
            entry.add(new CommandHistory.Outcome(record.instanceId(), record.name(),
                    CommandStatus.UNSUPPORTED, "instance does not support " + action.wire(),
                    System.currentTimeMillis()));
            return;
        }
        AgentConnection connection = record.connection();
        if (connection == null || !connection.link().open() || !connection.authenticated()) {
            entry.add(new CommandHistory.Outcome(record.instanceId(), record.name(),
                    CommandStatus.REJECTED, "instance is not connected", System.currentTimeMillis()));
            return;
        }
        PairingRecord pairing = pairings.get(record.instanceId());
        if (pairing != null) {
            Permission needed = Permission.requiredFor(action);
            if (needed != null && !Permission.granted(pairing.permissions(), needed)) {
                audit.record(AuditLog.Kind.PERMISSION_DENIED, record.instanceId(),
                        "no " + needed.wire() + " for " + action.wire(), 1);
                entry.add(new CommandHistory.Outcome(record.instanceId(), record.name(),
                        CommandStatus.REJECTED, "controller lacks " + needed.wire(),
                        System.currentTimeMillis()));
                return;
            }
        }
        CommandRequest request = new CommandRequest(requestId, action, parameters, requestId);
        connection.sendCommand(request);
        entry.add(new CommandHistory.Outcome(record.instanceId(), record.name(),
                CommandStatus.PENDING, "sent", System.currentTimeMillis()));
        scheduleTimeout(requestId, entry, record.instanceId(), action, parameters);
    }

    /**
     * Watches one instance's command for a timeout. Safe, idempotent actions are retried up to
     * {@link #maxRetries()}; a retry re-uses the same request id so a late first answer still
     * counts and the entry is not duplicated.
     */
    private void scheduleTimeout(String requestId, CommandHistory.Entry entry, String instanceId,
                                 RemoteAction action, Map<String, String> parameters) {
        RemoteThreads.scheduler().schedule(() -> {
            if (completedFor(entry, instanceId)) {
                return;
            }
            InstanceRecord record = instances.get(instanceId);
            AgentConnection connection = record == null ? null : record.connection();
            if (action.idempotent() && connection != null && connection.link().open()
                    && connection.inFlight().contains(requestId)
                    && retriesFor(requestId, instanceId) < maxRetries) {
                bumpRetry(requestId, instanceId);
                connection.sendCommand(new CommandRequest(requestId, action, parameters, requestId));
                scheduleTimeout(requestId, entry, instanceId, action, parameters);
                return;
            }
            if (!completedFor(entry, instanceId)) {
                entry.add(new CommandHistory.Outcome(instanceId,
                        record == null ? instanceId : record.name(), CommandStatus.TIMEOUT,
                        "no acknowledgement within " + commandTimeoutMillis + "ms",
                        System.currentTimeMillis()));
                if (connection != null) {
                    connection.forget(requestId);
                }
                audit.record(AuditLog.Kind.COMMAND_RESULT, instanceId, "timeout for " + action.wire(), 1);
                changed();
            }
        }, commandTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    private final Map<String, Integer> retries = new ConcurrentHashMap<>();

    private int retriesFor(String requestId, String instanceId) {
        return retries.getOrDefault(requestId + "|" + instanceId, 0);
    }

    private void bumpRetry(String requestId, String instanceId) {
        retries.merge(requestId + "|" + instanceId, 1, Integer::sum);
    }

    private boolean completedFor(CommandHistory.Entry entry, String instanceId) {
        for (CommandHistory.Outcome outcome : entry.outcomes()) {
            if (outcome.instanceId().equals(instanceId) && outcome.status().terminal()) {
                return true;
            }
        }
        return false;
    }

    /** Cancels a pending command on an instance, if it has not completed. */
    public void cancel(String requestId, String instanceId) {
        CommandHistory.Entry entry = history.byRequestId(requestId);
        if (entry != null && !completedFor(entry, instanceId)) {
            InstanceRecord record = instances.get(instanceId);
            entry.add(new CommandHistory.Outcome(instanceId,
                    record == null ? instanceId : record.name(), CommandStatus.CANCELLED,
                    "cancelled", System.currentTimeMillis()));
            changed();
        }
    }

    // ------------------------------------------------------------- maintenance

    private void maintenance() {
        long now = System.currentTimeMillis();
        for (String expired : sessions.expireIdle(now)) {
            audit.record(AuditLog.Kind.SESSION_EXPIRED, expired, "idle timeout", 1);
            AgentConnection connection = connections.get(expired);
            if (connection != null) {
                connection.link().sendBye("session expired");
            }
        }
        // A link whose socket died without a clean close is marked disconnected here.
        for (InstanceRecord record : instances.values()) {
            if (record.status() == InstanceStatus.ONLINE
                    && record.sinceHeartbeatMillis() > linkConfig.heartbeatTimeoutMillis * 2) {
                record.setStatus(InstanceStatus.DISCONNECTED);
                record.setLastError("heartbeat lost");
                changed();
            }
        }
    }

    private void changed() {
        Runnable listener = changeListener;
        if (listener != null) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // A broken listener must not break the pipeline.
            }
        }
    }

    /** The number of instances currently controllable. */
    public int onlineCount() {
        int n = 0;
        for (InstanceRecord record : instances.values()) {
            if (record.status() == InstanceStatus.ONLINE) {
                n++;
            }
        }
        return n;
    }

    public int size() {
        return instances.size();
    }

    // ------------------------------------------------------------- shutdown

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        for (AgentConnection connection : connections.values()) {
            connection.link().sendBye("controller shutting down");
        }
        connections.clear();
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // teardown
            }
        }
        if (connectionPool != null) {
            connectionPool.shutdownNow();
        }
        if (acceptThread != null) {
            acceptThread.interrupt();
        }
        if (maintenanceTask != null) {
            maintenanceTask.cancel(false);
        }
        audit.record(AuditLog.Kind.CONFIG_CHANGED, "", "controller stopped");
        changed();
    }
}
