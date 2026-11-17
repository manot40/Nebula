#!/usr/bin/env python3
"""Audit gaps between resources/bin/ tables and server-side src/main/resources/defs/ + PlayerErrorCode.java."""

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


ENUM_ENTRY_RE = re.compile(r"^\s*(\w+)\s*\(\s*(-?\d+)\s*\)", re.MULTILINE)


def repo_root() -> Path:
    return Path(__file__).resolve().parents[4]


def load_json(path: Path) -> Any:
    if not path.is_file():
        return None
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def parse_java_enum(java_path: Path) -> dict[int, str]:
    if not java_path.is_file():
        return {}
    text = java_path.read_text(encoding="utf-8")
    return {int(m.group(2)): m.group(1) for m in ENUM_ENTRY_RE.finditer(text)}


def audit_achievements(root: Path, lang: str, emit_stubs: bool) -> None:
    bin_data = load_json(root / "resources" / "bin" / "Achievement.json") or {}
    lang_map = load_json(root / "resources" / "language" / lang / "Achievement.json") or {}
    param_list = load_json(root / "src" / "main" / "resources" / "defs" / "AchievementParam.json") or []
    cond_enum = parse_java_enum(
        root / "src" / "main" / "java" / "emu" / "nebula" / "game" / "achievement" / "AchievementCondition.java"
    )

    param_by_id = {int(item["Id"]): item for item in param_list if isinstance(item, dict) and "Id" in item}
    conds_with_params: set[int] = set()
    cond_totals: dict[int, int] = {}
    cond_param_counts: dict[int, int] = {}

    for row in bin_data.values():
        if not isinstance(row, dict):
            continue
        aid = int(row.get("Id", 0))
        cond = int(row.get("CompleteCond", 0))
        cond_totals[cond] = cond_totals.get(cond, 0) + 1
        if aid in param_by_id:
            conds_with_params.add(cond)
            cond_param_counts[cond] = cond_param_counts.get(cond, 0) + 1

    missing: list[dict[str, Any]] = []
    unknown_cond_rows: list[dict[str, Any]] = []
    for row in bin_data.values():
        if not isinstance(row, dict):
            continue
        aid = int(row.get("Id", 0))
        cond = int(row.get("CompleteCond", 0))
        if cond and cond not in cond_enum:
            unknown_cond_rows.append(row)
        if cond in conds_with_params and aid not in param_by_id:
            missing.append(row)

    print(f"=== AchievementParam Audit ({len(param_by_id)} param defs / {len(bin_data)} bin records) ===")
    if unknown_cond_rows:
        unknown_conds = sorted({int(r["CompleteCond"]) for r in unknown_cond_rows})
        print(f"  [WARN] CompleteCond values missing from AchievementCondition.java: {unknown_conds}")

    print(f"  Missing AchievementParam entries for parameterized conditions: {len(missing)}")
    stubs = []
    for row in missing:
        aid = int(row["Id"])
        cond = int(row.get("CompleteCond", 0))
        cond_name = cond_enum.get(cond, f"Unknown({cond})")
        ratio = f"{cond_param_counts.get(cond, 0)}/{cond_totals.get(cond, 0)}"
        title = lang_map.get(row.get("Title", ""), row.get("Title", ""))
        desc = lang_map.get(row.get("Desc", ""), row.get("Desc", ""))
        aim = row.get("AimNumShow", 1)
        print(f"    Id={aid:<5} Cond={cond:<3} ({cond_name}, {ratio} parameterized) Aim={aim:<4} | {title} -- {desc}")
        stubs.append({"Id": aid, "Param1": 0, "ParamCond1": 0})

    if emit_stubs and stubs:
        print("\n  // Suggested AchievementParam.json stubs (replace Param1 with target ID):")
        print(json.dumps(stubs, indent=2))


def audit_quest_params(
    root: Path,
    lang: str,
    bin_name: str,
    param_name: str,
    emit_stubs: bool,
    stub_template: dict[str, Any],
) -> None:
    bin_data = load_json(root / "resources" / "bin" / f"{bin_name}.json") or {}
    lang_map = load_json(root / "resources" / "language" / lang / f"{bin_name}.json") or {}
    param_list = load_json(root / "src" / "main" / "resources" / "defs" / f"{param_name}.json") or []
    param_ids = {int(item["Id"]) for item in param_list if isinstance(item, dict) and "Id" in item}

    missing: list[dict[str, Any]] = []
    for row in bin_data.values():
        if isinstance(row, dict) and int(row.get("Id", 0)) not in param_ids:
            missing.append(row)

    print(f"\n=== {param_name} Audit ({len(param_ids)} param defs / {len(bin_data)} bin records) ===")
    print(f"  Missing {param_name} entries: {len(missing)}")
    stubs = []
    for row in missing:
        qid = int(row["Id"])
        title = lang_map.get(row.get("Title", ""), row.get("Title", ""))
        print(f"    Id={qid:<7} | {title}")
        stub = {"Id": qid, **stub_template}
        stubs.append(stub)

    if emit_stubs and stubs:
        print(f"\n  // Suggested {param_name}.json stubs:")
        print(json.dumps(stubs, indent=2))


def audit_error_codes(root: Path, lang: str, emit_stubs: bool) -> None:
    bin_data = load_json(root / "resources" / "bin" / "ErrorCode.json") or {}
    lang_map = load_json(root / "resources" / "language" / lang / "ErrorCode.json") or {}
    java_path = root / "src" / "main" / "java" / "emu" / "nebula" / "game" / "player" / "PlayerErrorCode.java"
    existing = parse_java_enum(java_path)

    missing: list[tuple[int, str, str]] = []
    for row in bin_data.values():
        if not isinstance(row, dict):
            continue
        eid = int(row.get("Id", 0))
        title = str(row.get("Title", f"Err{eid}"))
        tmpl_key = str(row.get("Template", ""))
        tmpl_text = lang_map.get(tmpl_key, "")
        if eid not in existing:
            missing.append((eid, title, tmpl_text))

    print(f"\n=== PlayerErrorCode Audit ({len(existing)} Java enum values / {len(bin_data)} bin records) ===")
    print(f"  Missing error codes in PlayerErrorCode.java: {len(missing)}")
    for eid, title, tmpl_text in missing:
        print(f"    {title}({eid}), // {tmpl_text}")

    if emit_stubs and missing:
        print("\n  // Ready-to-paste PlayerErrorCode.java lines:")
        for eid, title, _ in missing:
            print(f"    {title}({eid}),")


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description="Audit custom server params and error code enums")
    parser.add_argument(
        "--target",
        choices=["all", "achievements", "dorm", "startower", "errors"],
        default="all",
        help="Subsystem to audit (default: all)",
    )
    parser.add_argument("--lang", default="en_US", help="Language code under resources/language/ (default: en_US)")
    parser.add_argument("--emit-stubs", action="store_true", help="Print JSON or Java enum stubs for missing entries")
    args = parser.parse_args()

    root = repo_root()
    if args.target in ("all", "achievements"):
        audit_achievements(root, args.lang, args.emit_stubs)
    if args.target in ("all", "dorm"):
        audit_quest_params(
            root,
            args.lang,
            "DormQuest",
            "DormQuestParam",
            args.emit_stubs,
            {"CompleteCond": 0, "CondParams": [1]},
        )
    if args.target in ("all", "startower"):
        audit_quest_params(
            root,
            args.lang,
            "StarTowerQuest",
            "StarTowerQuestParam",
            args.emit_stubs,
            {"CompleteCond": 0, "Aim": 1, "Param1": 0, "ParamCond1": 0},
        )
    if args.target in ("all", "errors"):
        audit_error_codes(root, args.lang, args.emit_stubs)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
