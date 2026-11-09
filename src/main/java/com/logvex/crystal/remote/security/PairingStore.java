package com.logvex.crystal.remote.security;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.logvex.crystal.config.ConfigIo;
import com.logvex.crystal.remote.protocol.Json;
import com.logvex.crystal.remote.protocol.Payloads;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persists the controller's pairings — instance ids, shared secrets, permissions, allowlists and
 * revocation state — to a single owner-only file.
 *
 * <p>This file <em>is</em> the controller's credential store, so two rules apply. First, it is
 * written atomically through {@link ConfigIo} so a crash cannot corrupt it and lock the user out
 * of their own instances. Second, on a POSIX filesystem it is chmod'ed to {@code rw-------} so no
 * other account on the machine can read the secrets. On a filesystem without POSIX permissions
 * the chmod is best-effort and the file is still written.
 *
 * <p>Secrets are never logged; only instance ids and names are. Minecraft-independent and unit
 * testable (a temp directory stands in for the config directory).
 */
public final class PairingStore {

    public static final int SCHEMA_VERSION = 1;

    private final Path file;

    public PairingStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** Reads the pairings, keyed by instance id. A missing or malformed file yields an empty map. */
    public Map<String, PairingRecord> load() {
        Map<String, PairingRecord> out = new LinkedHashMap<>();
        JsonObject root = ConfigIo.readObject(file);
        if (root == null) {
            return out;
        }
        if (Json.i32(root, "schema", SCHEMA_VERSION) > SCHEMA_VERSION) {
            return out;
        }
        JsonArray array = Json.arr(root, "pairings");
        for (int i = 0; i < array.size(); i++) {
            if (!array.get(i).isJsonObject()) {
                continue;
            }
            JsonObject entry = array.get(i).getAsJsonObject();
            String id = Json.str(entry, "instanceId", "");
            String secret = Json.str(entry, "secret", "");
            if (id.isBlank() || secret.isBlank()) {
                continue;
            }
            Set<Permission> permissions = EnumSet.noneOf(Permission.class);
            for (String wire : Payloads.stringList(entry, "permissions")) {
                Permission p = Permission.parse(wire);
                if (p != null) {
                    permissions.add(p);
                }
            }
            CommandAllowlist allowlist = CommandAllowlist.fromWire(Payloads.stringList(entry, "allowlist"));
            PairingRecord record = new PairingRecord(id, Json.str(entry, "name", id), secret,
                    Json.i64(entry, "pairedAt", System.currentTimeMillis()), permissions, allowlist);
            record.markSeen(Json.i64(entry, "lastSeen", record.pairedAtMillis()));
            if (Json.bool(entry, "revoked", false)) {
                record.revoke();
            }
            out.put(id, record);
        }
        return out;
    }

    /** Writes the pairings atomically and, where possible, restricts the file to the owner. */
    public void save(Map<String, PairingRecord> records) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonArray array = new JsonArray();
        if (records != null) {
            for (PairingRecord record : records.values()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("instanceId", record.instanceId());
                entry.addProperty("name", record.name());
                entry.addProperty("secret", record.secret());
                entry.addProperty("pairedAt", record.pairedAtMillis());
                entry.addProperty("lastSeen", record.lastSeenMillis());
                entry.addProperty("revoked", record.revoked());
                JsonArray perms = new JsonArray();
                for (String wire : record.permissionNames()) {
                    perms.add(wire);
                }
                entry.add("permissions", perms);
                JsonArray allow = new JsonArray();
                for (String wire : record.allowlist().wireNames()) {
                    allow.add(wire);
                }
                entry.add("allowlist", allow);
                array.add(entry);
            }
        }
        root.add("pairings", array);
        ConfigIo.writeObject(file, root);
        restrictToOwner(file);
    }

    /** Best-effort owner-only permissions; silently skipped on a filesystem without POSIX perms. */
    static void restrictToOwner(Path file) {
        try {
            if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(file, EnumSet.of(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            }
        } catch (Exception ignored) {
            // Not fatal: the file is still written; the OS may simply not support the mode.
        }
    }

    /** A convenience for tests: an in-memory list of the ids currently stored. */
    public List<String> ids() {
        List<String> ids = new ArrayList<>();
        ids.addAll(load().keySet());
        return ids;
    }
}
