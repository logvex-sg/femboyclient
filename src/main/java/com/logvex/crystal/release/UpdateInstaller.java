package com.logvex.crystal.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Installs a verified artifact next to the running one, keeping the previous file recoverable.
 *
 * <p>The running jar is never overwritten in place. The sequence is:
 *
 * <ol>
 *   <li>the verified download already sits in a temporary location;</li>
 *   <li>the current jar is moved aside into a {@code backups/} directory;</li>
 *   <li>the new jar is moved into the mods directory under its final name;</li>
 *   <li>if any step fails, the previous jar is moved back, so the installation is never left
 *       without a loadable mod.</li>
 * </ol>
 *
 * <p>The class is deliberately not wired to the network or to a specific mods directory: the caller
 * supplies the paths, so the same logic serves the in-game updater, a test, and the release tooling.
 * Installation happens only after {@link ArtifactVerifier} has passed, which the caller must have
 * checked — {@link #install} re-checks the file's checksum as a final guard.
 */
public final class UpdateInstaller {

    private static final Logger LOG = LoggerFactory.getLogger("LogvexCrystal");

    private final Path modsDirectory;
    private final Path backupDirectory;

    public UpdateInstaller(Path modsDirectory) {
        this.modsDirectory = modsDirectory;
        this.backupDirectory = modsDirectory.resolve("logvex-backups");
    }

    /** A record of one installation, so the UI can report what happened. */
    public record Outcome(boolean installed, Path installedFile, Path backupFile, String reason) {
    }

    /**
     * Installs {@code downloaded} (a fully verified jar in a temporary directory) over
     * {@code currentJar}, keeping the old file in {@code logvex-backups/}.
     *
     * @throws IOException when the filesystem refuses a move; the previous jar is restored first.
     */
    public Outcome install(Path downloaded, Path currentJar, ReleaseManifest manifest) throws IOException {
        if (!Files.isRegularFile(downloaded)) {
            return new Outcome(false, null, null, "downloaded artifact is missing");
        }
        String actual;
        try {
            actual = ArtifactVerifier.sha256(downloaded);
        } catch (IOException e) {
            return new Outcome(false, null, null, "could not read downloaded artifact: " + e.getMessage());
        }
        if (!actual.equalsIgnoreCase(manifest.sha256())) {
            return new Outcome(false, null, null,
                    "downloaded artifact failed its final checksum check");
        }

        Files.createDirectories(modsDirectory);
        Files.createDirectories(backupDirectory);

        Path backup = null;
        boolean hadCurrent = Files.isRegularFile(currentJar);
        if (hadCurrent) {
            backup = backupDirectory.resolve(currentJar.getFileName() + ".bak");
            Files.move(currentJar, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        Path target = modsDirectory.resolve(manifest.artifact());
        try {
            Files.move(downloaded, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Put the previous jar back so the mods directory still holds a loadable mod.
            if (hadCurrent && backup != null) {
                Files.move(backup, currentJar, StandardCopyOption.REPLACE_EXISTING);
            }
            throw e;
        }
        LOG.info("Installed {} {} (previous kept at {})", manifest.edition().wire(),
                manifest.clientVersion(), backup);
        return new Outcome(true, target, backup, "installed");
    }

    /**
     * Rolls back the most recent installation: the backup is moved back over the current jar.
     *
     * @return true when a backup existed and was restored.
     */
    public boolean rollback(Path currentJar) throws IOException {
        Path backup = backupDirectory.resolve(currentJar.getFileName() + ".bak");
        if (!Files.isRegularFile(backup)) {
            return false;
        }
        Files.move(backup, currentJar, StandardCopyOption.REPLACE_EXISTING);
        LOG.info("Rolled back {}", currentJar.getFileName());
        return true;
    }

    /** The backups this installation keeps, newest first. */
    public List<Path> backups() throws IOException {
        if (!Files.isDirectory(backupDirectory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(backupDirectory)) {
            return stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparingLong(UpdateInstaller::lastModified).reversed())
                    .toList();
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    /** The directory backups are kept in, so the UI can show it. */
    public Path backupDirectory() {
        return backupDirectory;
    }
}
