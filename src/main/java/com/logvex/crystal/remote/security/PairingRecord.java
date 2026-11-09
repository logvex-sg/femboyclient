package com.logvex.crystal.remote.security;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A controller's record of one paired instance: the shared secret, the permissions and
 * allowlist granted to it, and whether it has been revoked.
 *
 * <p>The secret is the whole of the instance's authentication material, so it lives only in the
 * controller's credential file (written owner-only, see {@link PairingStore}) and never appears
 * in a log, the dashboard, or the repository. Revocation is a flag rather than a deletion, so a
 * revoked instance is remembered as revoked (and its id cannot be silently re-registered)
 * instead of looking like an instance that was never paired.
 *
 * <p>Minecraft-independent and unit testable.
 */
public final class PairingRecord {

    private final String instanceId;
    private String name;
    private String secret;
    private final long pairedAtMillis;
    private long lastSeenMillis;
    private boolean revoked;
    private Set<Permission> permissions;
    private CommandAllowlist allowlist;

    public PairingRecord(String instanceId, String name, String secret, long pairedAtMillis,
                         Set<Permission> permissions, CommandAllowlist allowlist) {
        this.instanceId = instanceId == null ? "" : instanceId;
        this.name = name == null || name.isBlank() ? this.instanceId : name;
        this.secret = secret == null ? "" : secret;
        this.pairedAtMillis = pairedAtMillis;
        this.lastSeenMillis = pairedAtMillis;
        this.permissions = permissions == null ? EnumSet.noneOf(Permission.class)
                : EnumSet.copyOf(permissions);
        this.allowlist = allowlist == null ? CommandAllowlist.safeDefaults() : allowlist;
    }

    /** Creates a fresh pairing: a new secret, safe-default permissions and allowlist. */
    public static PairingRecord pair(String instanceId, String name, Set<Permission> permissions) {
        return new PairingRecord(instanceId, name, RemoteCrypto.newSecret(),
                System.currentTimeMillis(), permissions, CommandAllowlist.safeDefaults());
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

    /** The shared secret. Callers must not log this. */
    public String secret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret == null ? "" : secret;
    }

    public long pairedAtMillis() {
        return pairedAtMillis;
    }

    public long lastSeenMillis() {
        return lastSeenMillis;
    }

    public void markSeen(long nowMillis) {
        this.lastSeenMillis = nowMillis;
    }

    public boolean revoked() {
        return revoked;
    }

    public void revoke() {
        this.revoked = true;
        // Rotate the secret too, so even a copy of the file cannot re-authenticate.
        this.secret = RemoteCrypto.newSecret();
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public void setPermissions(Set<Permission> permissions) {
        this.permissions = permissions == null ? EnumSet.noneOf(Permission.class)
                : EnumSet.copyOf(permissions);
    }

    public CommandAllowlist allowlist() {
        return allowlist;
    }

    public void setAllowlist(CommandAllowlist allowlist) {
        this.allowlist = allowlist == null ? CommandAllowlist.none() : allowlist;
    }

    /** True when this record may still authenticate and is not revoked. */
    public boolean active() {
        return !revoked && !secret.isBlank();
    }

    /** The pairing code the user types on the agent to complete this pairing. */
    public String pairingCode() {
        return RemoteCrypto.pairingCode(secret);
    }

    /** Wire names of the granted permissions. */
    public List<String> permissionNames() {
        return permissions.stream().map(Permission::wire).toList();
    }
}
