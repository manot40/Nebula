#!/usr/bin/env python3
"""Audit NetMsgId.java opcodes against @HandlerId classes and Quickbuf proto files."""

import argparse
import re
from pathlib import Path


OPCODE_RE = re.compile(r"public\s+static\s+final\s+int\s+(\w+)\s*=\s*(-?\d+)\s*;")
HANDLER_ID_RE = re.compile(r"@HandlerId\s*\(\s*NetMsgId\.(\w+)\s*\)")


def repo_root() -> Path:
    return Path(__file__).resolve().parents[4]


def snake_to_pascal(name: str) -> str:
    return "".join(part.capitalize() for part in name.split("_") if part)


def main() -> int:
    parser = argparse.ArgumentParser(description="Audit NetMsgId opcodes vs implemented @HandlerId classes")
    parser.add_argument("--missing", action="store_true", help="Only list unimplemented *_req opcodes")
    parser.add_argument("--implemented", action="store_true", help="Only list implemented *_req opcodes")
    parser.add_argument("--filter", default="", help="Case-insensitive substring filter on opcode name")
    parser.add_argument("--range", dest="id_range", help="Numeric ID range filter, e.g. 4700-4800")
    args = parser.parse_args()

    root = repo_root()
    netmsgid_path = root / "src" / "main" / "java" / "emu" / "nebula" / "net" / "NetMsgId.java"
    handlers_dir = root / "src" / "main" / "java" / "emu" / "nebula" / "server" / "handlers"
    proto_dir = root / "src" / "generated" / "main" / "emu" / "nebula" / "proto"

    opcodes: dict[str, int] = {}
    for m in OPCODE_RE.finditer(netmsgid_path.read_text(encoding="utf-8")):
        opcodes[m.group(1)] = int(m.group(2))

    handlers: dict[str, str] = {}
    for java_file in sorted(handlers_dir.rglob("*.java")):
        text = java_file.read_text(encoding="utf-8")
        for m in HANDLER_ID_RE.finditer(text):
            handlers[m.group(1)] = java_file.name

    proto_files = {p.stem.lower(): p.name for p in proto_dir.glob("*.java")} if proto_dir.is_dir() else {}

    min_id, max_id = None, None
    if args.id_range and "-" in args.id_range:
        lo, hi = args.id_range.split("-", 1)
        min_id, max_id = int(lo), int(hi)

    flt = args.filter.lower()
    reqs = [(name, val) for name, val in opcodes.items() if name.endswith("_req") and val > 0]
    reqs.sort(key=lambda x: x[1])

    impl_count = sum(1 for name, _ in reqs if name in handlers)
    missing_count = len(reqs) - impl_count
    print(f"=== NetMsgId Opcode Audit ===")
    print(f"Total positive *_req opcodes : {len(reqs)}")
    print(f"Implemented (@HandlerId)     : {impl_count}")
    print(f"Unimplemented (dummy ack)    : {missing_count}\n")

    rows = []
    for req_name, req_id in reqs:
        is_impl = req_name in handlers
        if args.missing and is_impl:
            continue
        if args.implemented and not is_impl:
            continue
        if flt and flt not in req_name.lower():
            continue
        if min_id is not None and not (min_id <= req_id <= max_id):
            continue

        base = req_name[:-4]
        succ_id = opcodes.get(f"{base}_succeed_ack")
        fail_id = opcodes.get(f"{base}_failed_ack")
        pascal = snake_to_pascal(base)
        proto_match = proto_files.get(pascal.lower(), "-")
        status = handlers[req_name] if is_impl else "MISSING (dummy failed_ack)"
        rows.append((req_id, req_name, succ_id, fail_id, proto_match, status))

    print(f"{'ID':<6} {'Request Opcode':<46} {'Succ':<6} {'Fail':<6} {'Proto File':<36} {'Handler / Status'}")
    print("-" * 135)
    for req_id, req_name, succ_id, fail_id, proto_match, status in rows:
        s_str = str(succ_id) if succ_id is not None else "-"
        f_str = str(fail_id) if fail_id is not None else "-"
        print(f"{req_id:<6} {req_name:<46} {s_str:<6} {f_str:<6} {proto_match:<36} {status}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
