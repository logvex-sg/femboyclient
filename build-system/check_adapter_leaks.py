#!/usr/bin/env python3
"""Detect accidental references to version-specific classes from shared code.

Shared modules must not name a Minecraft or Fabric symbol that only exists on part of
the supported version range; those call sites belong behind ``compat/Compat.java`` or in
``src/versioned/<band>/java``. The forbidden symbols are listed in
``build-system/adapter-guard.json`` (derived from ``breaking-changes.json``), and the
roots that are allowed to name them are listed there too.

Usage:
    python3 build-system/check_adapter_leaks.py           # scan, exit 1 on any leak
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CONFIG = Path(__file__).resolve().parent / "adapter-guard.json"


def main() -> int:
    config = json.loads(CONFIG.read_text())
    patterns = [(re.compile(p["pattern"]), p["reason"]) for p in config["forbidden"]]
    exempt = tuple(config["exemptPrefixes"])
    roots = [ROOT / r for r in config["sharedRoots"]]

    leaks = []
    scanned = 0
    for root in roots:
        for path in sorted(root.rglob("*.java")):
            rel = path.relative_to(ROOT).as_posix()
            if any(rel.startswith(prefix) for prefix in exempt):
                continue
            scanned += 1
            text = path.read_text(encoding="utf-8")
            for lineno, line in enumerate(text.splitlines(), 1):
                stripped = line.strip()
                if stripped.startswith("//") or stripped.startswith("*"):
                    continue
                for pattern, reason in patterns:
                    if pattern.search(line):
                        leaks.append((rel, lineno, stripped, reason))

    if leaks:
        print(f"adapter-leak guard FAILED: {len(leaks)} reference(s) in {scanned} shared files",
              file=sys.stderr)
        for rel, lineno, line, reason in leaks:
            print(f"  {rel}:{lineno}: {line}", file=sys.stderr)
            print(f"      -> {reason}", file=sys.stderr)
        return 1

    print(f"adapter-leak guard OK: scanned {scanned} shared files, 0 forbidden references")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
