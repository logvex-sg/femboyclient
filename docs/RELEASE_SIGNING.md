# Release Signing and Updates

A LOGVEX CRYSTAL release is not just a jar. It is a jar plus a signed manifest that says exactly
which artifact it is, and the client refuses to install anything it cannot authenticate.

## Why a signature and a checksum

A SHA-256 checksum proves a file was not corrupted in transit. It does not prove who produced the
file: anyone can compute a checksum over anything. Authenticity comes from an Ed25519 signature,
which only the holder of the release private key can produce.

The order of checks in the client is deliberate and each one answers a different question:

| Check | Question it answers |
| --- | --- |
| Signature | Did the LOGVEX release key holder produce this manifest? |
| SHA-256 | Are the downloaded bytes exactly the bytes that were signed? |
| Edition / Minecraft / protocol | Is this the right artifact for *this* installation? |

All three must pass. A failure is reported with a specific reason and the update is refused.

## The key

The private key never enters the repository. It lives in the release pipeline's protected secrets
(see the CI configuration) and on the maintainer's offline signing machine. Only the public key
ships with the client, as `src/main/resources/logvex-release-key.txt`:

```
# LOGVEX CRYSTAL release signing key (Ed25519, X.509 SubjectPublicKeyInfo, base64)
MCowBQYDK2VwAyEA...
```

The file is a comment header plus one base64 line, so a human can tell at a glance which key it is.
`SignatureVerifier.fromResource()` reads it; if it is absent the client simply cannot verify, and the
updater treats that as "no verified updates" rather than as a pass.

Generate a key pair once, on a machine you trust, and never commit the private half:

```bash
python3 - <<'PY'
from cryptography.hazmat.primitives.asymmetric import ed25519
from cryptography.hazmat.primitives import serialization
key = ed25519.Ed25519PrivateKey.generate()
open("logvex-release-key.pem", "wb").write(key.private_bytes(
    encoding=serialization.Encoding.PEM,
    format=serialization.PrivateFormat.PKCS8,
    encryption_algorithm=serialization.NoEncryption()))
PY
```

Keep `logvex-release-key.pem` offline. Commit only what `public-key` writes.

## Producing a signed release

```bash
# 1. Build and stage both editions (writes dist/<mc>/<edition>/ + build-metadata.json)
./gradlew buildAll

# 2. Generate and sign a manifest per staged artifact
for ed in standard overseer; do
  python3 build-system/release_manifest.py generate \
    --dist build/dist/1.21.11/$ed \
    --channel stable \
    --protocol-version 1 \
    --signing-key /secure/logvex-release-key.pem \
    --out build/release
done

# 3. Verify what you produced, before publishing
for ed in standard overseer; do
  python3 build-system/release_manifest.py verify \
    --manifest build/release/$ed-1.21.11-1.0.0.json \
    --artifact build/dist/1.21.11/$ed/*.jar
done

# 4. Publish the jar, the manifest, and the public key as release assets
```

Step 3 is not optional. A release that has not been re-verified from the exact bytes that will be
published is not a release.

## The manifest

```json
{
  "schemaVersion": 1,
  "edition": "overseer",
  "clientVersion": "1.0.0",
  "minecraftVersion": "1.21.11",
  "adapterVersion": "modern",
  "protocolVersion": 1,
  "loaderRequirement": ">=0.20.1",
  "javaRequirement": "21",
  "artifact": "logvex-crystal-overseer-1.21.11-1.0.0.jar",
  "sha256": "95954b31...",
  "signature": "JwWD+/2lBHaSKChn3PI9Y+3n6lMV...",
  "releaseChannel": "stable"
}
```

The parser is strict: an unknown `schemaVersion`, a missing field, an unknown `edition` or
`releaseChannel`, or a malformed `sha256` all raise `ManifestFormatException`. A manifest that
parses is well-formed; it is not yet trusted until its signature verifies.

### The signed payload

The signature covers a fixed, ordered text payload — every identity field except the signature
itself. Both the signer (Python) and the verifier (Java) build it identically:

```
schemaVersion=1
edition=overseer
clientVersion=1.0.0
minecraftVersion=1.21.11
adapterVersion=modern
protocolVersion=1
loaderRequirement=>=0.20.1
javaRequirement=21
artifact=logvex-crystal-overseer-1.21.11-1.0.0.jar
sha256=95954b31...
releaseChannel=stable
```

If you change one side, change the other. `ReleaseManifest.canonicalPayload()` and
`canonical_payload()` in `build-system/release_manifest.py` are the two definitions, and
`ReleaseManifestTest` locks the Java one.

## Channels

An artifact is `stable` or `experimental`. A stable artifact has passed every release gate; an
experimental one has not and must be advertised as such. The updater only offers an experimental
build when the user has opted into that channel (`ReleaseCatalog.bestUpdate(...)` takes the accepted
channels as an argument). The two are never mixed.

## Installing an update

`UpdateInstaller` never overwrites the running jar in place:

1. the verified download sits in a temporary location;
2. the current jar is moved into `mods/logvex-backups/`;
3. the new jar is moved into the mods directory under its final name;
4. if step 3 fails, the previous jar is moved back.

So an interrupted update never leaves the mods directory without a loadable mod, and
`UpdateInstaller.rollback()` restores the previous jar on request.

## What is verified, and where

| Layer | Where | What it guarantees |
| --- | --- | --- |
| Build metadata | `assembleEdition` | The staged artifact is the edition it claims, with the right classes. |
| Manifest format | `ReleaseManifest.parse` | The document is well-formed and self-consistent. |
| Signature | `SignatureVerifier` | The release key holder produced the manifest. |
| Artifact identity | `ArtifactVerifier` | The bytes, edition, Minecraft version and protocol all match. |
| Installation | `UpdateInstaller` | The running jar is preserved and recoverable. |

Each layer is unit-tested (`ReleaseManifestTest`, `ArtifactVerifierTest`, `ReleaseCatalogTest`), so a
regression in any of them fails the build rather than a user's update.
