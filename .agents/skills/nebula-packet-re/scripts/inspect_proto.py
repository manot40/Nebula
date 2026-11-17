#!/usr/bin/env python3
"""Extract concise .proto schemas from generated Quickbuf Java files in src/generated/main/emu/nebula/proto/."""

import argparse
import html
import re
import sys
from pathlib import Path


TYPE_HEADER_RE = re.compile(
    r"/\*\*\s*\n\s*\*\s*Protobuf\s+(type|enum)\s+\{@code\s+(\w+)\}\s*\n\s*\*/\s*\n\s*public\s+(?:static\s+final\s+class|enum)\s+(\w+)",
    re.MULTILINE,
)
CODE_TAG_RE = re.compile(r"<code>([^<]+)</code>")
FIELD_DEF_RE = re.compile(r"^(optional|repeated|required)\s+([\w.]+)\s+(\w+)\s*=\s*(\d+);$")
ENUM_DEF_RE = re.compile(r"^(\w+)\s*=\s*(-?\d+);$")


def repo_root() -> Path:
    return Path(__file__).resolve().parents[4]


def find_block_end(text: str, open_brace_pos: int) -> int:
    depth = 0
    for i in range(open_brace_pos, len(text)):
        ch = text[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return i
    return len(text)


def extract_definitions(java_path: Path, message_filter: str = "") -> list[tuple[str, str, str]]:
    text = java_path.read_text(encoding="utf-8")
    results: list[tuple[str, str, str]] = []
    flt = message_filter.lower()

    for m in TYPE_HEADER_RE.finditer(text):
        kind = m.group(1)  # 'type' or 'enum'
        name = m.group(2)
        if flt and flt not in name.lower():
            continue

        brace_idx = text.find("{", m.end())
        if brace_idx == -1:
            continue
        end_idx = find_block_end(text, brace_idx)
        body = text[brace_idx + 1 : end_idx]

        # Strip nested message/enum blocks so outer message only collects its own fields
        nested_spans: list[tuple[int, int]] = []
        for nm in TYPE_HEADER_RE.finditer(body):
            nb_open = body.find("{", nm.end())
            if nb_open != -1:
                nb_close = find_block_end(body, nb_open)
                nested_spans.append((nm.start(), nb_close + 1))

        def in_nested(pos: int) -> bool:
            return any(lo <= pos < hi for lo, hi in nested_spans)

        if kind == "type":
            seen_tags: set[int] = set()
            fields: list[tuple[int, str, str, str]] = []
            for cm in CODE_TAG_RE.finditer(body):
                if in_nested(cm.start()):
                    continue
                raw = html.unescape(cm.group(1)).strip()
                fm = FIELD_DEF_RE.match(raw)
                if fm:
                    rule, ftype, fname, tag_str = fm.groups()
                    tag = int(tag_str)
                    if tag not in seen_tags:
                        seen_tags.add(tag)
                        fields.append((tag, rule, ftype, fname))
            fields.sort(key=lambda x: (x[0] == 2047, x[0]))
            lines = [f"message {name} {{"]
            for tag, rule, ftype, fname in fields:
                lines.append(f"  {rule} {ftype} {fname} = {tag};")
            lines.append("}")
            results.append(("message", name, "\n".join(lines)))
        else:
            seen_vals: set[tuple[str, int]] = set()
            vals: list[tuple[int, str]] = []
            for cm in CODE_TAG_RE.finditer(body):
                if in_nested(cm.start()):
                    continue
                raw = html.unescape(cm.group(1)).strip()
                em = ENUM_DEF_RE.match(raw)
                if em:
                    ename, eval_str = em.groups()
                    ev = int(eval_str)
                    if (ename, ev) not in seen_vals:
                        seen_vals.add((ename, ev))
                        vals.append((ev, ename))
            lines = [f"enum {name} {{"]
            for ev, ename in vals:
                lines.append(f"  {ename} = {ev};")
            lines.append("}")
            results.append(("enum", name, "\n".join(lines)))

    return results


def main() -> int:
    parser = argparse.ArgumentParser(description="Inspect Quickbuf generated proto schemas")
    parser.add_argument("query", nargs="?", default="", help="Proto file name or message/enum name")
    parser.add_argument("--message", default="", help="Filter definitions inside the matched file by message/enum name")
    parser.add_argument("--list", action="store_true", help="List available Quickbuf proto files")
    args = parser.parse_args()

    proto_dir = repo_root() / "src" / "generated" / "main" / "emu" / "nebula" / "proto"
    if not proto_dir.is_dir():
        print(f"Error: Proto directory not found: {proto_dir}", file=sys.stderr)
        return 1

    files = sorted(proto_dir.glob("*.java"))
    if args.list or not args.query:
        q = args.query.lower()
        matched = [f.stem for f in files if not q or q in f.stem.lower()]
        print(f"=== Quickbuf Proto Files ({len(matched)}/{len(files)}) ===")
        for stem in matched:
            print(f"  {stem}")
        return 0

    q = args.query[:-5] if args.query.endswith(".java") else args.query
    exact_file = proto_dir / f"{q}.java"
    if exact_file.is_file():
        defs = extract_definitions(exact_file, args.message)
        print(f"// File: {exact_file.relative_to(repo_root())}")
        for _, _, block in defs:
            print(block)
            print()
        return 0

    # Otherwise search by file stem substring first, then message name across files
    stem_matches = [f for f in files if q.lower() in f.stem.lower()]
    if stem_matches:
        for f in stem_matches:
            defs = extract_definitions(f, args.message)
            if defs:
                print(f"// File: {f.relative_to(repo_root())}")
                for _, _, block in defs:
                    print(block)
                    print()
        return 0

    found = False
    for f in files:
        text = f.read_text(encoding="utf-8")
        if q.lower() not in text.lower():
            continue
        defs = extract_definitions(f, q)
        if defs:
            found = True
            print(f"// File: {f.relative_to(repo_root())}")
            for _, _, block in defs:
                print(block)
                print()

    if not found:
        print(f"No Quickbuf file or message matching '{args.query}' found.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
