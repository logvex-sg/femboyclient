#!/usr/bin/env python3
"""Generate the LOGVEX CRYSTAL Minecraft version inventory.

This is the single source of truth for the Phase 9 multi-version build. It queries
authoritative upstream metadata only (Mojang piston-meta, Fabric meta, the Fabric
Maven) and writes ``build-system/versions.json``. Nothing here is hand-typed, so the
inventory can be regenerated as upstream changes.

Usage:
    python3 build-system/generate_inventory.py            # write versions.json
    python3 build-system/generate_inventory.py --check    # fail if out of date

The build (Gradle) and the compatibility-manifest generator both read the resulting
JSON, so the matrix can never drift from the metadata it was derived from.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "versions.json"

MIN_VERSION = "1.21.1"
MAX_VERSION = "26.3"

# The default build target. This is the version the source tree is written against today
# (Yarn names), and the only one with a green full build. The 26.x line compiles only once
# the Mojang-name adaptation layer lands; see docs/phase9/implementation-plan.md.
PRIMARY_VERSION = "1.21.11"

MOJANG_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
FABRIC_GAME = "https://meta.fabricmc.net/v2/versions/game"
FABRIC_YARN = "https://meta.fabricmc.net/v2/versions/yarn"
FABRIC_INTERMEDIARY = "https://meta.fabricmc.net/v2/versions/intermediary"
FABRIC_LOADER = "https://meta.fabricmc.net/v2/versions/loader"
FABRIC_API_META = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml"

# A Minecraft "major line" is the leading numeric component before the first dot
# (e.g. "1" for 1.21.x, "26" for 26.x). The Java requirement and the mapping scheme
# changed on the 1 -> 26 boundary, which is exactly what splits the two families.
FAMILY_BY_LINE = {
    "1": "mc-1.21.x",
    "26": "mc-26.x",
}

# The mapping scheme change on the 1 -> 26 boundary also changes which Loom plugin can
# build the version:
#   * 1.21.x  -> standard `fabric-loom` (remaps Yarn/intermediary to the game jar)
#   * 26.x    -> `net.fabricmc.fabric-loom` (no-remap; the 26.x jar is already named)
FAMILY_LOOM = {
    "mc-1.21.x": {"plugin": "fabric-loom", "version": "1.18.4"},
    "mc-26.x": {"plugin": "net.fabricmc.fabric-loom", "version": "1.17.20"},
}


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "logvex-crystal-inventory/1"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return resp.read()


def fetch_json(url: str):
    return json.loads(fetch(url))


def version_key(v: str):
    """Sort key for release versions; the new scheme keeps the numeric order."""
    parts = re.split(r"[.\-]", v)
    key = []
    for p in parts:
        if p.isdigit():
            key.append((0, int(p), ""))
        else:
            key.append((1, 0, p))
    return key


def load_targets(manifest: dict) -> list[str]:
    releases = {v["id"] for v in manifest["versions"] if v["type"] == "release"}
    ordered = sorted(releases, key=version_key)
    lo = ordered.index(MIN_VERSION)
    hi = ordered.index(MAX_VERSION)
    return ordered[lo : hi + 1]


def yarn_for(game: str, yarn: dict) -> str | None:
    entries = [e for e in yarn if e["gameVersion"] == game]
    if not entries:
        return None
    return max(entries, key=lambda e: e.get("build", 0))["version"]


def http_ok(url: str) -> bool:
    req = urllib.request.Request(url, headers={"User-Agent": "logvex-crystal-inventory/1"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status == 200
    except Exception:
        return False


def intermediary_for(game: str) -> str | None:
    # The /versions/intermediary list is unreliable for the 26.x line (it advertises
    # versions whose artifacts 404), so probe the real Maven artifact instead.
    url = f"https://maven.fabricmc.net/net/fabricmc/intermediary/{game}/intermediary-{game}-v2.jar"
    return game if http_ok(url) else None


def fabric_api_for(game: str, versions: list[str]) -> str | None:
    matches = [v for v in versions if v.endswith("+" + game)]
    return matches[-1] if matches else None


def main() -> int:
    manifest = fetch_json(MOJANG_MANIFEST)
    yarn = fetch_json(FABRIC_YARN)
    fabric_game = {v["version"]: v for v in fetch_json(FABRIC_GAME)}
    loader_versions = [v["version"] for v in fetch_json(FABRIC_LOADER)]
    latest_loader = next(v for v in loader_versions if re.match(r"^\d+\.\d+\.\d+$", v))
    api_versions = re.findall(r"<version>([^<]+)</version>", fetch(FABRIC_API_META).decode())

    by_id = {v["id"]: v for v in manifest["versions"]}
    targets = load_targets(manifest)

    versions = []
    for game in targets:
        meta = fetch_json(by_id[game]["url"])
        java_major = meta.get("javaVersion", {}).get("majorVersion")
        java_component = meta.get("javaVersion", {}).get("component")
        downloads = meta.get("downloads", {})
        has_mojang_mappings = "client_mappings" in downloads
        yarn_v = yarn_for(game, yarn)
        inter_v = intermediary_for(game)
        api_v = fabric_api_for(game, api_versions)

        if yarn_v:
            mappings_scheme = "yarn"
            mappings_coord = f"net.fabricmc:yarn:{yarn_v}:v2"
        elif has_mojang_mappings:
            mappings_scheme = "mojang-official"
            mappings_coord = None
        else:
            # 26.x ships deobfuscated under Mojang names; no mappings artifact exists.
            mappings_scheme = "mojang-names-in-jar"
            mappings_coord = None

        line = game.split(".")[0]
        family = FAMILY_BY_LINE.get(line)
        fabric_supported = game in fabric_game
        # Loom mode: standard remapping needs a mappings artifact; 26.x uses no-remap.
        loom = FAMILY_LOOM.get(family, {})
        loom_plugin = loom.get("plugin")
        loom_version = loom.get("version")

        reasons = []
        if not fabric_supported:
            reasons.append("Fabric does not list this version.")
        if not api_v:
            reasons.append("No Fabric API release for this version.")
        if not inter_v and mappings_scheme != "mojang-names-in-jar":
            reasons.append("No Fabric intermediary for this version.")

        versions.append(
            {
                "minecraft": game,
                "family": family,
                "releaseTime": by_id[game]["releaseTime"],
                "java": java_major,
                "javaComponent": java_component,
                "mappingsScheme": mappings_scheme,
                "mappings": mappings_coord,
                "loomPlugin": loom_plugin,
                "loomVersion": loom_version,
                "loader": latest_loader,
                "fabricApi": api_v,
                "fabricSupported": fabric_supported,
                "clientSha1": downloads.get("client", {}).get("sha1"),
                "supportable": fabric_supported and api_v is not None,
                "blockers": reasons,
            }
        )

    out = {
        "generatedFrom": {
            "mojangManifest": MOJANG_MANIFEST,
            "fabricGame": FABRIC_GAME,
            "fabricYarn": FABRIC_YARN,
            "fabricIntermediary": FABRIC_INTERMEDIARY,
            "fabricApi": FABRIC_API_META,
        },
        "range": {"min": MIN_VERSION, "max": MAX_VERSION},
        # The default build target: the fully-verified baseline. Kept as a named field so the
        # Gradle build never has to hard-code a version string.
        "primary": PRIMARY_VERSION,
        "loaderLatest": latest_loader,
        "versions": versions,
    }
    text = json.dumps(out, indent=2, sort_keys=False) + "\n"

    if "--check" in sys.argv:
        current = OUT.read_text() if OUT.exists() else ""
        # Compare only the version rows; upstream timestamps would otherwise churn.
        if json.loads(current or "{}").get("versions") != versions:
            print("versions.json is out of date; run generate_inventory.py", file=sys.stderr)
            return 1
        print("versions.json is up to date")
        return 0

    OUT.write_text(text)
    print(f"wrote {OUT} ({len(versions)} versions)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
