package com.logvex.crystal.remote.protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the dashboard shows about one instance, as a plain value.
 *
 * <p>The agent builds this on its own side from the live client (name, versions, world,
 * enabled modules, performance) and sends it as the {@link MessageType#STATUS} payload; the
 * controller stores the last one it received and renders it. Keeping it a value type with no
 * Minecraft dependency means the dashboard and the tests can read a snapshot without a game.
 *
 * <p>Privacy fields are already resolved by the agent before the snapshot is built: {@code
 * serverAddress} is blank when the agent's privacy setting hides it, and {@code coordinates} is
 * null when the agent has not opted in. The controller never has to apply privacy policy; the
 * data simply is not there.
 *
 * @param instanceId     the agent's stable unique id
 * @param name           the user-facing instance name (renameable from the dashboard)
 * @param minecraftVersion the Minecraft version the agent is running
 * @param clientVersion  the LOGVEX CRYSTAL version
 * @param protocolVersion the protocol the agent speaks
 * @param serverAddress  the connected server, or blank when hidden by privacy settings
 * @param dimension      the current dimension id, or blank when unknown
 * @param coordinates    {@code [x, y, z]} when the agent opted in, else null
 * @param enabledModules names of the currently enabled modules
 * @param fps            frames per second, or -1 when unknown
 * @param activeModules  how many modules are enabled
 * @param activity       a short human phrase, e.g. "mining", "idle", "in combat"
 * @param capabilities   what this instance can do
 * @param capturedAtMillis when the snapshot was taken (agent clock)
 */
public record InstanceSnapshot(
        String instanceId,
        String name,
        String minecraftVersion,
        String clientVersion,
        int protocolVersion,
        String serverAddress,
        String dimension,
        double[] coordinates,
        List<String> enabledModules,
        int fps,
        int activeModules,
        String activity,
        Capability capabilities,
        long capturedAtMillis) {

    public InstanceSnapshot {
        instanceId = orEmpty(instanceId);
        name = name == null || name.isBlank() ? instanceId : name;
        minecraftVersion = orEmpty(minecraftVersion);
        clientVersion = orEmpty(clientVersion);
        serverAddress = orEmpty(serverAddress);
        dimension = orEmpty(dimension);
        coordinates = coordinates != null && coordinates.length == 3 ? coordinates.clone() : null;
        enabledModules = enabledModules == null ? List.of() : List.copyOf(enabledModules);
        activity = orEmpty(activity);
        capabilities = capabilities == null ? Capability.none() : capabilities;
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    /** True when the agent shares coordinates. */
    public boolean hasCoordinates() {
        return coordinates != null;
    }

    /** A copy with a new display name, used when the controller renames an instance locally. */
    public InstanceSnapshot withName(String newName) {
        return new InstanceSnapshot(instanceId, newName, minecraftVersion, clientVersion,
                protocolVersion, serverAddress, dimension, coordinates, enabledModules, fps,
                activeModules, activity, capabilities, capturedAtMillis);
    }

    /** The enabled module names as a mutable copy, so callers cannot mutate the record's list. */
    public List<String> modulesCopy() {
        return new ArrayList<>(enabledModules);
    }
}
