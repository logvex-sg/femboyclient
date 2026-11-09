package com.logvex.crystal.remote.agent;

/**
 * How an agent connects: the controller address, the instance identity, and its secret.
 *
 * <p>Every field is optional until the user configures remote control, because the feature is off
 * by default and the client must keep working when it is unconfigured. The secret is set either
 * by a pairing code the user typed ({@link #pairingCode}) or by a stored secret from a previous
 * run; it is never logged.
 *
 * <p>Minecraft-independent.
 */
public final class AgentConfig {

    /** Whether the agent should try to connect at all. Off by default. */
    public boolean enabled = false;

    /** The controller host to dial (outbound, so NAT is not a problem). */
    public String controllerHost = "127.0.0.1";

    /** The controller port. */
    public int controllerPort = 45871;

    /** This instance's stable id; generated once and persisted. */
    public String instanceId = "";

    /** The user-facing name shown on the dashboard. */
    public String instanceName = "";

    /** The pairing code the user typed, or the stored secret after a successful pairing. */
    public String pairingCode = "";

    /** The LOGVEX CRYSTAL version, filled in by the client bridge. */
    public String clientVersion = "";

    /** The Minecraft version, filled in by the client bridge. */
    public String minecraftVersion = "";

    /** Whether to use TLS (recommended off-LAN). */
    public boolean useTls = false;

    /** Optional PKCS#12 truststore path for verifying the controller's certificate. */
    public String truststorePath = "";

    /** Optional truststore password (never logged). */
    public String truststorePassword = "";

    /** Whether to share the connected server address with the controller. */
    public boolean shareServerAddress = false;

    /** Whether to share player coordinates with the controller. */
    public boolean shareCoordinates = false;

    /** A copy, for snapshotting into a settings store. */
    public AgentConfig copy() {
        AgentConfig c = new AgentConfig();
        c.enabled = enabled;
        c.controllerHost = controllerHost;
        c.controllerPort = controllerPort;
        c.instanceId = instanceId;
        c.instanceName = instanceName;
        c.pairingCode = pairingCode;
        c.useTls = useTls;
        c.truststorePath = truststorePath;
        c.truststorePassword = truststorePassword;
        c.shareServerAddress = shareServerAddress;
        c.shareCoordinates = shareCoordinates;
        return c;
    }

    /** Clamps a hand-edited config into a usable range rather than rejecting it. */
    public void clamp() {
        if (controllerHost == null || controllerHost.isBlank()) {
            controllerHost = "127.0.0.1";
        }
        if (controllerPort < 1 || controllerPort > 65535) {
            controllerPort = 45871;
        }
        if (instanceName == null) {
            instanceName = "";
        }
        if (instanceId == null) {
            instanceId = "";
        }
        if (pairingCode == null) {
            pairingCode = "";
        }
    }

    /** True when there is enough configuration to attempt a connection. */
    public boolean configured() {
        return enabled && !controllerHost.isBlank() && controllerPort > 0;
    }
}
