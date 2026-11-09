package com.logvex.crystal.remote.protocol;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One operation the controller asks an agent to perform: an allowlisted {@link RemoteAction}
 * plus a small, typed parameter map.
 *
 * <p>The parameters are deliberately a flat string map rather than arbitrary JSON: a command is
 * always "action + a few named strings", which keeps validation on the agent side simple and
 * keeps the protocol from becoming a general-purpose remote procedure call. The agent resolves
 * each parameter by name against a per-action schema and rejects an unknown or ill-typed value.
 *
 * <p>A command carries a stable {@code requestId} so its result can be matched to it, and an
 * optional {@code idempotencyKey} so a retry of a safe action is not double-applied.
 */
public final class CommandRequest {

    private final String requestId;
    private final RemoteAction action;
    private final Map<String, String> parameters;
    private final String idempotencyKey;

    public CommandRequest(String requestId, RemoteAction action, Map<String, String> parameters,
                          String idempotencyKey) {
        this.requestId = requestId == null ? Nonces.requestId() : requestId;
        this.action = action;
        this.parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        this.idempotencyKey = idempotencyKey == null ? "" : idempotencyKey;
    }

    public static CommandRequest of(RemoteAction action, Map<String, String> parameters) {
        return new CommandRequest(Nonces.requestId(), action, parameters, "");
    }

    public static CommandRequest of(RemoteAction action, String key, String value) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put(key, value);
        return of(action, params);
    }

    public String requestId() {
        return requestId;
    }

    public RemoteAction action() {
        return action;
    }

    public Map<String, String> parameters() {
        return parameters;
    }

    public String parameter(String key) {
        return parameters.get(key);
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    /** A copy with a specific idempotency key, so a retry can be recognised as the same attempt. */
    public CommandRequest withIdempotencyKey(String key) {
        return new CommandRequest(requestId, action, parameters, key);
    }

    /** The JSON payload for a {@link MessageType#COMMAND} message. */
    public JsonObject toPayload() {
        JsonObject root = new JsonObject();
        root.addProperty("action", action == null ? "" : action.wire());
        JsonObject params = new JsonObject();
        for (Map.Entry<String, String> e : parameters.entrySet()) {
            params.addProperty(e.getKey(), e.getValue());
        }
        root.add("params", params);
        if (!idempotencyKey.isBlank()) {
            root.addProperty("idempotencyKey", idempotencyKey);
        }
        return root;
    }
}
