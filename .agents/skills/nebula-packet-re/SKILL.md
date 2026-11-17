---
name: nebula-packet-re
description: >-
  Use this skill when reverse-engineering network packets, implementing missing @HandlerId packet handlers, auditing NetMsgId.java opcodes, inspecting Quickbuf protobuf schemas in src/generated/main/emu/nebula/proto/, decoding AgentZoneHandler hex packet logs, or searching decompiled client Lua/IL2CPP/C# dumps.
---

# Nebula Packet & Protobuf Reverse Engineering

Nebula handles encrypted client RPCs on `POST /agent-zone-1/` via `AgentZoneHandler.java`, dispatching 2-byte big-endian `NetMsgId` opcodes to `@HandlerId` classes in `src/main/java/emu/nebula/server/handlers/` using Quickbuf protos in `src/generated/main/emu/nebula/proto/`.

> **Token Efficiency**: Quickbuf generated `.java` files in `src/generated/main/emu/nebula/proto/` are hundreds to 100,000+ lines long (`Public.java` is 102k lines). **Never** read generated Quickbuf `.java` files raw when you only need message/enum definitions—use `inspect_proto.py` instead.

---

## 1. Audit Implemented vs. Missing Opcodes (`audit_opcodes.py`)

Run [audit_opcodes.py](./scripts/audit_opcodes.py) to list unimplemented `*_req` opcodes (currently bound to dummy `*_failed_ack` handlers by `AgentZoneHandler.registerDummyHandlers()`) along with their `*_succeed_ack`, `*_failed_ack`, and matched Quickbuf proto files:

```bash
# Summary + all unimplemented *_req opcodes
python .agents/skills/nebula-packet-re/scripts/audit_opcodes.py --missing

# Filter opcodes (both implemented and missing) by keyword or ID range
python .agents/skills/nebula-packet-re/scripts/audit_opcodes.py --filter star_tower_build
python .agents/skills/nebula-packet-re/scripts/audit_opcodes.py --range 4700-4800
```

---

## 2. Extract Clean `.proto` Schemas from Quickbuf Java (`inspect_proto.py`)

Run [inspect_proto.py](./scripts/inspect_proto.py) to extract concise `.proto`-style message and enum definitions from `src/generated/main/emu/nebula/proto/*.java`:

```bash
# Inspect all messages/enums in a specific Quickbuf file or matching a message name
python .agents/skills/nebula-packet-re/scripts/inspect_proto.py StarTowerBuildGenerator
python .agents/skills/nebula-packet-re/scripts/inspect_proto.py AchievementRewardReq

# Search across Public.java for a specific message or enum without loading 100k lines
python .agents/skills/nebula-packet-re/scripts/inspect_proto.py Public --message UInt32
```

---

## 3. Decode `AgentZoneHandler` Packet Logs & Raw Hex (`decode_packet.py`)

Run [decode_packet.py](./scripts/decode_packet.py) to decode raw hex strings or `AgentZoneHandler` (`logOptions.packets = true`) `RECV:` / `SEND:` log blocks into protobuf wire fields, mapped against Quickbuf field names and recursively unpacking piggybacked `NextPackage` (field `2047`) notifications:

```bash
# Decode a raw hex payload against a known Quickbuf message or NetMsgId
python .agents/skills/nebula-packet-re/scripts/decode_packet.py "0a029705" --proto AchievementRewardReq --follow-next

# Decode an entire log snippet file containing RECV:/SEND: headers + hex lines
python .agents/skills/nebula-packet-re/scripts/decode_packet.py path/to/packet_log.txt --follow-next
```

---

## 4. Search Decompiled Client Dumps (`search_client_dump.py`)

When reverse-engineering client Lua scripts, IL2CPP `dump.cs`, or decompiled C# assemblies, run [search_client_dump.py](./scripts/search_client_dump.py) (pass `--client-dir <path>` or set `NEBULA_CLIENT_DUMP_DIR`):

```bash
python .agents/skills/nebula-packet-re/scripts/search_client_dump.py star_tower_build_generator --mode net --client-dir "D:/ClientDump"
```

See [references/client-dump-re-guide.md](./references/client-dump-re-guide.md) for the complete mapping guide from client network calls, error dialogs, and state flags to Nebula `NetHandler`, `PlayerChangeInfo`, `nextPackage`, and `PlayerManager` code.
