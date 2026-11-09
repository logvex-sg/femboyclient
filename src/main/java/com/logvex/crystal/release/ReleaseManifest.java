package com.logvex.crystal.release;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A machine-readable description of one released artifact. Every field a consumer needs in order to
 * decide whether an artifact is safe and compatible is present here, and {@link #parse} rejects a
 * document that is missing any of them or carries a value outside the known set.
 *
 * <p>The parser is deliberately strict and side-effect free: it never touches the network or the
 * filesystem, so it can be unit-tested and reused by the updater, the fleet update model and the
 * release tooling without a Minecraft runtime. A manifest that parses is well-formed; it is not yet
 * trusted — {@link SignatureVerifier} establishes authenticity separately.
 */
public final class ReleaseManifest {

    /** The only schema version this build understands. */
    public static final int SCHEMA_VERSION = 1;

    private final Edition edition;
    private final String clientVersion;
    private final String minecraftVersion;
    private final String adapterVersion;
    private final int protocolVersion;
    private final String loaderRequirement;
    private final String javaRequirement;
    private final String artifact;
    private final String sha256;
    private final String signature;
    private final ReleaseChannel releaseChannel;

    public ReleaseManifest(Edition edition, String clientVersion, String minecraftVersion,
                           String adapterVersion, int protocolVersion, String loaderRequirement,
                           String javaRequirement, String artifact, String sha256, String signature,
                           ReleaseChannel releaseChannel) {
        this.edition = Objects.requireNonNull(edition, "edition");
        this.clientVersion = requireText(clientVersion, "clientVersion");
        this.minecraftVersion = requireText(minecraftVersion, "minecraftVersion");
        this.adapterVersion = requireText(adapterVersion, "adapterVersion");
        if (protocolVersion <= 0) {
            throw new IllegalArgumentException("protocolVersion must be positive");
        }
        this.protocolVersion = protocolVersion;
        this.loaderRequirement = requireText(loaderRequirement, "loaderRequirement");
        this.javaRequirement = requireText(javaRequirement, "javaRequirement");
        this.artifact = requireText(artifact, "artifact");
        this.sha256 = requireSha256(sha256);
        this.signature = requireText(signature, "signature");
        this.releaseChannel = Objects.requireNonNull(releaseChannel, "releaseChannel");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("manifest field '" + field + "' is required");
        }
        return value.trim();
    }

    private static String requireSha256(String value) {
        String trimmed = requireText(value, "sha256").toLowerCase(java.util.Locale.ROOT);
        if (trimmed.length() != 64 || !trimmed.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be 64 hex characters");
        }
        return trimmed;
    }

    /**
     * Parses a manifest document.
     *
     * @throws ManifestFormatException when the document is not valid JSON, is not an object, has an
     *                                 unknown or missing schema version, is missing a required field,
     *                                 or carries an unknown edition or channel.
     */
    public static ReleaseManifest parse(String json) {
        JsonObject root;
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                throw new ManifestFormatException("manifest is not a JSON object");
            }
            root = element.getAsJsonObject();
        } catch (ManifestFormatException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ManifestFormatException("manifest is not valid JSON: " + e.getMessage());
        }

        int schema = intField(root, "schemaVersion");
        if (schema != SCHEMA_VERSION) {
            throw new ManifestFormatException("unsupported schemaVersion " + schema
                    + " (this build understands " + SCHEMA_VERSION + ")");
        }

        Edition edition = Edition.parse(text(root, "edition"));
        if (edition == null) {
            throw new ManifestFormatException("unknown edition '" + text(root, "edition") + "'");
        }
        ReleaseChannel channel = ReleaseChannel.parse(text(root, "releaseChannel"));
        if (channel == null) {
            throw new ManifestFormatException(
                    "unknown releaseChannel '" + text(root, "releaseChannel") + "'");
        }

        try {
            return new ReleaseManifest(
                    edition,
                    text(root, "clientVersion"),
                    text(root, "minecraftVersion"),
                    text(root, "adapterVersion"),
                    intField(root, "protocolVersion"),
                    text(root, "loaderRequirement"),
                    text(root, "javaRequirement"),
                    text(root, "artifact"),
                    text(root, "sha256"),
                    text(root, "signature"),
                    channel);
        } catch (IllegalArgumentException e) {
            throw new ManifestFormatException(e.getMessage());
        }
    }

    private static String text(JsonObject root, String field) {
        JsonElement element = root.get(field);
        if (element == null || element.isJsonNull()) {
            throw new ManifestFormatException("manifest is missing required field '" + field + "'");
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new ManifestFormatException("field '" + field + "' must be a string");
        }
        return element.getAsString();
    }

    private static int intField(JsonObject root, String field) {
        JsonElement element = root.get(field);
        if (element == null || element.isJsonNull()) {
            throw new ManifestFormatException("manifest is missing required field '" + field + "'");
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new ManifestFormatException("field '" + field + "' must be a number");
        }
        return element.getAsInt();
    }

    /**
     * The exact bytes a signature covers. Every identity-bearing field is included, in a fixed
     * order, so the signer and verifier cannot disagree about what was signed. {@code signature}
     * itself is excluded (it is the value being produced).
     */
    public String canonicalPayload() {
        List<String> fields = new ArrayList<>();
        fields.add("schemaVersion=" + SCHEMA_VERSION);
        fields.add("edition=" + edition.wire());
        fields.add("clientVersion=" + clientVersion);
        fields.add("minecraftVersion=" + minecraftVersion);
        fields.add("adapterVersion=" + adapterVersion);
        fields.add("protocolVersion=" + protocolVersion);
        fields.add("loaderRequirement=" + loaderRequirement);
        fields.add("javaRequirement=" + javaRequirement);
        fields.add("artifact=" + artifact);
        fields.add("sha256=" + sha256);
        fields.add("releaseChannel=" + releaseChannel.wire());
        return String.join("\n", fields);
    }

    public Edition edition() {
        return edition;
    }

    public String clientVersion() {
        return clientVersion;
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    public String adapterVersion() {
        return adapterVersion;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public String loaderRequirement() {
        return loaderRequirement;
    }

    public String javaRequirement() {
        return javaRequirement;
    }

    public String artifact() {
        return artifact;
    }

    public String sha256() {
        return sha256;
    }

    public String signature() {
        return signature;
    }

    public ReleaseChannel releaseChannel() {
        return releaseChannel;
    }

    /** Thrown when a manifest document cannot be trusted to be well-formed. */
    public static final class ManifestFormatException extends RuntimeException {
        public ManifestFormatException(String message) {
            super(message);
        }
    }
}
