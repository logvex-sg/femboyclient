package com.logvex.crystal.remote.protocol;

import com.google.gson.JsonObject;

/**
 * The agent's answer to one {@link CommandRequest}: a status, a message, and the id of the
 * command it answers.
 *
 * <p>An agent must send exactly one result per command it accepts, and the result must be sent
 * only after the work has actually been attempted — a controller treats "no result" as
 * {@link CommandStatus#TIMEOUT}, never as success. The {@code instanceId} is echoed so a
 * controller multiplexing many agents can attribute a stray result correctly.
 */
public final class CommandResult {

    private final String requestId;
    private final String instanceId;
    private final CommandStatus status;
    private final String message;
    private final long completedAtMillis;

    public CommandResult(String requestId, String instanceId, CommandStatus status, String message,
                         long completedAtMillis) {
        this.requestId = requestId == null ? "" : requestId;
        this.instanceId = instanceId == null ? "" : instanceId;
        this.status = status == null ? CommandStatus.FAILED : status;
        this.message = message == null ? "" : message;
        this.completedAtMillis = completedAtMillis;
    }

    public static CommandResult ok(String requestId, String instanceId, String message) {
        return new CommandResult(requestId, instanceId, CommandStatus.OK, message,
                System.currentTimeMillis());
    }

    public static CommandResult unsupported(String requestId, String instanceId, String message) {
        return new CommandResult(requestId, instanceId, CommandStatus.UNSUPPORTED, message,
                System.currentTimeMillis());
    }

    public static CommandResult failed(String requestId, String instanceId, String message) {
        return new CommandResult(requestId, instanceId, CommandStatus.FAILED, message,
                System.currentTimeMillis());
    }

    public static CommandResult rejected(String requestId, String instanceId, String message) {
        return new CommandResult(requestId, instanceId, CommandStatus.REJECTED, message,
                System.currentTimeMillis());
    }

    public String requestId() {
        return requestId;
    }

    public String instanceId() {
        return instanceId;
    }

    public CommandStatus status() {
        return status;
    }

    public String message() {
        return message;
    }

    public long completedAtMillis() {
        return completedAtMillis;
    }

    public JsonObject toPayload() {
        JsonObject root = new JsonObject();
        root.addProperty("status", status.wire());
        root.addProperty("message", message);
        root.addProperty("completedAt", completedAtMillis);
        return root;
    }
}
