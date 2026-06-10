#!/usr/bin/env python3
"""
filetree.py — FILETREE.md maintenance script

Usage:
    python filetree.py init [--force] generate from scratch
    python filetree.py update [--dry-run]           sync with repo state
    python filetree.py lint                         read-only drift check (CI-friendly)
    python filetree.py apply <part.json [...]>    apply LLM output

Each entry carries a git-hash of its content. Hash mismatch = stale summary.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import textwrap
from datetime import datetime
from pathlib import Path
from typing import Optional

REPO_ROOT = Path(__file__).parent.parent.resolve()
FILETREE = REPO_ROOT / "FILETREE.md"
HASH_COMMENT_RE = re.compile(r"<!--hash:([a-f0-9]+)-->")

# ─── git helpers ───────────────────────────────────────────────────────────────

def git(*args: str, capture=True) -> str:
    result = subprocess.run(
        ["git"] + list(args),
        cwd=REPO_ROOT,
        capture_output=capture,
        text=True,
    )
    if capture:
        return result.stdout.strip()
    return ""


def hash_file(path: Path) -> str:
    """git hash-object equivalent, independent of repo."""
    if path.is_symlink():
        return hashlib.md5(path.read_text().encode()).hexdigest()[:8]
    with open(path, "rb") as f:
        return hashlib.md5(f.read()).hexdigest()[:8]


def list_files() -> list[str]:
    """All tracked + untracked non-ignored files."""
    tracked = git("ls-files").splitlines()
    others = [
        line[3:].strip()
        for line in git("status", "--porcelain").splitlines()
        if line.startswith("??")
    ]
    all_files = tracked + others
    return sorted(set(all_files))


def detect_changes() -> dict:
    """Return {added, changed, removed, renamed} vs current FILETREE.md."""
    status_lines = git("status", "--porcelain").splitlines()
    current = _read_manifest()

    added: list[str] = []
    changed: list[str] = []
    removed: list[str] = []

    for line in status_lines:
        code = line[:2]
        path = line[3:].strip()
        if code == "??":
            if path not in current:
                added.append(path)
        elif code == "!!":
            continue # ignored
        else:
            if path in current:
                changed.append(path)
            else:
                # might be a rename source
                added.append(path)

    # Check for removed files (in current but not in repo)
    all_repo = set(list_files())
    for path in current:
        if path not in all_repo:
            removed.append(path)

    return {
        "added": sorted(added),
        "changed": sorted(changed),
        "removed": sorted(removed),
    }


def _read_manifest() -> dict[str, dict]:
    """Parse FILETREE.md into {relpath: {summary, hash}}."""
    if not FILETREE.exists():
        return {}
    current: dict[str, dict] = {}
    section = ""
    for line in FILETREE.read_text(encoding="utf-8").splitlines():
        m = re.match(r"^## (.+)/?$", line)
        if m:
            section = m.group(1)
            continue
        m = re.match(r"^- `(.*?)` — (.+?) <!--hash:([a-f0-9]+)-->$", line)
        if m:
            rel = m.group(1)
            summary = m.group(2)
            h = m.group(3)
            current[rel] = {"summary": summary, "hash": h, "section": section}
    return current


# ─── commands ────────────────────────────────────────────────────────────────

def cmd_init(force: bool = False) -> int:
    if FILETREE.exists() and not force:
        print(f"FILETREE.md already exists. Use --force to overwrite.", file=sys.stderr)
        return 1
    files = list_files()
    sections: dict[str, list[tuple[str, str]]] = {}
    for f in files:
        parts = Path(f).parts
        section = "/".join(parts[:-1]) if len(parts) > 1 else "(root)"
        sections.setdefault(section, []).append((f, ""))
    lines = [
        "# Project Filetree",
        "",
        f"_Auto-generated at {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}_",
        "",
    ]
    for section in sorted(sections):
        lines.append(f"## {section}/" if section != "(root)" else "## (root)/")
        for rel, _summary in sorted(sections[section], key=lambda x: x[0]):
            lines.append(f"- `{rel}` — <!--hash:00000000-->")
    FILETREE.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Generated {FILETREE} with {len(files)} entries.")
    return 0


def cmd_update(dry_run: bool = False) -> int:
    current = _read_manifest()
    files = list_files()
    sections: dict[str, list[str]] = {}

    added: list[str] = []
    changed: list[str] = []
    removed: list[str] = []

    status_lines = git("status", "--porcelain").splitlines()
    repo_paths = {line[3:].strip() for line in status_lines}
    repo_paths.update(list_files())

    for f in files:
        parts = Path(f).parts
        section = "/".join(parts[:-1]) if len(parts) > 1 else "(root)"
        sections.setdefault(section, []).append(f)

    for f in files:
        if f not in current:
            added.append(f)
    for f, info in current.items():
        if f not in files:
            removed.append(f)
        else:
            # hash changed?
            h = hash_file(REPO_ROOT / f)
            if h != info["hash"]:
                changed.append(f)

    print(f"# FILETREE.md drift report ({datetime.now().strftime('%Y-%m-%d %H:%M:%S')})")
    print(f"  added:   {len(added)}")
    print(f"  changed: {len(changed)}")
    print(f"  removed: {len(removed)}")

    if dry_run:
        print("(dry-run — no file written)")
        return 0

    # Rebuild manifest preserving existing summaries for changed items
    lines = [
        "# Project Filetree",
        "",
        f"_Auto-maintained. Sync at {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}_",
        "",
    ]
    all_sections = sorted(set(list(sections.keys()) + [c for v in current.values() for c in [v.get("section", "")] if c]))
    for section in all_sections:
        sec_files = sorted(sections.get(section, []))
        # Add files that only exist in current (not yet in repo but not removed)
        for f, info in current.items():
            if info.get("section") == section and f not in sec_files and f in files:
                sec_files.append(f)
        sec_files = sorted(set(sec_files))
        if not sec_files:
            continue
        lines.append(f"## {section}/" if section != "(root)" else "## (root)/")
        for rel in sec_files:
            if rel in current:
                summary = current[rel]["summary"]
                h = hash_file(REPO_ROOT / rel) if (REPO_ROOT / rel).exists() else current[rel]["hash"]
            else:
                summary = ""
                h = hash_file(REPO_ROOT / rel)
            lines.append(f"- `{rel}` — {summary} <!--hash:{h}-->")

    FILETREE.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Updated {FILETREE}.")
    return 0


def cmd_lint() -> int:
    """Read-only drift check. Exit0 if clean, 1 if drift found."""
    current = _read_manifest()
    drift: list[str] = []

    for rel, info in current.items():
        path = REPO_ROOT / rel
        if not path.exists():
            drift.append(f"removed: {rel}")
            continue
        h = hash_file(path)
        if h != info["hash"]:
            drift.append(f"changed: {rel} (hash {info['hash'][:8]} → {h})")

    all_repo = set(list_files())
    for rel in current:
        if rel not in all_repo and rel not in [d.split(": ", 1)[1] for d in drift]:
            drift.append(f"removed: {rel}")

    if drift:
        print("FILETREE.md drift detected:")
        for d in drift:
            print(f"  {d}")
        return 1
    print("FILETREE.md is clean.")
    return 0


def cmd_apply(part_files: list[str]) -> int:
    """Apply LLM output: merge part_N.json files into FILETREE.md."""
    updates: dict[str, str] = {}  # path → summary or "UNCHANGED"
    for pf in part_files:
        p = Path(pf).resolve()
        if not p.exists():
            print(f"WARNING: {p} not found, skipping", file=sys.stderr)
            continue
        data = json.loads(p.read_text(encoding="utf-8"))
        for item in data.get("updates", []):
            updates[item["path"]] = item["summary"]

    current = _read_manifest()
    for rel, summary in updates.items():
        if rel not in current:
            continue
        if summary == "UNCHANGED":
            # just refresh hash
            h = hash_file(REPO_ROOT / rel) if (REPO_ROOT / rel).exists() else current[rel]["hash"]
            current[rel]["hash"] = h
        else:
            h = hash_file(REPO_ROOT / rel) if (REPO_ROOT / rel).exists() else current[rel]["hash"]
            current[rel]["summary"] = summary
            current[rel]["hash"] = h

    # Rebuild file preserving section order
    sections: dict[str, list[tuple[str, dict]]] = {}
    for rel, info in current.items():
        sec = info.get("section", "(root)")
        sections.setdefault(sec, []).append((rel, info))
    lines = [
        "# Project Filetree",
        "",
        f"_Auto-maintained by filetree.py apply. Last sync: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}_",
        "",
    ]
    for section in sorted(sections):
        lines.append(f"## {section}/" if section != "(root)" else "## (root)/")
        for rel, info in sorted(sections[section], key=lambda x: x[0]):
            lines.append(f"- `{rel}` — {info['summary']} <!--hash:{info['hash']}-->")

    FILETREE.write_text("\n".join(lines) + "\n", encoding="utf-8")
    added = sum(1 for u in updates.values() if u != "UNCHANGED")
    unchanged = sum(1 for u in updates.values() if u == "UNCHANGED")
    print(f"Applied {added} new summaries, {unchanged} UNCHANGED. Updated {FILETREE}.")
    return 0


# ─── main ────────────────────────────────────────────────────────────────────

def main() -> int:
    parser = argparse.ArgumentParser(prog="filetree.py", description="FILETREE.md maintenance")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_init = sub.add_parser("init")
    p_init.add_argument("--force", action="store_true")
    p_update = sub.add_parser("update")
    p_update.add_argument("--dry-run", action="store_true")
    sub.add_parser("lint")
    p_apply = sub.add_parser("apply")
    p_apply.add_argument("part_files", nargs="+")

    args = parser.parse_args()

    if args.cmd == "init":
        return cmd_init(force=args.force)
    elif args.cmd == "update":
        return cmd_update(dry_run=args.dry_run)
    elif args.cmd == "lint":
        return cmd_lint()
    elif args.cmd == "apply":
        return cmd_apply(args.part_files)
    return 0


if __name__ == "__main__":
    sys.exit(main())