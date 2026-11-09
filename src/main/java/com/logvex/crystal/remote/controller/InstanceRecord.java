package com.logvex.crystal.remote.controller;

import com.logvex.crystal.remote.protocol.InstanceSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The controller's view of one managed instance: identity, connection state, the last snapshot,
 * the groups it belongs to, and liveness bookkeeping.
 *
 * <p>It deliberately holds no secret — the shared secret lives in the controller's
 * {@link com.logvex.crystal.remote.security.PairingStore} — so a record can be handed to the
 * dashboard and to tests freely. Mutable because the controller updates it as messages arrive,
 * but every mutation is a small, intention-revealing method; the dashboard reads a consistent
 * picture by copying what it needs while holding the registry lock.
 *
 * <p>Minecraft-independent and unit testable.
 */
public final class InstanceRecord {

    private final String instanceId;
    private String name;
    private volatile InstanceStatus status = InstanceStatus.OFFLINE;
    private volatile InstanceSnapshot snapshot;
    private volatile String lastError = "";
    private volatile long lastHeartbeatMillis;
    private volatile long lastStatusMillis;
    private volatile int protocolVersion;
    private volatile String clientVersion = "";
    private volatile String minecraftVersion = "";
    private volatile boolean paired;
    private final Set<String> groups = new LinkedHashSet<>();
    /** Transient handle to the live connection; null when offline. */
    private transient AgentConnection connection;

    public InstanceRecord(String instanceId, String name) {
        this.instanceId = instanceId == null ? "" : instanceId;
        this.name = name == null || name.isBlank() ? this.instanceId : name;
    }

    public String instanceId() {
        return instanceId;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
    }

    public InstanceStatus status() {
        return status;
    }

    public void setStatus(InstanceStatus status) {
        this.status = status == null ? InstanceStatus.OFFLINE : status;
    }

    public InstanceSnapshot snapshot() {
        return snapshot;
    }

    public void setSnapshot(InstanceSnapshot snapshot) {
        this.snapshot = snapshot;
        if (snapshot != null) {
            this.clientVersion = snapshot.clientVersion();
            this.minecraftVersion = snapshot.minecraftVersion();
            this.protocolVersion = snapshot.protocolVersion();
            this.lastStatusMillis = System.currentTimeMillis();
        }
    }

    public String lastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError == null ? "" : lastError;
    }

    public long lastHeartbeatMillis() {
        return lastHeartbeatMillis;
    }

    public void markHeartbeat(long nowMillis) {
        this.lastHeartbeatMillis = nowMillis;
    }

    public long lastStatusMillis() {
        return lastStatusMillis;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String clientVersion() {
        return clientVersion;
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    public boolean paired() {
        return paired;
    }

    public void setPaired(boolean paired) {
        this.paired = paired;
    }

    public AgentConnection connection() {
        return connection;
    }

    public void setConnection(AgentConnection connection) {
        this.connection = connection;
    }

    public Set<String> groups() {
        return groups;
    }

    public void addGroup(String group) {
        if (group != null && !group.isBlank()) {
            groups.add(group.trim());
        }
    }

    public void removeGroup(String group) {
        groups.remove(group);
    }

    public boolean inGroup(String group) {
        return group != null && groups.contains(group);
    }

    /** True when the instance is online and its link is still open. */
    public boolean controllable() {
        AgentConnection c = connection;
        return status == InstanceStatus.ONLINE && c != null && c.link().open();
    }

    /** Milliseconds since the last heartbeat, or Long.MAX_VALUE when never heard from. */
    public long sinceHeartbeatMillis() {
        return lastHeartbeatMillis == 0 ? Long.MAX_VALUE
                : System.currentTimeMillis() - lastHeartbeatMillis;
    }

    /** The enabled module names from the last snapshot, or an empty list. */
    public List<String> enabledModules() {
        InstanceSnapshot s = snapshot;
        return s == null ? List.of() : s.enabledModules();
    }

    /** A one-line summary for the list view. */
    public String describe() {
        StringBuilder sb = new StringBuilder(name);
        sb.append(" (").append(status.label()).append(')');
        if (!minecraftVersion.isBlank()) {
            sb.append(" MC ").append(minecraftVersion);
        }
        if (snapshot != null && snapshot.fps() >= 0) {
            sb.append(' ').append(snapshot.fps()).append("fps");
        }
        return sb.toString();
    }

    /** A mutable copy of the group set, so a caller can iterate without holding a live view. */
    public List<String> groupsCopy() {
        return new ArrayList<>(groups);
    }
}
