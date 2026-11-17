#!/usr/bin/env python3
"""Inspect schema and query records from resources/bin/<Table>.json with localization resolution."""

import argparse
import json
import sys
from pathlib import Path
from typing import Any


def repo_root() -> Path:
    return Path(__file__).resolve().parents[4]


def normalize_table_name(name: str) -> str:
    if name.endswith(".json"):
        name = name[:-5]
    if name.endswith("Def"):
        name = name[:-3]
    return name


def load_json(path: Path) -> Any:
    if not path.is_file():
        return None
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def infer_scalar_java_type(values: list[Any]) -> str:
    has_float = False
    has_long = False
    for v in values:
        if isinstance(v, bool):
            return "boolean"
        if isinstance(v, int):
            if v < -2147483648 or v > 2147483647:
                has_long = True
        elif isinstance(v, float):
            has_float = True
        elif isinstance(v, str):
            return "String"
        elif isinstance(v, dict):
            return "Map<String, Object>"
    if has_float:
        return "double"
    if has_long:
        return "long"
    return "int"


def infer_java_type(values: list[Any]) -> str:
    non_null = [v for v in values if v is not None]
    if not non_null:
        return "Object"
    first = non_null[0]
    if isinstance(first, list):
        elem_values = [elem for lst in non_null if isinstance(lst, list) for elem in lst if elem is not None]
        if not elem_values:
            return "int[]"
        elem_type = infer_scalar_java_type(elem_values)
        return f"{elem_type}[]"
    return infer_scalar_java_type(non_null)


def resolve_lang_value(val: Any, lang_map: dict[str, str]) -> str | None:
    if isinstance(val, str) and val in lang_map:
        return lang_map[val]
    return None


def print_schema(table_name: str, data: dict[str, dict[str, Any]], lang_map: dict[str, str]) -> None:
    total = len(data)
    print(f"=== Table: {table_name}.json ({total} records, lang keys: {len(lang_map)}) ===")
    field_values: dict[str, list[Any]] = {}
    for row in data.values():
        if isinstance(row, dict):
            for k, v in row.items():
                field_values.setdefault(k, []).append(v)

    print(f"{'Field':<28} {'Java Type':<18} {'Presence':<14} {'Range / Sample'}")
    print("-" * 95)
    for field, vals in field_values.items():
        jtype = infer_java_type(vals)
        count = len(vals)
        presence = f"{count}/{total}" + (" (opt)" if count < total else "")
        sample_info = ""
        if jtype in ("int", "long", "double") and vals:
            nums = [v for v in vals if isinstance(v, (int, float)) and not isinstance(v, bool)]
            if nums:
                distinct = sorted(set(nums))
                if len(distinct) <= 8:
                    sample_info = f"values={distinct}"
                else:
                    sample_info = f"min={min(nums)}, max={max(nums)}"
        else:
            sample = vals[0]
            resolved = resolve_lang_value(sample, lang_map)
            if resolved is not None:
                sample_info = f"e.g. {sample!r} -> {resolved!r}"
            else:
                sample_info = f"e.g. {sample!r}"
        if len(sample_info) > 48:
            sample_info = sample_info[:45] + "..."
        print(f"{field:<28} {jtype:<18} {presence:<14} {sample_info}")


def matches_where(row: dict[str, Any], filters: list[str]) -> bool:
    for expr in filters:
        if "=" not in expr:
            continue
        k, expected = expr.split("=", 1)
        val = row.get(k)
        if val is None:
            return False
        if isinstance(val, list):
            if not any(str(item) == expected for item in val):
                return False
        elif str(val) != expected:
            return False
    return True


def matches_search(key: str, row: dict[str, Any], lang_map: dict[str, str], query: str) -> bool:
    q = query.lower()
    if q in key.lower():
        return True
    for v in row.values():
        if isinstance(v, str):
            if q in v.lower():
                return True
            resolved = lang_map.get(v)
            if resolved and q in resolved.lower():
                return True
        elif q in str(v).lower():
            return True
    return False


def enrich_row(row: dict[str, Any], lang_map: dict[str, str]) -> dict[str, Any]:
    if not lang_map:
        return row
    out: dict[str, Any] = {}
    for k, v in row.items():
        out[k] = v
        resolved = resolve_lang_value(v, lang_map)
        if resolved is not None:
            out[f"{k}_Text"] = resolved
    return out


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description="Inspect and query resources/bin/<Table>.json")
    parser.add_argument("table", help="Table name (e.g. Achievement or Achievement.json)")
    parser.add_argument("--lang", default="en_US", help="Language folder under resources/language/ (default: en_US)")
    parser.add_argument("--id", help="Comma-separated record IDs to look up")
    parser.add_argument("--where", action="append", default=[], help="Filter key=value (repeatable)")
    parser.add_argument("--search", help="Case-insensitive substring search across values and localized text")
    parser.add_argument("--limit", type=int, default=15, help="Max records to display (default: 15)")
    parser.add_argument("--schema-only", action="store_true", help="Only print the inferred schema summary")
    args = parser.parse_args()

    root = repo_root()
    table_name = normalize_table_name(args.table)
    bin_path = root / "resources" / "bin" / f"{table_name}.json"
    if not bin_path.is_file():
        print(f"Error: Table file not found: {bin_path}", file=sys.stderr)
        return 1

    data = load_json(bin_path)
    if not isinstance(data, dict):
        print(f"Error: Expected JSON object map in {bin_path}", file=sys.stderr)
        return 1

    lang_path = root / "resources" / "language" / args.lang / f"{table_name}.json"
    lang_map = load_json(lang_path) or {}
    if not isinstance(lang_map, dict):
        lang_map = {}

    has_query = bool(args.id or args.where or args.search)
    if args.schema_only or not has_query:
        print_schema(table_name, data, lang_map)
        if args.schema_only:
            return 0
        print()

    selected: list[tuple[str, dict[str, Any]]] = []
    if args.id:
        for raw_id in args.id.split(","):
            rid = raw_id.strip()
            if rid in data and isinstance(data[rid], dict):
                selected.append((rid, data[rid]))
            else:
                for k, v in data.items():
                    if isinstance(v, dict) and str(v.get("Id")) == rid:
                        selected.append((k, v))
                        break
    else:
        for k, row in data.items():
            if not isinstance(row, dict):
                continue
            if args.where and not matches_where(row, args.where):
                continue
            if args.search and not matches_search(k, row, lang_map, args.search):
                continue
            selected.append((k, row))

    print(f"=== Matched {len(selected)} record(s) (showing up to {args.limit}) ===")
    for k, row in selected[: args.limit]:
        enriched = enrich_row(row, lang_map)
        print(f"[{k}] {json.dumps(enriched, ensure_ascii=False)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
