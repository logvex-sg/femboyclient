package com.logvex.crystal.remote.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The concrete payload codecs for the protocol: turns the value types into JSON objects and
 * back, tolerating missing or mistyped fields by falling back to safe defaults.
 *
 * <p>Every decoder here is total (never throws on malformed input); the resulting value is then
 * validated by the layer that uses it, so a bad payload becomes a clear rejection rather than a
 * crash. This keeps all wire-shape knowledge in one file instead of scattered across the agent
 * and the controller.
 *
 * <p>Minecraft-independent and unit testable.
 */
public final class Payloads {

    private Payloads() {
    }

    // ------------------------------------------------------------- handshake

    /** The {@link MessageType#HELLO} payload an agent sends to begin registration. */
    public static JsonObject hello(String instanceId, String name, String clientVersion,
                                   String minecraftVersion) {
        JsonObject root = new JsonObject();
        root.addProperty("instanceId", instanceId);
        root.addProperty("name", name);
        root.addProperty("clientVersion", clientVersion);
        root.addProperty("minecraftVersion", minecraftVersion);
        return root;
    }

    /** The {@link MessageType#HELLO_ACK} payload: the negotiated version plus an auth challenge. */
    public static JsonObject helloAck(int negotiatedVersion, String challenge, String controllerName) {
        JsonObject root = new JsonObject();
        root.addProperty("negotiated", negotiatedVersion);
        root.addProperty("challenge", challenge);
        root.addProperty("controllerName", controllerName);
        return root;
    }

    /** The {@link MessageType#AUTH} payload: the proof that the agent holds its shared secret. */
    public static JsonObject auth(String instanceId, String proof) {
        JsonObject root = new JsonObject();
        root.addProperty("instanceId", instanceId);
        root.addProperty("proof", proof);
        return root;
    }

    /** The {@link MessageType#AUTH_OK} payload: the permissions and capabilities granted. */
    public static JsonObject authOk(List<String> permissions, List<String> capabilities,
                                    String sessionId) {
        JsonObject root = new JsonObject();
        root.add("permissions", strings(permissions));
        root.add("capabilities", strings(capabilities));
        root.addProperty("sessionId", sessionId);
        return root;
    }

    // ------------------------------------------------------------- status

    /** Encodes an {@link InstanceSnapshot} as the {@link MessageType#STATUS} payload. */
    public static JsonObject status(InstanceSnapshot snapshot) {
        JsonObject root = new JsonObject();
        root.addProperty("instanceId", snapshot.instanceId());
        root.addProperty("name", snapshot.name());
        root.addProperty("minecraftVersion", snapshot.minecraftVersion());
        root.addProperty("clientVersion", snapshot.clientVersion());
        root.addProperty("protocolVersion", snapshot.protocolVersion());
        root.addProperty("serverAddress", snapshot.serverAddress());
        root.addProperty("dimension", snapshot.dimension());
        if (snapshot.hasCoordinates()) {
            JsonArray coords = new JsonArray();
            for (double c : snapshot.coordinates()) {
                coords.add(c);
            }
            root.add("coordinates", coords);
        }
        root.add("enabledModules", strings(snapshot.enabledModules()));
        root.addProperty("fps", snapshot.fps());
        root.addProperty("activeModules", snapshot.activeModules());
        root.addProperty("activity", snapshot.activity());
        root.add("capabilities", strings(snapshot.capabilities().wireNames()));
        root.addProperty("capturedAt", snapshot.capturedAtMillis());
        return root;
    }

    /** Decodes a {@link InstanceSnapshot} from a STATUS payload. */
    public static InstanceSnapshot snapshot(JsonObject root) {
        List<String> modules = stringList(root, "enabledModules");
        List<String> caps = stringList(root, "capabilities");
        double[] coords = null;
        JsonArray arr = Json.arr(root, "coordinates");
        if (arr.size() == 3) {
            coords = new double[] {arr.get(0).getAsDouble(), arr.get(1).getAsDouble(),
                    arr.get(2).getAsDouble()};
        }
        return new InstanceSnapshot(
                Json.str(root, "instanceId", ""),
                Json.str(root, "name", ""),
                Json.str(root, "minecraftVersion", ""),
                Json.str(root, "clientVersion", ""),
                Json.i32(root, "protocolVersion", ProtocolVersion.CURRENT),
                Json.str(root, "serverAddress", ""),
                Json.str(root, "dimension", ""),
                coords,
                modules,
                Json.i32(root, "fps", -1),
                Json.i32(root, "activeModules", modules.size()),
                Json.str(root, "activity", ""),
                Capability.fromWire(caps),
                Json.i64(root, "capturedAt", System.currentTimeMillis()));
    }

    // ------------------------------------------------------------- commands

    /** Decodes a {@link CommandRequest} from a COMMAND payload. */
    public static CommandRequest command(JsonObject root) {
        RemoteAction action = RemoteAction.parse(Json.str(root, "action", ""));
        JsonObject params = Json.obj(root, "params");
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, com.google.gson.JsonElement> e : params.entrySet()) {
            if (e.getValue() != null && e.getValue().isJsonPrimitive()) {
                map.put(e.getKey(), e.getValue().getAsString());
            }
        }
        return new CommandRequest("", action, map, Json.str(root, "idempotencyKey", ""));
    }

    /** Decodes a {@link CommandResult} from a COMMAND_RESULT payload. */
    public static CommandResult result(String requestId, String instanceId, JsonObject root) {
        return new CommandResult(
                requestId,
                instanceId,
                CommandStatus.parse(Json.str(root, "status", "")),
                Json.str(root, "message", ""),
                Json.i64(root, "completedAt", System.currentTimeMillis()));
    }

    // ------------------------------------------------------------- helpers

    public static JsonArray strings(List<String> values) {
        JsonArray arr = new JsonArray();
        if (values != null) {
            for (String value : values) {
                arr.add(value);
            }
        }
        return arr;
    }

    public static List<String> stringList(JsonObject root, String key) {
        List<String> out = new ArrayList<>();
        JsonArray arr = Json.arr(root, key);
        for (int i = 0; i < arr.size(); i++) {
            if (arr.get(i).isJsonPrimitive()) {
                out.add(arr.get(i).getAsString());
            }
        }
        return out;
    }
}
