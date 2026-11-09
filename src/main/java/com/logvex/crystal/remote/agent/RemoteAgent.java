package com.logvex.crystal.remote.agent;

import com.logvex.crystal.remote.core.LinkConfig;
import com.logvex.crystal.remote.core.LinkListener;
import com.logvex.crystal.remote.core.ReconnectPolicy;
import com.logvex.crystal.remote.core.RemoteLink;
import com.logvex.crystal.remote.core.RemoteThreads;
import com.logvex.crystal.remote.core.RemoteTransport;
import com.logvex.crystal.remote.core.SocketTransport;
import com.logvex.crystal.remote.protocol.CommandRequest;
import com.logvex.crystal.remote.protocol.CommandResult;
import com.logvex.crystal.remote.protocol.Json;
import com.logvex.crystal.remote.protocol.MessageType;
import com.logvex.crystal.remote.protocol.Payloads;
import com.logvex.crystal.remote.protocol.ProtocolVersion;
import com.logvex.crystal.remote.protocol.RemoteMessage;
import com.logvex.crystal.remote.security.AuditLog;
import com.logvex.crystal.remote.security.CommandAllowlist;
import com.logvex.crystal.remote.security.CommandValidator;
import com.logvex.crystal.remote.security.Permission;
import com.logvex.crystal.remote.security.RemoteCrypto;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The lightweight service that runs alongside one LOGVEX CRYSTAL instance and lets a controller
 * manage it.
 *
 * <p>It dials <em>out</em> to the controller (so instances behind NAT need no port forwarding),
 * registers with its instance id, proves it holds the shared secret, then answers commands. All
 * I/O is on daemon threads; the game thread is only ever touched through the
 * {@link RemoteActionHandler} the bridge supplies.
 *
 * <p>Reliability: on any disconnect it retries with {@link ReconnectPolicy} exponential backoff
 * until told to stop, so a controller restart or a network blip heals itself. A command is
 * validated against the allowlist and permissions <em>here</em>, on the agent, before the handler
 * runs — the controller's checks are a convenience, never the security boundary. An action the
 * instance cannot perform is answered {@code unsupported}, never {@code ok}.
 *
 * <p>Minecraft-independent and unit testable against a loopback controller.
 */
public final class RemoteAgent implements AutoCloseable {

    private final AgentConfig config;
    private final AuditLog audit;
    private final LinkConfig linkConfig;
    private final ReconnectPolicy reconnect;
    private final RemoteActionHandler handler;
    private final CommandAllowlist allowlist;
    private final Set<Permission> permissions;
    private final CommandValidator validator;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private volatile RemoteLink link;
    private volatile boolean authenticated;
    private volatile String lastError = "";
    private volatile long lastConnectedMillis;
    private volatile int reconnectAttempts;
    private volatile String grantedSecret;
    private final Map<String, String> idempotencyCache = new ConcurrentHashMap<>();

    public RemoteAgent(AgentConfig config, AuditLog audit, LinkConfig linkConfig,
                       RemoteActionHandler handler, CommandAllowlist allowlist,
                       Set<Permission> permissions) {
        this.config = config;
        this.audit = audit == null ? AuditLog.standard() : audit;
        this.linkConfig = linkConfig == null ? LinkConfig.defaults() : linkConfig;
        this.reconnect = ReconnectPolicy.standard();
        this.handler = handler;
        this.allowlist = allowlist == null ? CommandAllowlist.safeDefaults() : allowlist;
        this.permissions = permissions == null ? Set.of() : permissions;
        this.validator = new CommandValidator(this.allowlist, this.permissions);
    }

    /** An agent with the standard policy and safe-default allowlist. */
    public static RemoteAgent standard(AgentConfig config, RemoteActionHandler handler) {
        return new RemoteAgent(config, AuditLog.standard(), LinkConfig.defaults(), handler,
                CommandAllowlist.safeDefaults(), Set.of(Permission.VIEW_STATUS,
                Permission.CONTROL_MODULES, Permission.READ_DIAGNOSTICS, Permission.APPLY_PROFILES,
                Permission.EDIT_KEYBINDS, Permission.DISCONNECT));
    }

    public AgentConfig config() {
        return config;
    }

    public boolean authenticated() {
        return authenticated;
    }

    public boolean running() {
        return running.get();
    }

    public String lastError() {
        return lastError;
    }

    public long lastConnectedMillis() {
        return lastConnectedMillis;
    }

    public int reconnectAttempts() {
        return reconnectAttempts;
    }

    /** The secret currently in use (from the pairing code or a previous pairing). */
    public String secret() {
        return grantedSecret == null ? "" : grantedSecret;
    }

    /** True when the agent has a live, authenticated link. */
    public boolean connected() {
        RemoteLink current = link;
        return current != null && current.open() && authenticated;
    }

    // ------------------------------------------------------------- lifecycle

    /** Starts connecting; the agent keeps itself alive until {@link #close()}. Idempotent. */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        config.clamp();
        grantedSecret = resolveSecret();
        connectAsync(0);
    }

    /** The secret to authenticate with: a stored secret, else decoded from the pairing code. */
    private String resolveSecret() {
        String code = config.pairingCode == null ? "" : config.pairingCode.trim();
        if (code.isEmpty()) {
            return "";
        }
        String fromCode = RemoteCrypto.secretFromPairingCode(code);
        return fromCode.isEmpty() ? code : fromCode;
    }

    private void connectAsync(long delayMillis) {
        if (!running.get()) {
            return;
        }
        // The delayed retry is cheap and may sit on the shared scheduler, but the attempt itself
        // must not: socket.connect() blocks for up to its timeout, and the shared scheduler also
        // drives every link's heartbeat. Blocking it would starve heartbeats and disconnect live
        // instances. So the attempt always runs on its own short-lived daemon thread.
        Runnable attempt = () -> {
            if (!running.get() || !connecting.compareAndSet(false, true)) {
                return;
            }
            try {
                attemptConnection();
            } finally {
                connecting.set(false);
            }
        };
        if (delayMillis <= 0) {
            RemoteThreads.namedFactory("connect").newThread(attempt).start();
        } else {
            RemoteThreads.scheduler().schedule(
                    () -> RemoteThreads.namedFactory("connect").newThread(attempt).start(),
                    delayMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void attemptConnection() {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(config.controllerHost, config.controllerPort), 5_000);
            socket.setTcpNoDelay(true);
            // No SO_TIMEOUT; see the controller: liveness is enforced by heartbeats that close
            // the socket, which safely unblocks the reader without risking a mid-frame timeout.
            RemoteTransport transport = new SocketTransport(socket, false,
                    "tcp://" + config.controllerHost + ":" + config.controllerPort);
            RemoteLink newLink = new RemoteLink(transport, linkConfig, new Listener());
            this.link = newLink;
            newLink.start();
            sendHello(newLink);
            lastError = "";
        } catch (IOException e) {
            lastError = "connect failed: " + e.getMessage();
            closeQuietly(socket);
            scheduleReconnect();
        }
    }

    private void sendHello(RemoteLink target) {
        target.setProtocolVersion(ProtocolVersion.CURRENT);
        target.send(new RemoteMessage(ProtocolVersion.CURRENT, MessageType.HELLO, "",
                System.currentTimeMillis(), com.logvex.crystal.remote.protocol.Nonces.fresh(),
                Payloads.hello(config.instanceId, name(), config.clientVersion,
                        currentMinecraftVersion())));
    }

    private String name() {
        return config.instanceName == null || config.instanceName.isBlank()
                ? config.instanceId : config.instanceName;
    }

    /** The Minecraft version this instance runs, from the config (filled by the client bridge). */
    protected String currentMinecraftVersion() {
        return config.minecraftVersion == null ? "" : config.minecraftVersion;
    }

    private void scheduleReconnect() {
        if (!running.get()) {
            return;
        }
        reconnectAttempts++;
        long delay = reconnect.nextDelayMillis();
        audit.record(AuditLog.Kind.AGENT_DISCONNECTED, config.instanceId,
                "reconnecting in " + delay + "ms (attempt " + reconnectAttempts + ")", 1);
        connectAsync(delay);
    }

    // ------------------------------------------------------------- inbound

    private final class Listener implements LinkListener {
        @Override
        public void onMessage(RemoteMessage message) {
            switch (message.type()) {
                case HELLO_ACK -> handleHelloAck(message);
                case AUTH_OK -> handleAuthOk(message);
                case AUTH_FAIL -> {
                    authenticated = false;
                    lastError = Json.str(message.payload(), "reason", "authentication failed");
                    audit.record(AuditLog.Kind.AUTH_FAILURE, config.instanceId, lastError, 2);
                }
                case COMMAND -> handleCommand(message);
                default -> {
                    // PONG and unknown types need no action here.
                }
            }
        }

        @Override
        public void onClosed(String reason, boolean fatal) {
            authenticated = false;
            lastError = reason;
            if (running.get()) {
                scheduleReconnect();
            }
        }

        @Override
        public void onWarning(String message) {
            audit.record(AuditLog.Kind.REPLAY_REJECTED, config.instanceId, message, 1);
        }
    }

    private void handleHelloAck(RemoteMessage message) {
        int negotiated = Json.i32(message.payload(), "negotiated", ProtocolVersion.CURRENT);
        String challenge = Json.str(message.payload(), "challenge", "");
        RemoteLink current = link;
        if (current == null) {
            return;
        }
        current.setProtocolVersion(negotiated);
        if (challenge.isEmpty() || grantedSecret == null || grantedSecret.isEmpty()) {
            // The controller does not have a pairing for us (or we have no secret): wait for the
            // user to pair. The link stays open so the controller can show us as awaiting pairing.
            audit.record(AuditLog.Kind.PAIRING_REJECTED, config.instanceId,
                    "controller has no pairing for this instance", 1);
            return;
        }
        String proof = RemoteCrypto.sign(grantedSecret, "auth:" + challenge);
        current.setSecret(grantedSecret);
        current.requireInboundSignature(true);
        // The controller requires every message after HELLO to be signed, and the agent already
        // holds the secret, so AUTH itself must carry a valid tag.
        current.signOutbound(true);
        current.send(new RemoteMessage(negotiated, MessageType.AUTH, message.requestId(),
                System.currentTimeMillis(), com.logvex.crystal.remote.protocol.Nonces.fresh(),
                Payloads.auth(config.instanceId, proof)));
    }

    private void handleAuthOk(RemoteMessage message) {
        authenticated = true;
        reconnectAttempts = 0;
        reconnect.reset();
        lastConnectedMillis = System.currentTimeMillis();
        lastError = "";
        audit.record(AuditLog.Kind.AUTH_SUCCESS, config.instanceId, "authenticated with controller");
        RemoteLink current = link;
        if (current != null) {
            current.signOutbound(true);
            sendStatus(current);
        }
    }

    private void handleCommand(RemoteMessage message) {
        String requestId = message.requestId();
        CommandRequest request = Payloads.command(message.payload());
        RemoteLink current = link;
        if (current == null) {
            return;
        }
        if (request.action() == null) {
            current.send(new RemoteMessage(current.protocolVersion(), MessageType.COMMAND_RESULT,
                    requestId, System.currentTimeMillis(),
                    com.logvex.crystal.remote.protocol.Nonces.fresh(),
                    CommandResult.rejected(requestId, config.instanceId, "unknown action").toPayload()));
            return;
        }
        // Idempotency: if we already answered this request id, resend the cached result instead
        // of running the action again.
        String cached = idempotencyCache.get(requestId);
        if (cached != null) {
            current.send(new RemoteMessage(current.protocolVersion(), MessageType.COMMAND_RESULT,
                    requestId, System.currentTimeMillis(),
                    com.logvex.crystal.remote.protocol.Nonces.fresh(), replayPayload(cached)));
            return;
        }
        CommandValidator.Verdict verdict = validator.validate(request.action(), request.parameters());
        if (!verdict.allowed()) {
            audit.record(AuditLog.Kind.ALLOWLIST_DENIED, config.instanceId,
                    request.action().wire() + ": " + verdict.reason(), 1);
            CommandResult result = CommandResult.rejected(requestId, config.instanceId, verdict.reason());
            respond(current, requestId, result);
            return;
        }
        if (request.action() == com.logvex.crystal.remote.protocol.RemoteAction.RESTART_AGENT
                && !handler.canRestart()) {
            respond(current, requestId, CommandResult.unsupported(requestId, config.instanceId,
                    "restart is not supported by this host"));
            return;
        }
        CommandResult result;
        try {
            result = handler.execute(requestId, config.instanceId, request.action(), request.parameters());
        } catch (RuntimeException e) {
            result = CommandResult.failed(requestId, config.instanceId,
                    "handler error: " + e.getMessage());
        }
        if (result == null) {
            result = CommandResult.failed(requestId, config.instanceId, "handler returned no result");
        }
        respond(current, requestId, result);
    }

    private void respond(RemoteLink current, String requestId, CommandResult result) {
        idempotencyCache.put(requestId, result.status().wire() + "\u0000" + result.message());
        if (idempotencyCache.size() > 512) {
            // Bounded so a long session cannot grow without limit; the oldest key is dropped.
            java.util.Iterator<String> it = idempotencyCache.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        current.send(new RemoteMessage(current.protocolVersion(), MessageType.COMMAND_RESULT,
                requestId, System.currentTimeMillis(),
                com.logvex.crystal.remote.protocol.Nonces.fresh(), result.toPayload()));
    }

    private com.google.gson.JsonObject replayPayload(String cached) {
        String[] parts = cached.split("\u0000", 2);
        com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
        payload.addProperty("status", parts.length > 0 ? parts[0] : "failed");
        payload.addProperty("message", parts.length > 1 ? parts[1] : "replayed");
        payload.addProperty("completedAt", System.currentTimeMillis());
        return payload;
    }

    // ------------------------------------------------------------- outbound status

    /** Sends a STATUS snapshot now (called after AUTH_OK and on the heartbeat schedule). */
    public void sendStatus() {
        RemoteLink current = link;
        if (current != null && authenticated) {
            sendStatus(current);
        }
    }

    private void sendStatus(RemoteLink current) {
        try {
            var snapshot = handler.snapshot(config.instanceId);
            current.send(new RemoteMessage(current.protocolVersion(), MessageType.STATUS, "",
                    System.currentTimeMillis(), com.logvex.crystal.remote.protocol.Nonces.fresh(),
                    Payloads.status(snapshot)));
        } catch (RuntimeException e) {
            audit.record(AuditLog.Kind.CONFIG_CHANGED, config.instanceId,
                    "snapshot failed: " + e.getMessage(), 1);
        }
    }

    /** The capabilities this agent advertises, from the handler. */
    public com.logvex.crystal.remote.protocol.Capability capabilities() {
        return handler.capabilities();
    }

    // ------------------------------------------------------------- shutdown

    /** Asks the host to restart, if it can; returns true when the request was accepted. */
    public boolean requestRestart() {
        if (!handler.canRestart()) {
            return false;
        }
        return handler.restart();
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        RemoteLink current = link;
        if (current != null) {
            current.sendBye("agent shutting down");
        }
        authenticated = false;
        audit.record(AuditLog.Kind.AGENT_DISCONNECTED, config.instanceId, "agent stopped");
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // teardown
        }
    }
}
