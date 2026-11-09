package com.logvex.crystal.compat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Fails the build if shared code references a Minecraft/Fabric symbol that only exists on part
 * of the supported version range.
 *
 * <p>This mirrors {@code build-system/check_adapter_leaks.py} (which CI also runs), but keeps
 * the rule enforceable from the ordinary test task, where a developer will see it first. The
 * forbidden symbols come from {@code build-system/adapter-guard.json}, which is derived from the
 * breaking-change register, so the rule tracks real API moves rather than a hand-kept list.
 */
class AdapterLeakGuardTest {

    @Test
    void sharedCodeDoesNotReferenceVersionSpecificSymbols() throws IOException {
        Path root = repositoryRoot();
        Path config = root.resolve("build-system/adapter-guard.json");
        assertTrue(Files.exists(config), "adapter-guard.json must exist");

        JsonObject json = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
        JsonArray forbidden = json.getAsJsonArray("forbidden");
        List<Pattern> patterns = new ArrayList<>();
        for (var element : forbidden) {
            patterns.add(Pattern.compile(element.getAsJsonObject().get("pattern").getAsString()));
        }
        List<String> exempt = new ArrayList<>();
        for (var element : json.getAsJsonArray("exemptPrefixes")) {
            exempt.add(element.getAsString());
        }

        List<String> leaks = new ArrayList<>();
        for (var rootName : json.getAsJsonArray("sharedRoots")) {
            Path sharedRoot = root.resolve(rootName.getAsString());
            if (!Files.isDirectory(sharedRoot)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(sharedRoot)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String rel = root.relativize(file).toString().replace('\\', '/');
                    if (exempt.stream().anyMatch(rel::startsWith)) {
                        continue;
                    }
                    List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size(); i++) {
                        String trimmed = lines.get(i).trim();
                        if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
                            continue;
                        }
                        for (Pattern pattern : patterns) {
                            if (pattern.matcher(lines.get(i)).find()) {
                                leaks.add(rel + ":" + (i + 1) + ": " + trimmed);
                            }
                        }
                    }
                }
            }
        }

        assertTrue(leaks.isEmpty(),
                "Version-specific symbols referenced from shared code (route them through "
                        + "compat/Compat.java or src/versioned/<band>):\n" + String.join("\n", leaks));
    }

    private static Path repositoryRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("build-system/adapter-guard.json"))) {
            dir = dir.getParent();
        }
        assertTrue(dir != null, "could not locate the repository root from " + System.getProperty("user.dir"));
        return dir;
    }
}
