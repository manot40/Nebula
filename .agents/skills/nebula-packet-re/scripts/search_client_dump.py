#!/usr/bin/env python3
"""Search decompiled client dumps (Lua scripts, IL2CPP dump.cs, C# files) for NetMsgId, protos, and conditions."""

import argparse
import os
import re
import sys
from pathlib import Path


SUPPORTED_EXTS = {".lua", ".cs", ".txt", ".json", ".proto", ".java"}

MODE_HINTS = {
    "net": re.compile(r"(NetMsgId|SendMsg|HttpNet|MsgId|_req\b|_ack\b|_notify\b)", re.IGNORECASE),
    "cond": re.compile(r"(CompleteCond|AchievementCondition|QuestCondition|Condition)", re.IGNORECASE),
    "error": re.compile(r"(ErrorCode|Err[A-Z]\w+)", re.IGNORECASE),
    "proto": re.compile(r"(Protobuf|Proto|Parser|Descriptor)", re.IGNORECASE),
}


def resolve_client_dir(cli_arg: str | None) -> Path | None:
    raw = cli_arg or os.environ.get("NEBULA_CLIENT_DUMP_DIR")
    if not raw:
        return None
    p = Path(raw).expanduser().resolve()
    return p if p.is_dir() else None


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(
        description="Search decompiled client Lua / IL2CPP dump.cs / C# files for Nebula RE targets"
    )
    parser.add_argument("query", help="Regex or substring to search for (e.g. star_tower_build_generator or 4761)")
    parser.add_argument(
        "--client-dir",
        help="Path to decompiled client dump root (defaults to $NEBULA_CLIENT_DUMP_DIR)",
    )
    parser.add_argument(
        "--mode",
        choices=["all", "net", "cond", "error", "proto"],
        default="all",
        help="Optional context filter mode (default: all)",
    )
    parser.add_argument("--context", type=int, default=2, help="Lines of context around each match (default: 2)")
    parser.add_argument("--limit", type=int, default=30, help="Maximum matches to print (default: 30)")
    args = parser.parse_args()

    client_dir = resolve_client_dir(args.client_dir)
    if client_dir is None:
        print(
            "Error: Client dump directory not configured or not found.\n"
            "Pass --client-dir <path> or set the NEBULA_CLIENT_DUMP_DIR environment variable.",
            file=sys.stderr,
        )
        return 1

    pattern = re.compile(args.query, re.IGNORECASE)
    mode_re = MODE_HINTS.get(args.mode)
    matches_found = 0

    print(f"=== Searching Client Dump: {client_dir} (query={args.query!r}, mode={args.mode}) ===")
    for file_path in sorted(client_dir.rglob("*")):
        if not file_path.is_file() or file_path.suffix.lower() not in SUPPORTED_EXTS:
            continue
        try:
            text = file_path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue

        if not pattern.search(text):
            continue

        lines = text.splitlines()
        rel = file_path.relative_to(client_dir)
        for idx, line in enumerate(lines):
            if not pattern.search(line):
                continue
            lo = max(0, idx - args.context)
            hi = min(len(lines), idx + args.context + 1)
            window = "\n".join(lines[lo:hi])
            if mode_re and not mode_re.search(window):
                continue

            matches_found += 1
            print(f"\n[{matches_found}] {rel}:{idx + 1}")
            for w_idx in range(lo, hi):
                marker = ">" if w_idx == idx else " "
                print(f"  {marker} {w_idx + 1:>6}: {lines[w_idx]}")

            if matches_found >= args.limit:
                print(f"\nReached limit of {args.limit} matches.")
                return 0

    print(f"\nTotal matches: {matches_found}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
