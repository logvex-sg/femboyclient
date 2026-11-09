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
7. `verifyAll` — both editions build for every supported Minecraft version.

If any step fails, the test report is uploaded as an artifact so the failure is visible without a
re-run. Concurrency cancels superseded runs on the same ref, so a busy branch does not queue.

## `gated-release.yml` — tags matching `v*`

A release runs the same `verifyAll` gate first. Only then does it:

1. `buildAll` — assemble both editions for the primary version.
2. write the signing key from the `LOGVEX_RELEASE_SIGNING_KEY` secret to a `0600` file outside the
   workspace;
3. `signRelease -PsigningKey=...` — sign one manifest per edition;
4. verify each manifest against the exact staged artifact, using the public key it just wrote;
5. collect the jars, manifests, build metadata and `SHA256SUMS` into `release-assets/`;
6. publish them as a GitHub release with generated notes;
7. delete the key file, always, even on failure.

The key is written with `umask 077` into `$RUNNER_TEMP`, never echoed, and removed in an
`if: always()` step. If the secret is unset the workflow fails before signing rather than publishing
an unsigned release.

## Required repository configuration

- Secret `LOGVEX_RELEASE_SIGNING_KEY`: the **private** Ed25519 key in PEM form. Generate it once
  offline (see `docs/RELEASE_SIGNING.md`) and store only the private half here; the public half is
  committed at `src/main/resources/logvex-release-key.txt`.

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
