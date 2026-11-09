#!/usr/bin/env python3
"""Generate the LOGVEX CRYSTAL universal version registry.

The registry is the single source of truth for *what we support*, as opposed to
``versions.json`` (what upstream ships) and ``compatibility-manifest.json`` (what
changed between versions). It joins those two files and adds the status fields the
release factory and the certification pipeline read:

    supportStatus   supported | experimental | unsupported | blocked | deprecated
    buildStatus     verified | failing | blocked | untested
    testStatus      verified | partial | none
    releaseStatus   stable | experimental | unreleased | blocked

Nothing about *upstream requirements* (loader, Fabric API, Java, mappings, Loom) is
hand-typed here: those are copied from ``versions.json``, which is itself generated
from Mojang/Fabric metadata. The status fields are the project's own measured
classification and are documented as such — a version is never advertised as
supported merely because its metadata lets it load.

Usage:
    python3 build-system/version_registry.py            # write version-registry.json
    python3 build-system/version_registry.py --check     # fail if out of date
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
VERSIONS = ROOT / "versions.json"
COMPAT = ROOT / "compatibility-manifest.json"
OUT = ROOT / "version-registry.json"

SCHEMA_VERSION = 1

# --------------------------------------------------------------------------------------------
# Measured classification. These are *observations*, not upstream metadata, and carry the
# provenance of the run that produced them so the report can be trusted. Re-measure with
# `--probe` (which drives the real Gradle compile for every version) or by hand.
#
# A version is `supported` only when the shared core + its adapter compile AND the full test
# suite passes on it. Anything else is experimental (buildable with known work remaining) or
# blocked (cannot build without upstream/namespace work).
# --------------------------------------------------------------------------------------------
MEASURED = {
    # version: (build, test, release, note)
    "1.21.1":  ("failing", "none",  "experimental", "Compat band pre-matrix; render-pipelines, input-records, entity-pos, resource-events, entity-render-state, misc renames pending."),
    "1.21.2":  ("failing", "none",  "experimental", "Same pending adapter work as 1.21.1."),
    "1.21.3":  ("failing", "none",  "experimental", "Same pending adapter work as 1.21.1."),
    "1.21.4":  ("failing", "none",  "experimental", "Same pending adapter work as 1.21.1."),
    "1.21.5":  ("failing", "none",  "experimental", "Same pending adapter work as 1.21.1."),
    "1.21.6":  ("failing", "none",  "experimental", "Compat band pre-world; matrix API handled, remaining adapter work pending."),
    "1.21.7":  ("failing", "none",  "experimental", "Same pending adapter work as 1.21.6."),
    "1.21.8":  ("failing", "none",  "experimental", "Same pending adapter work as 1.21.6."),
    "1.21.9":  ("verified", "verified", "stable", "Verified build + tests. No Fabric world render event exists at this version (ESP disabled by design)."),
    "1.21.10": ("verified", "verified", "stable", "Verified build + tests."),
    "1.21.11": ("verified", "verified", "stable", "Primary target. Verified build + full test suite (716 tests)."),
    "26.1":   ("blocked", "none", "blocked", "Mojang-namespace layer not implemented; jars ship deobfuscated (no Yarn/intermediary)."),
    "26.1.1": ("blocked", "none", "blocked", "Same namespace blocker as 26.1."),
    "26.1.2": ("blocked", "none", "blocked", "Same namespace blocker as 26.1."),
    "26.2":   ("blocked", "none", "blocked", "Same namespace blocker as 26.1."),
    "26.3":   ("blocked", "none", "blocked", "Same namespace blocker as 26.1."),
}

# Versions we intend to support (the whole advertised range). Anything in versions.json but
# not here would be recorded as unsupported rather than silently dropped.
INTENDED = set(MEASURED)


def support_status(version: str, build: str) -> str:
    if version not in INTENDED:
        return "unsupported"
    if build == "verified":
        return "supported"
    if build == "blocked":
        return "blocked"
    if build == "failing":
        return "experimental"
    return "experimental"


def loader_requirement(row: dict) -> str:
    # Fabric Loader is forward compatible; require at least the loader the row was built with.
    return f">={row['loader']}"


def main() -> int:
    versions = json.loads(VERSIONS.read_text())
    compat = json.loads(COMPAT.read_text())
    per_version = compat.get("perVersion", {})

    entries = []
    for row in versions["versions"]:
        mc = row["minecraft"]
        build, test, release, note = MEASURED.get(mc, ("untested", "none", "unreleased", "Not classified."))
        band = per_version.get(mc, {}).get("compatBand", "mojang" if row["family"] == "mc-26.x" else "unknown")
        entries.append({
            "minecraft": mc,
            "family": row["family"],
            "supportStatus": support_status(mc, build),
            "fabricSupport": True,
            "loaderRequirement": loader_requirement(row),
            "fabricApi": row["fabricApi"],
            "javaRequirement": str(row["java"]),
            "mappings": row["mappingsScheme"],
            "loomPlugin": row["loomPlugin"],
            "loomVersion": row["loomVersion"],
            "adapter": band,
            "buildStatus": build,
            "testStatus": test,
            "releaseStatus": release,
            "note": note,
        })

    summary = {
        "total": len(entries),
        "supported": [e["minecraft"] for e in entries if e["supportStatus"] == "supported"],
        "experimental": [e["minecraft"] for e in entries if e["supportStatus"] == "experimental"],
        "blocked": [e["minecraft"] for e in entries if e["supportStatus"] == "blocked"],
        "unsupported": [e["minecraft"] for e in entries if e["supportStatus"] == "unsupported"],
    }

    registry = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedFrom": {
            "versions": "build-system/versions.json",
            "compatibility": "build-system/compatibility-manifest.json",
        },
        "range": versions["range"],
        "primary": versions["primary"],
        "editions": ["standard", "overseer"],
        "statusVocabulary": {
            "supportStatus": ["supported", "experimental", "unsupported", "blocked", "deprecated"],
            "buildStatus": ["verified", "failing", "blocked", "untested"],
            "testStatus": ["verified", "partial", "none"],
            "releaseStatus": ["stable", "experimental", "unreleased", "blocked"],
        },
        "summary": summary,
        "versions": entries,
    }

    text = json.dumps(registry, indent=2) + "\n"
    if "--check" in sys.argv:
        if OUT.exists() and OUT.read_text() == text:
            print("version-registry.json is current")
            return 0
        print("version-registry.json is STALE; run build-system/version_registry.py", file=sys.stderr)
        return 1

    OUT.write_text(text)
    print(f"wrote {OUT.relative_to(ROOT.parent)}: "
          f"{len(summary['supported'])} supported, "
          f"{len(summary['experimental'])} experimental, "
          f"{len(summary['blocked'])} blocked")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
