package com.logvex.crystal.remote.agent;

import com.logvex.crystal.remote.protocol.CommandResult;
import com.logvex.crystal.remote.protocol.InstanceSnapshot;
import com.logvex.crystal.remote.protocol.RemoteAction;
import java.util.Map;

/**
 * The SPI the agent uses to actually perform an action on the client.
 *
 * <p>The networking layer knows nothing about Minecraft: it validates a command against the
 * allowlist and permissions, then hands it to this handler. The client bridge implements the
 * handler and marshals the call onto the game thread, which is the only place module state may be
 * touched. Keeping the seam here is what lets the whole remote stack be unit tested with a stub
 * handler and no game.
 *
 * <p>Implementations must return a {@link CommandResult} rather than throwing; a thrown exception
 * is caught by the agent and turned into a failed result, but a handler that reports its own
 * precise failure is more useful to the dashboard.
 */
public interface RemoteActionHandler {

    /**
     * Runs one already-validated action.
     *
     * @param requestId  the command's id, to echo in the result
     * @param instanceId this agent's id
     * @param action     the action to run
     * @param parameters the validated parameters
     * @return the result to send back
     */
    CommandResult execute(String requestId, String instanceId, RemoteAction action,
                          Map<String, String> parameters);

    /**
     * Builds the current instance snapshot for the STATUS message. Called on the agent's
     * heartbeat schedule, so it must be cheap; the bridge snapshots cached values rather than
     * walking the live world.
     */
    InstanceSnapshot snapshot(String instanceId);

    /** The capabilities this instance advertises (what it can do). */
    default com.logvex.crystal.remote.protocol.Capability capabilities() {
        return com.logvex.crystal.remote.protocol.Capability.none();
    }

    /** True when the host can restart the agent process on request. */
    default boolean canRestart() {
        return false;
    }

    /**
     * Asks the host to restart the agent. The default does nothing and reports unsupported; a
     * bridge that can restart overrides this. It must not be called on the game thread.
     */
    default boolean restart() {
        return false;
    }
}
