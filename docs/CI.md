# Continuous Integration

Two workflows under `.github/workflows/`. They exist so that the gate a release passes is the same
gate a pull request passes — nothing ships that a reviewer could not have run.

## `pr-validation.yml` — every pull request, and every push to `main`

The job installs JDK 25 (Loom's build JVM) and Python 3.12, then runs, in order:

1. `adapterGuard` — no shared code references a version-specific symbol.
2. `checkInventory` — `build-system/versions.json` is current against upstream metadata.
3. `checkManifest` — the compatibility manifest is current.
4. `checkVersionRegistry` — the version registry is current.
5. `checkSbom` — `docs/SBOM.md` matches the resolved dependency versions.
6. `test` — the full unit suite.
7. `verifyAll` — the client builds for every supported Minecraft version.

If any step fails, the test report is uploaded as an artifact so the failure is visible without a
re-run. Concurrency cancels superseded runs on the same ref, so a busy branch does not queue.

## `gated-release.yml` — tags matching `v*`

A release runs the same `verifyAll` gate first. Only then does it:

1. `buildAll` — assemble the client for the primary version;
2. collect the jar, its build metadata and a `SHA256SUMS` file into `release-assets/`;
3. publish them as a GitHub release with generated notes.

## Running the same gate locally

```bash
./scripts/setup-jdk.sh
export JAVA_HOME="$HOME/.local/logvex-jdk-25" PATH="$JAVA_HOME/bin:$PATH"
./gradlew adapterGuard checkInventory checkManifest checkVersionRegistry checkSbom
./gradlew test
./gradlew verifyAll
```

If those three commands are green locally, CI will be green. If they are not, CI will fail in exactly
the same place — the workflows add no checks that do not run locally.
