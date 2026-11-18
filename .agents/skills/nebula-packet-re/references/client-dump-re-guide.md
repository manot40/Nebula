# Client Dump & Packet Reverse-Engineering Guide

This guide covers how decompiled client artifacts (Lua scripts, IL2CPP `dump.cs`, C# assemblies) and live `AgentZoneHandler` packet logs map into Nebula's Java codebase.

---

## 1. Translating Client Network Calls to `@HandlerId`

1. **Identify the Opcode Pair**:
   - Every client request `<name>_req` in `NetMsgId.java` has a paired `<name>_succeed_ack` and `<name>_failed_ack`.
   - Run `audit_opcodes.py --filter <name>` to verify whether a `@HandlerId` class exists or if it is currently handled by `AgentZoneHandler.registerDummyHandlers()`.
2. **Inspect the Request & Ack Schemas**:
   - Run `inspect_proto.py <PascalName>` to see the exact request fields (`*Req`) and response fields (`*Resp` / `*Ack` or `Public.*`).
   - Check what the client expects when the response is empty (`Public.Nil`, `Public.ChangeInfo`, or a dedicated ack message).
3. **Handle Expected Failures Cleanly**:
   - Never return `null` from `NetHandler.handle()` unless you intend to trigger an HTTP `500`.
   - Return `session.encodeMsg(NetMsgId.<name>_failed_ack)` or attach a structured `PlayerErrorCode`:
     ```java
     return session.encodeMsg(
         NetMsgId.<name>_failed_ack,
         Error.newInstance().setCode(PlayerErrorCode.ErrArgs.getValue())
     );
     ```

---

## 2. Side-Effect Notifications (`PlayerChangeInfo` & `nextPackage`)

Because Nebula communicates over stateless HTTP `POST /agent-zone-1/`, unsolicited server pushes are piggybacked inside field `2047` (`optional bytes NextPackage = 2047;`) of the response proto:

- **Direct `ChangeInfo` response**: If the `*_succeed_ack` proto type *is* `Public.ChangeInfo` (or embeds `ChangeInfo`), return `change.toProto()` directly in `session.encodeMsg(...)`.
- **Piggybacked `items_change_notify`**: If the `*_succeed_ack` proto returns feature-specific data (or `Nil`) while mutating items/resources/characters/discs, queue the delta notification before encoding the response:
  ```java
  player.addNextPackage(NetMsgId.items_change_notify, change.toProto());
  ```
- **Quest / Achievement Triggers**:
  - Call `player.trigger(QuestCondition.<Cond>, amount, param1, param2)` and/or `player.trigger(AchievementCondition.<Cond>, amount, param1, param2)`.
  - `QuestManager` and `AchievementManager` automatically queue their own notify packets and set `queueSave = true` (flushed in `Player.afterResponse()`).

---

## 3. Wiring a New `PlayerManager` or Persistence Field

When a reverse-engineered feature requires new persistent player state:
1. **Adding fields to an existing `@Entity` manager**:
   - Prefer fastutil collections (`IntOpenHashSet`, `IntArrayList`, `Int2IntOpenHashMap`, `ItemParamMap`, `Bitset`) supported by Nebula's custom BSON codecs in `DatabaseManager.java`.
   - Always guard and initialize `null` fields inside `onLoad()` so existing player documents in MongoDB migrate without `NullPointerException`.
2. **Creating a new `PlayerManager`**:
   - Annotate with `@Entity(value = "...", useDiscriminator = false)` and provide a `@Deprecated public MyManager() {}` constructor for Morphia.
   - Load in `Player.onLoad()` (`src/main/java/emu/nebula/game/player/Player.java`).
   - Delete in `PlayerModule.deletePlayer(int uid)` (`src/main/java/emu/nebula/game/player/PlayerModule.java`).
   - Populate login state in `Player.toProto()` if the client expects initial bootstrap data in `player_data_succeed_ack`.

---

## 4. Quickbuf Ordering & Live Client Log Checks

- **Quickbuf field tag order**: Quickbuf serializes fields strictly in ascending proto tag order, not setter call order. If one sub-message holds both removal and addition fields (e.g., `SoldierTransform`: `Consume = 1` before `Obtain = 2`), split ordered steps into separate repeated transform entries rather than combining obtain + consume of the same UID in one element.
- **Correlate `AgentZoneHandler` hex logs with Unity `Player.log`**: When a server ACK succeeds (`*_succeed_ack`) yet the client UI freezes or fails to update, check the client's `Player.log` (on Windows: `%USERPROFILE%\AppData\LocalLow\YOSTAR (HONG KONG) LIMITED\StellaSora\Player.log`) for Lua nil-field tracebacks (e.g., extra UI options, missing `Node` change fields, or missing case entries).
