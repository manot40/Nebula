# Integrating Reverse-Engineered Client Findings

Nebula is a game server reimplementation. This guide explains how reverse-engineered client artifacts (network opcodes, protobuf schemas, client `bin/*.json` tables, and client logic) map into Nebula's Java codebase.

> [!NOTE]
> Repeatable data-extraction, schema-auditing, and reverse-engineering workflows are automated via workspace Agent Skills under `.agents/skills/`:
> - [`nebula-data-extraction`](../.agents/skills/nebula-data-extraction/SKILL.md) — Query `resources/bin/` & `resources/language/`, scaffold `*Def.java` + `GameData`, and audit schema drift.
> - [`nebula-custom-params`](../.agents/skills/nebula-custom-params/SKILL.md) — Audit and generate stubs for `src/main/resources/defs/*.json` (`AchievementParam`, `DormQuestParam`, `StarTowerQuestParam`) and `PlayerErrorCode`.
> - [`nebula-packet-re`](../.agents/skills/nebula-packet-re/SKILL.md) — Audit unimplemented `NetMsgId` opcodes, extract Quickbuf `.proto` schemas, decode `AgentZoneHandler` hex packet logs, and search client dumps.
> - [`nebula-version-bump`](../.agents/skills/nebula-version-bump/SKILL.md) — Consolidated patch audit across `GameConstants`, `versions.json`, `ActivityModule`, condition enums, and custom params.

---

## 1. Translating Network Packets into Handlers

### Discovering Opcodes & Missing Handlers
1. Enable `"packets": true` under `logOptions` in `./data/config.json` to log every incoming and outgoing `NetMsgId` name, ID, and hex payload (`AgentZoneHandler.logRecv()` / `logSend()`).
2. Unimplemented `*_req` opcodes in `NetMsgId.java` are automatically bound to `*_failed_ack` by `AgentZoneHandler.registerDummyHandlers()`. Watching client requests trigger dummy `*_failed_ack` or `"Unhandled request: <id>"` identifies what to implement next.
3. Inspect the matching request/response proto classes in `src/generated/main/emu/nebula/proto/` to see every field the client sends and expects back.

### Matching Client Semantics
- **Success vs. Failure Acks**: Most RPCs follow `<name>_req` -> `<name>_succeed_ack` / `<name>_failed_ack`. If a handler returns `null`, `AgentZoneHandler` sends HTTP `500`; prefer returning `*_failed_ack` (with `Public.Error` and `PlayerErrorCode` when the client displays an error dialog).
- **Side-effect notifications**: When a client action updates inventory, quests, mail, or character state without returning them directly in the ack proto, queue `*_notify` packets via `player.addNextPackage(NetMsgId.<notify>, proto)`.
- **Red-dot / state flags**: Login bootstrap (`Player.toProto()`) populates `PlayerInfo.State` containers; some client UI flows expect specific submessages (`getMutable*()`) to exist even when empty (see `Player.java`).

---

## 2. Mapping Client Tables (`bin/*.json`) to `GameData`

Client game tables (from datamined `bin/` JSONs) map 1:1 to `BaseDef` subclasses in `src/main/java/emu/nebula/data/resources/`:

1. Inspect the raw JSON object keys in `./resources/bin/<TableName>.json`.
2. Create `<TableName>Def extends BaseDef` with `@ResourceType(name = "<TableName>.json")` and fields matching the JSON property names.
3. Add `@Getter private static DataTable<<TableName>Def> <TableName>DataTable = new DataTable<>();` to `src/main/java/emu/nebula/data/GameData.java`.
4. Pre-compute derived structures (such as `ItemParamMap` rewards or lookup indices) inside `onLoad()`.
5. If another `Def.onLoad()` depends on this table, set `loadPriority = LoadPriority.HIGH` (or `HIGHEST`) on `@ResourceType`.

---

## 3. Handling Client/Server Gaps (`data/custom/` + `defs/`)

Client `bin/*.json` tables frequently omit server-side validation or progression parameters (e.g., quest trigger conditions, achievement params, gem attribute group weights). In Nebula, fill those gaps with internal custom defs rather than hardcoding magic IDs in Java logic:

1. Create a JSON array file in `src/main/resources/defs/<Name>.json` (see `DormQuestParam.json`, `AchievementParam.json`, `StarTowerQuestParam.json`, `CharGemAttrGroups.json`).
2. Create a `BaseDef` in `src/main/java/emu/nebula/data/custom/<Name>Def.java` annotated with:
   ```java
   @ResourceType(name = "<Name>.json", useInternal = true, loadPriority = LoadPriority.LOW)
   ```
3. In `onLoad()`, attach the parsed params onto the primary `GameData` entry (as `DormQuestParamDef` calls `quest.setParams(this)` on `DormQuestDataTable`).

---

## 4. Extending Enums & Error Codes

When reverse-engineering new quests, achievements, or failure responses:
- **Quest conditions**: `src/main/java/emu/nebula/game/quest/QuestCondition.java`
- **Achievement conditions**: `src/main/java/emu/nebula/game/achievement/AchievementCondition.java`
- **Quest types**: `src/main/java/emu/nebula/game/quest/QuestType.java`
- **Error codes**: `src/main/java/emu/nebula/game/player/PlayerErrorCode.java` (corresponds to `bin/ErrorCode.json`) and `src/main/java/emu/nebula/server/error/ErrorCode.java`

---

## 5. Game Version Bump Checklist

When updating Nebula to support a newer client patch (compare recent commits like `0d6bbc2` for reference):

1. **Protos & Opcodes**:
   - Place updated `.proto` files in `./proto/` (gitignored) and run `GENERATE_PROTO=true ./gradlew updateProto` to update `src/generated/main/emu/nebula/proto/`.
   - Update `src/main/java/emu/nebula/net/NetMsgId.java` with new/changed opcodes.
2. **Version Metadata**:
   - Update `GameConstants.VERSION` (`src/main/java/emu/nebula/GameConstants.java`) if major/minor/patch changed.
   - Update region data versions in `src/main/resources/versions.json` (`global`, `kr`, `jp`, `tw`, `cn`).
3. **Compatibility Fixes**:
   - Fix any broken proto references in `src/main/java/` caused by renamed/restructured fields in `src/generated/main/`.
   - Add any new condition enum values (`QuestCondition`, `AchievementCondition`) or resource fields (`*Def.java`).
4. **Build Verification**:
   - Run `./gradlew jar` to confirm compilation and packaging succeed cleanly.
