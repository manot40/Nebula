#!/usr/bin/env python3
"""Scaffold a Nebula BaseDef class from resources/bin/<Table>.json or diff against an existing *Def.java."""

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

from inspect_table import infer_java_type, load_json, normalize_table_name, repo_root


FIELD_DECL_RE = re.compile(r"\bprivate\s+(?!static\b)(?!transient\b)(?:final\s+)?([\w<>\[\],\s]+?)\s+(\w+)\s*;")
SERIALIZED_RE = re.compile(r'@SerializedName\(\s*"([^"]+)"\s*\)')


def parse_existing_def_fields(java_path: Path) -> set[str]:
    text = java_path.read_text(encoding="utf-8")
    fields: set[str] = set()
    for m in FIELD_DECL_RE.finditer(text):
        fields.add(m.group(2))
    for m in SERIALIZED_RE.finditer(text):
        fields.add(m.group(1))
    return fields


def collect_table_fields(data: dict[str, Any]) -> dict[str, list[Any]]:
    field_values: dict[str, list[Any]] = {}
    for row in data.values():
        if isinstance(row, dict):
            for k, v in row.items():
                field_values.setdefault(k, []).append(v)
    return field_values


def detect_id_field(field_types: dict[str, str]) -> str:
    if "Id" in field_types:
        return "Id"
    for k, t in field_types.items():
        if k.lower().endswith("id") and t == "int":
            return k
    for k, t in field_types.items():
        if t == "int":
            return k
    return "Id"


def detect_reward_pairs(field_types: dict[str, str]) -> list[tuple[str, str]]:
    pairs: list[tuple[str, str]] = []
    for idx in range(1, 10):
        if f"Tid{idx}" in field_types and f"Qty{idx}" in field_types:
            pairs.append((f"Tid{idx}", f"Qty{idx}"))
        elif f"Reward{idx}" in field_types and f"RewardQty{idx}" in field_types:
            pairs.append((f"Reward{idx}", f"RewardQty{idx}"))
        elif f"Item{idx}" in field_types and f"Qty{idx}" in field_types:
            pairs.append((f"Item{idx}", f"Qty{idx}"))
    if not pairs and "ItemId" in field_types and "ItemQty" in field_types:
        pairs.append(("ItemId", "ItemQty"))
    return pairs


def generate_def_source(table_name: str, field_types: dict[str, str]) -> str:
    class_name = f"{table_name}Def"
    id_field = detect_id_field(field_types)
    reward_pairs = detect_reward_pairs(field_types)
    needs_map = any(t.startswith("Map<") for t in field_types.values())

    imports = [
        "import emu.nebula.data.BaseDef;",
        "import emu.nebula.data.ResourceType;",
    ]
    if reward_pairs:
        imports.append("import emu.nebula.game.inventory.ItemParamMap;")
    if needs_map:
        imports.insert(0, "import java.util.Map;")
    imports.append("import lombok.Getter;")

    lines = [
        "package emu.nebula.data.resources;",
        "",
        *imports,
        "",
        "@Getter",
        f'@ResourceType(name = "{table_name}.json")',
        f"public class {class_name} extends BaseDef {{",
    ]

    for fname, ftype in field_types.items():
        lines.append(f"    private {ftype} {fname};")

    if reward_pairs:
        lines.append("")
        lines.append("    // Cached rewards")
        lines.append("    private transient ItemParamMap rewards;")

    lines.extend([
        "",
        "    @Override",
        "    public int getId() {",
        f"        return this.{id_field};" if id_field in field_types else "        return 0;",
        "    }",
    ])

    if reward_pairs:
        lines.extend([
            "",
            "    @Override",
            "    public void onLoad() {",
            "        this.rewards = new ItemParamMap();",
        ])
        for tid_f, qty_f in reward_pairs:
            lines.append(f"        if (this.{tid_f} > 0 && this.{qty_f} > 0) {{")
            lines.append(f"            this.rewards.add(this.{tid_f}, this.{qty_f});")
            lines.append("        }")
        lines.append("    }")

    lines.append("}")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="Scaffold or diff a Nebula *Def.java class from resources/bin/<Table>.json")
    parser.add_argument("table", help="Table name (e.g. StarTowerBookEventReward)")
    parser.add_argument("--diff", action="store_true", help="Compare resources/bin/<Table>.json against existing <Table>Def.java")
    parser.add_argument("--write", action="store_true", help="Write new <Table>Def.java (refuses to overwrite existing files)")
    args = parser.parse_args()

    root = repo_root()
    table_name = normalize_table_name(args.table)
    bin_path = root / "resources" / "bin" / f"{table_name}.json"
    if not bin_path.is_file():
        print(f"Error: {bin_path} does not exist.", file=sys.stderr)
        return 1

    data = load_json(bin_path)
    if not isinstance(data, dict):
        print(f"Error: Expected JSON object map in {bin_path}", file=sys.stderr)
        return 1

    field_values = collect_table_fields(data)
    field_types = {k: infer_java_type(v) for k, v in field_values.items()}
    java_path = root / "src" / "main" / "java" / "emu" / "nebula" / "data" / "resources" / f"{table_name}Def.java"

    if args.diff:
        if not java_path.is_file():
            print(f"No existing Def file at {java_path}. Run without --diff to scaffold.")
            return 1
        declared = parse_existing_def_fields(java_path)
        declared_lower = {f.lower(): f for f in declared}
        missing: list[tuple[str, str]] = []
        for k, t in field_types.items():
            if k not in declared and k.lower() not in declared_lower:
                missing.append((k, t))
        print(f"=== Diff: {java_path.name} vs resources/bin/{table_name}.json ===")
        print(f"Declared Java fields ({len(declared)}): {sorted(declared)}")
        print(f"Unmapped JSON keys ({len(missing)}):")
        for k, t in missing:
            print(f"    private {t} {k};")
        return 0

    source = generate_def_source(table_name, field_types)
    game_data_line = f"    @Getter private static DataTable<{table_name}Def> {table_name}DataTable = new DataTable<>();"

    if args.write:
        if java_path.exists():
            print(f"Error: Refusing to overwrite existing file {java_path}. Use --diff instead.", file=sys.stderr)
            return 1
        java_path.write_text(source, encoding="utf-8")
        print(f"Created {java_path}")
        print("\nAdd to src/main/java/emu/nebula/data/GameData.java:")
        print(game_data_line)
        return 0

    print(f"// Target file: src/main/java/emu/nebula/data/resources/{table_name}Def.java")
    print(source)
    print("// Add to src/main/java/emu/nebula/data/GameData.java:")
    print(game_data_line)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
