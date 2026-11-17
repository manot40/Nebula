#!/usr/bin/env python3
"""Pure-Python protobuf wire decoder for AgentZoneHandler packet logs and raw hex payloads."""

import argparse
import html
import re
import struct
import sys
from pathlib import Path
from typing import Any

from audit_opcodes import OPCODE_RE, snake_to_pascal
from inspect_proto import CODE_TAG_RE, FIELD_DEF_RE, TYPE_HEADER_RE, find_block_end, repo_root


LOG_HEADER_RE = re.compile(r"\b(RECV|SEND):\s+(\w+)\s+\((-?\d+)\)")
HEX_LINE_RE = re.compile(r"^[0-9a-fA-F\s]+$")


def load_opcodes(root: Path) -> tuple[dict[str, int], dict[int, str]]:
    netmsgid_path = root / "src" / "main" / "java" / "emu" / "nebula" / "net" / "NetMsgId.java"
    name_to_id: dict[str, int] = {}
    id_to_name: dict[int, str] = {}
    if netmsgid_path.is_file():
        for m in OPCODE_RE.finditer(netmsgid_path.read_text(encoding="utf-8")):
            name, val = m.group(1), int(m.group(2))
            name_to_id[name] = val
            id_to_name[val] = name
    return name_to_id, id_to_name


def load_message_schemas(proto_dir: Path, target_files: list[Path]) -> dict[str, dict[int, tuple[str, str, str]]]:
    """Return {MessageName: {tag: (rule, ftype, fname)}}."""
    schemas: dict[str, dict[int, tuple[str, str, str]]] = {}
    for f in target_files:
        if not f.is_file():
            continue
        text = f.read_text(encoding="utf-8")
        for m in TYPE_HEADER_RE.finditer(text):
            if m.group(1) != "type":
                continue
            msg_name = m.group(2)
            brace_idx = text.find("{", m.end())
            if brace_idx == -1:
                continue
            end_idx = find_block_end(text, brace_idx)
            body = text[brace_idx + 1 : end_idx]

            nested_spans: list[tuple[int, int]] = []
            for nm in TYPE_HEADER_RE.finditer(body):
                nb_open = body.find("{", nm.end())
                if nb_open != -1:
                    nested_spans.append((nm.start(), find_block_end(body, nb_open) + 1))

            fields: dict[int, tuple[str, str, str]] = {}
            for cm in CODE_TAG_RE.finditer(body):
                if any(lo <= cm.start() < hi for lo, hi in nested_spans):
                    continue
                raw = html.unescape(cm.group(1)).strip()
                fm = FIELD_DEF_RE.match(raw)
                if fm:
                    rule, ftype, fname, tag_str = fm.groups()
                    tag = int(tag_str)
                    if tag not in fields:
                        fields[tag] = (rule, ftype, fname)
            schemas[msg_name] = fields
    return schemas


def read_varint(buf: bytes, pos: int) -> tuple[int, int]:
    result = 0
    shift = 0
    while pos < len(buf):
        b = buf[pos]
        pos += 1
        result |= (b & 0x7F) << shift
        if not (b & 0x80):
            return result, pos
        shift += 7
        if shift >= 64:
            raise ValueError("Varint too long")
    raise ValueError("Truncated varint")


def zigzag_decode(n: int) -> int:
    return (n >> 1) ^ -(n & 1)


def decode_wire(
    buf: bytes,
    msg_name: str | None,
    schemas: dict[str, dict[int, tuple[str, str, str]]],
    id_to_name: dict[int, str],
    follow_next: bool,
    proto_dir: Path,
    depth: int = 0,
) -> list[dict[str, Any]]:
    pos = 0
    out: list[dict[str, Any]] = []
    msg_schema = schemas.get(msg_name or "", {})

    while pos < len(buf):
        tag_wire, pos = read_varint(buf, pos)
        field_num = tag_wire >> 3
        wire_type = tag_wire & 0x07
        if field_num == 0:
            break

        field_meta = msg_schema.get(field_num)
        rule, ftype, fname = field_meta if field_meta else ("optional", f"wire_{wire_type}", f"field_{field_num}")
        short_type = ftype.split(".")[-1]

        if wire_type == 0:  # Varint
            val, pos = read_varint(buf, pos)
            if short_type in ("sint32", "sint64"):
                val = zigzag_decode(val)
            elif short_type == "bool":
                val = bool(val)
            out.append({"tag": field_num, "name": fname, "type": ftype, "value": val})
        elif wire_type == 1:  # 64-bit
            raw = buf[pos : pos + 8]
            pos += 8
            val = struct.unpack("<d", raw)[0] if short_type == "double" else struct.unpack("<Q", raw)[0]
            out.append({"tag": field_num, "name": fname, "type": ftype, "value": val})
        elif wire_type == 2:  # Length-delimited
            length, pos = read_varint(buf, pos)
            chunk = buf[pos : pos + length]
            pos += length

            if field_num == 2047 and follow_next and len(chunk) >= 2:
                next_id = (chunk[0] << 8) | chunk[1]
                if next_id & 0x8000:
                    next_id -= 0x10000
                next_name = id_to_name.get(next_id, f"unknown_{next_id}")
                next_msg = guess_message_for_opcode(next_name, proto_dir, schemas)
                nested = decode_wire(chunk[2:], next_msg, schemas, id_to_name, follow_next, proto_dir, depth + 1)
                out.append({
                    "tag": 2047,
                    "name": "NextPackage",
                    "type": f"{next_name} ({next_id}) [{next_msg or 'raw'}]",
                    "value": nested,
                })
            elif rule == "repeated" and short_type in ("int32", "uint32", "int64", "uint64", "sint32", "sint64", "enum"):
                p2 = 0
                packed = []
                while p2 < len(chunk):
                    v, p2 = read_varint(chunk, p2)
                    if short_type in ("sint32", "sint64"):
                        v = zigzag_decode(v)
                    packed.append(v)
                out.append({"tag": field_num, "name": fname, "type": f"repeated {ftype}", "value": packed})
            elif short_type == "string":
                out.append({"tag": field_num, "name": fname, "type": ftype, "value": chunk.decode("utf-8", errors="replace")})
            elif short_type in schemas and depth < 6:
                nested = decode_wire(chunk, short_type, schemas, id_to_name, follow_next, proto_dir, depth + 1)
                out.append({"tag": field_num, "name": fname, "type": short_type, "value": nested})
            else:
                try:
                    text_val = chunk.decode("utf-8")
                    if text_val.isprintable():
                        out.append({"tag": field_num, "name": fname, "type": ftype, "value": text_val, "hex": chunk.hex()})
                        continue
                except UnicodeDecodeError:
                    pass
                out.append({"tag": field_num, "name": fname, "type": ftype, "hex": chunk.hex(), "len": len(chunk)})
        elif wire_type == 5:  # 32-bit
            raw = buf[pos : pos + 4]
            pos += 4
            val = struct.unpack("<f", raw)[0] if short_type == "float" else struct.unpack("<I", raw)[0]
            out.append({"tag": field_num, "name": fname, "type": ftype, "value": val})
        else:
            raise ValueError(f"Unsupported wire type {wire_type} at tag {field_num}")

    return out


def guess_message_for_opcode(
    opcode_name: str,
    proto_dir: Path,
    schemas: dict[str, dict[int, tuple[str, str, str]]],
) -> str | None:
    base = opcode_name
    for suffix in ("_req", "_succeed_ack", "_failed_ack", "_notify"):
        if base.endswith(suffix):
            base = base[: -len(suffix)]
            break
    pascal = snake_to_pascal(base)
    candidate_file = proto_dir / f"{pascal}.java"
    if candidate_file.is_file():
        schemas.update(load_message_schemas(proto_dir, [candidate_file]))

    if opcode_name.endswith("_req"):
        for cand in (f"{pascal}Req", pascal):
            if cand in schemas:
                return cand
    elif opcode_name.endswith("_succeed_ack"):
        for cand in (f"{pascal}Resp", f"{pascal}Ack", pascal, "ChangeInfo", "Nil"):
            if cand in schemas:
                return cand
    elif opcode_name.endswith("_failed_ack"):
        if "Error" in schemas:
            return "Error"
    elif opcode_name.endswith("_notify"):
        for cand in (f"{pascal}Notify", pascal, "ChangeInfo"):
            if cand in schemas:
                return cand
    return None


def format_decoded(fields: list[dict[str, Any]], indent: int = 2) -> str:
    pad = " " * indent
    lines: list[str] = []
    for f in fields:
        val = f.get("value")
        if isinstance(val, list) and val and isinstance(val[0], dict):
            lines.append(f"{pad}[{f['tag']}] {f['name']} ({f['type']}):")
            lines.append(format_decoded(val, indent + 4))
        elif "hex" in f and "value" not in f:
            lines.append(f"{pad}[{f['tag']}] {f['name']} ({f['type']}) = <{f['len']} bytes: {f['hex']}>")
        else:
            lines.append(f"{pad}[{f['tag']}] {f['name']} ({f['type']}) = {val!r}")
    return "\n".join(lines) if lines else f"{pad}<empty payload>"


def main() -> int:
    parser = argparse.ArgumentParser(description="Decode AgentZoneHandler packet logs or raw hex strings")
    parser.add_argument("input", help="Raw hex string or path to log file containing RECV:/SEND: lines")
    parser.add_argument("--msg", help="NetMsgId opcode name or numeric ID")
    parser.add_argument("--proto", help="Explicit Quickbuf message name (e.g. AchievementRewardReq)")
    parser.add_argument("--follow-next", action="store_true", help="Recursively decode field 2047 (NextPackage)")
    args = parser.parse_args()

    root = repo_root()
    proto_dir = root / "src" / "generated" / "main" / "emu" / "nebula" / "proto"
    name_to_id, id_to_name = load_opcodes(root)

    # Preload Public.java and specified proto files
    preload_files = [proto_dir / "Public.java"]
    if args.proto:
        for p in proto_dir.glob("*.java"):
            if args.proto.lower() in p.stem.lower():
                preload_files.append(p)
    schemas = load_message_schemas(proto_dir, preload_files)

    if args.proto and args.proto not in schemas:
        for p in proto_dir.glob("*.java"):
            if args.proto in p.read_text(encoding="utf-8"):
                schemas.update(load_message_schemas(proto_dir, [p]))
                break

    input_path = Path(args.input)
    if input_path.is_file():
        lines = input_path.read_text(encoding="utf-8").splitlines()
        cur_header = None
        for line in lines:
            hm = LOG_HEADER_RE.search(line)
            if hm:
                cur_header = (hm.group(1), hm.group(2), int(hm.group(3)))
                continue
            stripped = line.strip()
            if cur_header and stripped and HEX_LINE_RE.match(stripped):
                direction, op_name, op_id = cur_header
                msg_name = args.proto or guess_message_for_opcode(op_name, proto_dir, schemas)
                raw_bytes = bytes.fromhex(re.sub(r"\s+", "", stripped))
                print(f"=== {direction}: {op_name} ({op_id}) -> {msg_name or 'raw wire'} ({len(raw_bytes)} bytes) ===")
                decoded = decode_wire(raw_bytes, msg_name, schemas, id_to_name, args.follow_next, proto_dir)
                print(format_decoded(decoded))
                cur_header = None
        return 0

    clean_hex = re.sub(r"\s+", "", args.input)
    raw_bytes = bytes.fromhex(clean_hex)
    op_name = None
    if args.msg:
        if args.msg.lstrip("-").isdigit():
            op_name = id_to_name.get(int(args.msg))
        else:
            op_name = args.msg
    msg_name = args.proto or (guess_message_for_opcode(op_name, proto_dir, schemas) if op_name else None)
    print(f"=== Decoded Packet ({len(raw_bytes)} bytes, schema={msg_name or 'raw wire'}) ===")
    decoded = decode_wire(raw_bytes, msg_name, schemas, id_to_name, args.follow_next, proto_dir)
    print(format_decoded(decoded))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
