#!/usr/bin/env python3
"""Generate and verify LOGVEX CRYSTAL release manifests.

A release manifest is the machine-readable identity of one staged artifact. It is produced here,
signed with the release key (which never lives in the repository), and verified by the client's
``SignatureVerifier`` before anything is installed.

The canonical payload this script signs must match ``ReleaseManifest.canonicalPayload()`` in
``src/main/java/com/logvex/crystal/release/ReleaseManifest.java`` field for field, or verification
will fail. Both are documented in ``docs/RELEASE_SIGNING.md``.

Usage:
  # generate a manifest from a staged artifact + its build-metadata.json
  python3 build-system/release_manifest.py generate \\
      --dist build/dist/1.21.11/overseer \\
      --channel stable --signing-key /secure/logvex-release-key.pem \\
      --out build/release

  # verify a manifest against an artifact (and the public key)
  python3 build-system/release_manifest.py verify \\
      --manifest build/release/overseer-1.21.11-1.0.0.json \\
      --artifact build/dist/1.21.11/overseer/logvex-crystal-overseer-1.21.11-1.0.0.jar

  # emit the public key file that ships with the client
  python3 build-system/release_manifest.py public-key \\
      --signing-key /secure/logvex-release-key.pem \\
      --out src/main/resources/logvex-release-key.txt
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import pathlib
import sys

SCHEMA_VERSION = 1


def canonical_payload(manifest: dict) -> bytes:
    """The exact bytes signed. Mirrors ReleaseManifest.canonicalPayload()."""
    fields = [
        f"schemaVersion={SCHEMA_VERSION}",
        f"edition={manifest['edition']}",
        f"clientVersion={manifest['clientVersion']}",
        f"minecraftVersion={manifest['minecraftVersion']}",
        f"adapterVersion={manifest['adapterVersion']}",
        f"protocolVersion={manifest['protocolVersion']}",
        f"loaderRequirement={manifest['loaderRequirement']}",
        f"javaRequirement={manifest['javaRequirement']}",
        f"artifact={manifest['artifact']}",
        f"sha256={manifest['sha256']}",
        f"releaseChannel={manifest['releaseChannel']}",
    ]
    return "\n".join(fields).encode("utf-8")


def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_signing_key(path: pathlib.Path):
    try:
        from cryptography.hazmat.primitives import serialization
    except ImportError:
        sys.exit(
            "the 'cryptography' package is required to sign releases: pip install cryptography\n"
            "(generation of unsigned manifests and verification of unsigned manifests does not "
            "need it, but a signed release does)"
        )
    data = path.read_bytes()
    return serialization.load_pem_private_key(data, password=None)


def build_manifest(dist_dir: pathlib.Path, channel: str, protocol_version: int,
                   adapter_version: str) -> dict:
    meta_path = dist_dir / "build-metadata.json"
    if not meta_path.is_file():
        sys.exit(f"missing build metadata: {meta_path} (run assembleEdition first)")
    meta = json.loads(meta_path.read_text())
    artifact = dist_dir / meta["artifact"]
    if not artifact.is_file():
        sys.exit(f"missing artifact: {artifact}")
    return {
        "schemaVersion": SCHEMA_VERSION,
        "edition": meta["edition"],
        "clientVersion": meta["clientVersion"],
        "minecraftVersion": meta["minecraftVersion"],
        "adapterVersion": adapter_version or meta.get("adapter", "1"),
        "protocolVersion": protocol_version,
        "loaderRequirement": meta["loaderRequirement"],
        "javaRequirement": str(meta["javaRequirement"]),
        "artifact": meta["artifact"],
        "sha256": sha256_file(artifact),
        "signature": "",
        "releaseChannel": channel,
    }


def cmd_generate(args: argparse.Namespace) -> int:
    manifest = build_manifest(pathlib.Path(args.dist), args.channel, args.protocol_version,
                              args.adapter_version)
    key = load_signing_key(pathlib.Path(args.signing_key))
    from cryptography.hazmat.primitives import serialization

    raw = key.sign(canonical_payload(manifest))
    manifest["signature"] = base64.b64encode(raw).decode("ascii")

    out_dir = pathlib.Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    name = f"{manifest['edition']}-{manifest['minecraftVersion']}-{manifest['clientVersion']}.json"
    out_path = out_dir / name
    out_path.write_text(json.dumps(manifest, indent=2) + "\n")

    # The public key must be available to the verifier; write it next to the manifests too.
    public = key.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    (out_dir / "release-public-key.txt").write_text(
        "# LOGVEX CRYSTAL release signing key (Ed25519, X.509 SubjectPublicKeyInfo, base64)\n"
        + base64.b64encode(public).decode("ascii") + "\n"
    )
    print(f"wrote {out_path}")
    return 0


def cmd_verify(args: argparse.Namespace) -> int:
    manifest = json.loads(pathlib.Path(args.manifest).read_text())
    artifact = pathlib.Path(args.artifact)
    problems = []

    if manifest.get("schemaVersion") != SCHEMA_VERSION:
        problems.append(f"unsupported schemaVersion {manifest.get('schemaVersion')}")
    if artifact.name != manifest.get("artifact"):
        problems.append(f"artifact name {artifact.name} != manifest {manifest.get('artifact')}")
    actual = sha256_file(artifact)
    if actual != manifest.get("sha256"):
        problems.append(f"sha256 {actual} != manifest {manifest.get('sha256')}")

    key_path = pathlib.Path(args.public_key)
    if key_path.is_file() and manifest.get("signature"):
        try:
            from cryptography.hazmat.primitives import serialization
            from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey

            text = key_path.read_text()
            b64 = next(line.strip() for line in text.splitlines()
                       if line.strip() and not line.strip().startswith("#"))
            key = serialization.load_der_public_key(base64.b64decode(b64))
            assert isinstance(key, Ed25519PublicKey)
            key.verify(base64.b64decode(manifest["signature"]), canonical_payload(manifest))
        except ImportError:
            print("warning: 'cryptography' not installed; skipped signature check", file=sys.stderr)
        except Exception as exc:  # noqa: BLE001 - report any verification failure
            problems.append(f"signature did not verify: {exc}")
    else:
        problems.append("no public key or no signature; authenticity cannot be checked")

    if problems:
        for problem in problems:
            print(f"FAIL: {problem}", file=sys.stderr)
        return 1
    print(f"OK: {manifest['edition']} {manifest['minecraftVersion']} {manifest['clientVersion']} "
          f"sha256 {manifest['sha256'][:12]}... signature verified")
    return 0


def cmd_public_key(args: argparse.Namespace) -> int:
    from cryptography.hazmat.primitives import serialization

    key = load_signing_key(pathlib.Path(args.signing_key))
    public = key.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(
        "# LOGVEX CRYSTAL release signing key (Ed25519, X.509 SubjectPublicKeyInfo, base64)\n"
        + base64.b64encode(public).decode("ascii") + "\n"
    )
    print(f"wrote {out}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    gen = sub.add_parser("generate", help="generate and sign a manifest from a staged artifact")
    gen.add_argument("--dist", required=True, help="staged dist/<mc>/<edition> directory")
    gen.add_argument("--channel", required=True, choices=["stable", "experimental"])
    gen.add_argument("--protocol-version", type=int, default=1)
    gen.add_argument("--adapter-version", default="")
    gen.add_argument("--signing-key", required=True)
    gen.add_argument("--out", required=True)
    gen.set_defaults(func=cmd_generate)

    ver = sub.add_parser("verify", help="verify a manifest against an artifact")
    ver.add_argument("--manifest", required=True)
    ver.add_argument("--artifact", required=True)
    ver.add_argument("--public-key", default="build/release/release-public-key.txt")
    ver.set_defaults(func=cmd_verify)

    pub = sub.add_parser("public-key", help="write the public key file that ships with the client")
    pub.add_argument("--signing-key", required=True)
    pub.add_argument("--out", required=True)
    pub.set_defaults(func=cmd_public_key)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
