#!/usr/bin/env python3
"""Audit resources/bin/*.json against src/main/java/emu/nebula/data/resources/*Def.java and GameData.java."""

import argparse
import re
import sys
from pathlib import Path

from inspect_table import load_json, repo_root
from scaffold_def import collect_table_fields, infer_java_type, parse_existing_def_fields


RESOURCE_TYPE_RE = re.compile(r'@ResourceType\s*\(([^)]*)\)', re.DOTALL)
NAME_ATTR_RE = re.compile(r'name\s*=\s*"([^"]+)"')
GAMEDATA_TABLE_RE = re.compile(r'DataTable<\s*(\w+)\s*>\s+(\w+)\s*=')


def scan_def_classes(resources_java_dir: Path) -> dict[str, Path]:
    mapping: dict[str, Path] = {}
    for java_file in sorted(resources_java_dir.glob("*.java")):
        text = java_file.read_text(encoding="utf-8")
        m = RESOURCE_TYPE_RE.search(text)
        if not m:
            continue
        nm = NAME_ATTR_RE.search(m.group(1))
        if nm:
            mapping[nm.group(1)] = java_file
    return mapping


def scan_gamedata_tables(gamedata_path: Path) -> dict[str, str]:
    text = gamedata_path.read_text(encoding="utf-8")
    return {m.group(1): m.group(2) for m in GAMEDATA_TABLE_RE.finditer(text)}


def main() -> int:
    parser = argparse.ArgumentParser(description="Audit resources/bin/*.json coverage and field drift")
    parser.add_argument("--unmapped", action="store_true", help="List resources/bin/*.json files without a *Def.java class")
    parser.add_argument("--drift", action="store_true", help="Show mapped *Def.java files that omit JSON keys present in bin/")
    parser.add_argument("--filter", default="", help="Substring filter on table name")
    args = parser.parse_args()

    root = repo_root()
    bin_dir = root / "resources" / "bin"
    def_dir = root / "src" / "main" / "java" / "emu" / "nebula" / "data" / "resources"
    gamedata_path = root / "src" / "main" / "java" / "emu" / "nebula" / "data" / "GameData.java"

    if not bin_dir.is_dir():
        print(f"Error: {bin_dir} not found", file=sys.stderr)
        return 1

    bin_files = sorted(bin_dir.glob("*.json"))
    def_map = scan_def_classes(def_dir)
    gamedata_tables = scan_gamedata_tables(gamedata_path)
    flt = args.filter.lower()

    mapped_bins = [f for f in bin_files if f.name in def_map and (not flt or flt in f.name.lower())]
    unmapped_bins = [f for f in bin_files if f.name not in def_map and (not flt or flt in f.name.lower())]

    print(f"=== Resource Table Audit ===")
    print(f"Total bin/*.json files : {len(bin_files)}")
    print(f"Mapped *Def.java files : {len(def_map)}")
    print(f"GameData DataTables    : {len(gamedata_tables)}")

    for json_name, java_file in sorted(def_map.items()):
        cls_name = java_file.stem
        expected_table = cls_name[:-3] + "DataTable" if cls_name.endswith("Def") else cls_name + "DataTable"
        actual_table = gamedata_tables.get(cls_name)
        if actual_table is None:
            print(f"  [WARN] {cls_name} ({json_name}) has no DataTable<{cls_name}> in GameData.java (expected {expected_table})")
        elif actual_table.lower() != expected_table.lower():
            print(f"  [WARN] {cls_name} DataTable name mismatch: {actual_table} vs expected {expected_table}")

    if args.unmapped:
        print(f"\n=== Unmapped bin/*.json Tables ({len(unmapped_bins)}) ===")
        for f in unmapped_bins:
            data = load_json(f)
            count = len(data) if isinstance(data, dict) else 0
            print(f"  {f.name:<42} ({count} records)")

    if args.drift:
        print(f"\n=== Schema Drift Check Across {len(mapped_bins)} Mapped Tables ===")
        for f in mapped_bins:
            java_file = def_map[f.name]
            declared = parse_existing_def_fields(java_file)
            declared_lower = {d.lower() for d in declared}
            data = load_json(f)
            if not isinstance(data, dict):
                continue
            fields = collect_table_fields(data)
            missing = [
                f"{k}:{infer_java_type(v)}"
                for k, v in fields.items()
                if k not in declared and k.lower() not in declared_lower
            ]
            if missing:
                preview = ", ".join(missing[:8]) + (f", ... (+{len(missing) - 8} more)" if len(missing) > 8 else "")
                print(f"  {java_file.name:<36} ({len(declared)} declared, {len(missing)} unmapped keys): {preview}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
