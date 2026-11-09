#!/usr/bin/env python3
"""Generate the LOGVEX CRYSTAL compatibility manifest.

Reads the machine-generated inventory (``versions.json``) and the hand-verified
breaking-change register (``breaking-changes.json``) and writes
``compatibility-manifest.json``: for every Minecraft version in range, the API
changes that apply, the affected files and the adapter that absorbs the change.

The manifest is what the build and the docs consume, so the "what breaks where"
story has exactly one home. Regenerate after changing the inventory or the
breaking-change register.

Usage:
    python3 build-system/generate_manifest.py
    python3 build-system/generate_manifest.py --check
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
INVENTORY = ROOT / "versions.json"
BREAKING = ROOT / "breaking-changes.json"
OUT = ROOT / "compatibility-manifest.json"


def version_key(v: str):
    import re

    parts = re.split(r"[.\-]", v)
    key = []
    for p in parts:
        key.append((0, int(p), "") if p.isdigit() else (1, 0, p))
    return key


def applies(entry: dict, version: str) -> bool:
    """A change applies when the version is strictly older than where it landed.

    A change is introduced at ``introduced``; every version at or above it already
    has the new API and therefore does *not* need the compatibility shim.
    """
    return version_key(version) < version_key(entry["introduced"])


def compat_band(family: str, version: str) -> str:
    """Mirror of the band selection in build.gradle, so the manifest and the build agree."""
    if family == "mc-26.x":
        return "mojang"
    parts = [int(p) if p.isdigit() else 0 for p in version.split(".")]
    parts += [0] * (3 - len(parts))

    def at_least(floor):
        return tuple(parts[:3]) >= tuple(floor)

    if not at_least((1, 21, 6)):
        return "pre-matrix"
    if not at_least((1, 21, 9)):
        return "pre-world"
    if not at_least((1, 21, 10)):
        return "no-world-event"
    if not at_least((1, 21, 11)):
        return "pre-layers"
    return "modern"


def main() -> int:
    inventory = json.loads(INVENTORY.read_text())
    register = json.loads(BREAKING.read_text())["entries"]
    bands = json.loads(BREAKING.read_text()).get("bands", {})
    rows = {v["minecraft"]: v for v in inventory["versions"]}
    versions = [v["minecraft"] for v in inventory["versions"]]

    per_version = {}
    for v in versions:
        row = rows[v]
        changes = [e for e in register if applies(e, v)]
        # The second, larger axis: the 1 -> 26 boundary swaps the naming scheme. 26.x ships
        # Mojang official names, so every Yarn-named reference in the tree must be adapted.
        namespace_migration = row["mappingsScheme"] == "mojang-names-in-jar"
        per_version[v] = {
            "family": row["family"],
            "nameSpace": "yarn" if not namespace_migration else "mojang-official",
            "requiresNamespaceMigration": namespace_migration,
            "compatBand": compat_band(row["family"], v),
            "applicableChanges": [e["id"] for e in changes],
            "pendingChanges": [e["id"] for e in changes if not e.get("implemented", True)],
            "affectedFiles": sorted({f for e in changes for f in e["affectedFiles"]}),
            "adapters": sorted({b for e in changes for b in e.get("bands", [])}),
        }

    out = {
        "primary": inventory["primary"],
        "range": inventory["range"],
        "bands": bands,
        "breakingChanges": register,
        "perVersion": per_version,
        "summary": {
            "totalVersions": len(versions),
            "versionsNeedingShims": sum(
                1 for v in versions if per_version[v]["applicableChanges"]
            ),
            "versionsNeedingNamespaceMigration": sum(
                1 for v in versions if per_version[v]["requiresNamespaceMigration"]
            ),
            "versionsWithPendingWork": sorted(
                v for v in versions if per_version[v]["pendingChanges"]
            ),
            "distinctAdapters": sorted({b for e in register for b in e.get("bands", [])}),
        },
    }
    text = json.dumps(out, indent=2, sort_keys=False) + "\n"

    if "--check" in sys.argv:
        current = OUT.read_text() if OUT.exists() else ""
        if current != text:
            print("compatibility-manifest.json is out of date", file=sys.stderr)
            return 1
        print("compatibility-manifest.json is up to date")
        return 0

    OUT.write_text(text)
    print(
        f"wrote {OUT} "
        f"({out['summary']['totalVersions']} versions, "
        f"{out['summary']['versionsNeedingShims']} needing shims)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
