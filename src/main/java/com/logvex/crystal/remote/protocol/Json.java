package com.logvex.crystal.remote.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Small, defensive accessors over Gson's {@link JsonObject}.
 *
 * <p>The protocol decodes untrusted input, so every read must tolerate a missing, null or
 * wrong-typed member by returning a default rather than throwing. Centralising that here keeps
 * the payload codecs short and makes the "invalid payload" behaviour uniform: a malformed
 * message yields defaults and is then rejected by validation, instead of throwing a
 * {@code NullPointerException} somewhere in a decoder.
 *
 * <p>Minecraft-independent and unit testable.
 */
public final class Json {

    private Json() {
    }

    public static String str(JsonObject o, String key, String fallback) {
        if (o == null) {
            return fallback;
        }
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return e.getAsString();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    public static int i32(JsonObject o, String key, int fallback) {
        if (o == null) {
            return fallback;
        }
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return e.getAsInt();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    public static long i64(JsonObject o, String key, long fallback) {
        if (o == null) {
            return fallback;
        }
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return e.getAsLong();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    public static double f64(JsonObject o, String key, double fallback) {
        if (o == null) {
            return fallback;
        }
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return e.getAsDouble();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    public static boolean bool(JsonObject o, String key, boolean fallback) {
        if (o == null) {
            return fallback;
        }
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return e.getAsBoolean();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    /** The nested object at {@code key}, or an empty object when absent or mistyped. */
    public static JsonObject obj(JsonObject o, String key) {
        if (o == null) {
            return new JsonObject();
        }
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    /** The array at {@code key}, or an empty array when absent or mistyped. */
    public static JsonArray arr(JsonObject o, String key) {
        if (o == null) {
            return new JsonArray();
        }
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }
}
