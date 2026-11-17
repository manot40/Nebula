---
name: nebula-version-bump
description: >-
  Use this skill when updating Nebula for a new game client version or patch, updating GameConstants.VERSION and src/main/resources/versions.json, regenerating Quickbuf protos from ./proto/, checking new activities in ActivityModule.java, or auditing patch deltas across resources/bin/, NetMsgId.java, and custom defs.
---

# Nebula Game Version Bump Workflow

When the game client updates or `./resources/` is refreshed from `StellaSoraData`, follow this workflow to synchronize version metadata, protos, opcodes, activities, condition enums, and custom server parameters.

---

## 1. Run the Consolidated Patch Audit (`version_audit.py`)

Run [version_audit.py](./scripts/version_audit.py) to get a single report covering version metadata, new `Activity.json` entries not yet listed in `ActivityModule.java`, unknown `QuestCondition`/`AchievementCondition` values, missing `AchievementParam`/`DormQuestParam`/`StarTowerQuestParam`/`PlayerErrorCode` entries, and unimplemented `NetMsgId` opcodes:

```bash
python .agents/skills/nebula-version-bump/scripts/version_audit.py --lang en_US
```

---

## 2. Step-by-Step Version Update Runbook

1. **Protos & `NetMsgId` (when client `.proto` schemas changed)**:
   - Place updated `.proto` files in `./proto/` (gitignored).
   - Run:
     ```bash
     GENERATE_PROTO=true bash ./gradlew updateProto
     ```
     > **WARNING**: Never run `./gradlew clean` with `GENERATE_PROTO=true` unless `./proto/` is populated, because `clean` deletes committed files in `src/generated/main/emu/nebula/proto/`.
   - Update `src/main/java/emu/nebula/net/NetMsgId.java` with new/changed opcodes.

2. **Version Metadata**:
   - Update `GameConstants.VERSION` in `src/main/java/emu/nebula/GameConstants.java` if the base client version (`major.minor.patch`) changed.
   - Update per-region data versions (`global`, `kr`, `jp`, `tw`, `cn`) in `src/main/resources/versions.json`.

3. **Condition Enums & Error Codes**:
   - Add any new condition IDs flagged by `version_audit.py` to `src/main/java/emu/nebula/game/achievement/AchievementCondition.java` and `src/main/java/emu/nebula/game/quest/QuestCondition.java`.
   - Sync new error codes into `src/main/java/emu/nebula/game/player/PlayerErrorCode.java` using:
     ```bash
     python .agents/skills/nebula-custom-params/scripts/audit_custom_params.py --target errors --emit-stubs
     ```

4. **Custom Params & Activities**:
   - Check new achievements/quests flagged by `version_audit.py` and update `src/main/resources/defs/AchievementParam.json` (or `DormQuestParam.json` / `StarTowerQuestParam.json`).
   - Enable new event activity IDs in `src/main/java/emu/nebula/game/activity/ActivityModule.java` and verify `ActivityManager.java` maps any new `ActivityType` enum values.

5. **Build Verification**:
   - Always verify the full build (including `injectGitHash` -> `BuildConfig.java` and Quickbuf references) with:
     ```bash
     bash ./gradlew jar
     ```
