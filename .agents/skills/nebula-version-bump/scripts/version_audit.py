#!/usr/bin/env python3
"""Consolidated version-bump audit across GameConstants, versions.json, ActivityModule, conditions, and custom params."""

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any


VERSION_CONST_RE = re.compile(r'public\s+static\s+final\s+String\s+VERSION\s*=\s*"([^"]+)"')
ACTIVITY_ID_RE = re.compile(r"this\.activities\.add\(\s*(\d+)\s*\)")
ENUM_ENTRY_RE = re.compile(r"^\s*(\w+)\s*\(\s*(-?\d+)\s*\)", re.MULTILINE)


def repo_root() -> Path:
    return Path(__file__).resolve().parents[4]


def load_json(path: Path) -> Any:
    if not path.is_file():
        return None
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def parse_enum_values(java_path: Path) -> dict[int, str]:
    if not java_path.is_file():
        return {}
    return {int(m.group(2)): m.group(1) for m in ENUM_ENTRY_RE.finditer(java_path.read_text(encoding="utf-8"))}


def check_versions(root: Path) -> None:
    gc_path = root / "src" / "main" / "java" / "emu" / "nebula" / "GameConstants.java"
    ver_path = root / "src" / "main" / "resources" / "versions.json"
    m = VERSION_CONST_RE.search(gc_path.read_text(encoding="utf-8")) if gc_path.is_file() else None
    version_str = m.group(1) if m else "UNKNOWN"
    versions_data = load_json(ver_path) or {}
    print("=== 1. Version Metadata ===")
    print(f"  GameConstants.VERSION : {version_str}")
    print(f"  versions.json         : {json.dumps(versions_data)}")


def check_conditions(root: Path) -> None:
    print("\n=== 2. Quest & Achievement Condition Enum Coverage ===")
    ach_enum = parse_enum_values(
        root / "src" / "main" / "java" / "emu" / "nebula" / "game" / "achievement" / "AchievementCondition.java"
    )
    quest_enum = parse_enum_values(
        root / "src" / "main" / "java" / "emu" / "nebula" / "game" / "quest" / "QuestCondition.java"
    )

    ach_bin = load_json(root / "resources" / "bin" / "Achievement.json") or {}
    missing_ach = sorted({
        int(r["CompleteCond"])
        for r in ach_bin.values()
        if isinstance(r, dict) and int(r.get("CompleteCond", 0)) not in ach_enum and int(r.get("CompleteCond", 0)) != 0
    })
    print(f"  AchievementCondition.java missing IDs (from Achievement.json) : {missing_ach or 'None'}")

    quest_tables = ["DailyQuest", "WeeklyQuest", "BattlePassQuest", "WorldClass", "StarTowerBookEventReward", "ActivityTask"]
    missing_quest: dict[int, list[str]] = {}
    for tname in quest_tables:
        tdata = load_json(root / "resources" / "bin" / f"{tname}.json") or {}
        for r in tdata.values():
            if not isinstance(r, dict):
                continue
            c = int(r.get("CompleteCond", 0))
            if c and c not in quest_enum:
                missing_quest.setdefault(c, [])
                if tname not in missing_quest[c]:
                    missing_quest[c].append(tname)
    if missing_quest:
        print(f"  QuestCondition.java missing IDs : {missing_quest}")
    else:
        print("  QuestCondition.java missing IDs (across quest tables)         : None")


def check_activities(root: Path, lang: str) -> None:
    print("\n=== 3. ActivityModule Coverage (resources/bin/Activity.json) ===")
    act_mod_path = root / "src" / "main" / "java" / "emu" / "nebula" / "game" / "activity" / "ActivityModule.java"
    act_text = act_mod_path.read_text(encoding="utf-8") if act_mod_path.is_file() else ""

    active_ids: set[int] = set()
    commented_ids: set[int] = set()
    for line in act_text.splitlines():
        m = ACTIVITY_ID_RE.search(line)
        if not m:
            continue
        aid = int(m.group(1))
        if line.lstrip().startswith("//"):
            commented_ids.add(aid)
        else:
            active_ids.add(aid)

    act_bin = load_json(root / "resources" / "bin" / "Activity.json") or {}
    act_lang = load_json(root / "resources" / "language" / lang / "Activity.json") or {}
    unreferenced: list[tuple[int, int, str]] = []
    for r in act_bin.values():
        if not isinstance(r, dict):
            continue
        aid = int(r.get("Id", 0))
        if aid not in active_ids and aid not in commented_ids:
            atype = int(r.get("ActivityType", 0))
            raw_name = str(r.get("Name", ""))
            name = act_lang.get(raw_name, raw_name)
            unreferenced.append((aid, atype, name))

    unreferenced.sort()
    print(f"  Active in ActivityModule.java    : {len(active_ids)}")
    print(f"  Commented in ActivityModule.java : {len(commented_ids)}")
    print(f"  Unreferenced in Activity.json    : {len(unreferenced)}")
    for aid, atype, name in unreferenced[-15:]:
        print(f"    Id={aid:<8} ActivityType={atype:<4} Name={name}")


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description="Run consolidated Nebula version bump audit")
    parser.add_argument("--lang", default="en_US", help="Language folder under resources/language/ (default: en_US)")
    args = parser.parse_args()

    root = repo_root()
    check_versions(root)
    check_conditions(root)
    check_activities(root, args.lang)

    print("\n=== 4. Custom Params & ErrorCode Audit ===")
    custom_script = root / ".agents" / "skills" / "nebula-custom-params" / "scripts" / "audit_custom_params.py"
    if custom_script.is_file():
        subprocess.run([sys.executable, str(custom_script), "--target", "all", "--lang", args.lang], check=False)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
