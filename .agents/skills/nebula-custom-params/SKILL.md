---
name: nebula-custom-params
description: >-
  Use this skill when auditing, extracting, or updating server-only parameter definitions in src/main/resources/defs/ (AchievementParam.json, DormQuestParam.json, StarTowerQuestParam.json, CharGemAttrGroups.json) or syncing client enums (PlayerErrorCode.java, QuestCondition.java, AchievementCondition.java) from resources/bin/ and resources/language/.
---

# Nebula Custom Server Params & Enum Synchronization

Client tables in `./resources/bin/*.json` frequently omit server-evaluated completion conditions or target IDs (such as `Param1` on story/boss achievements, or `CompleteCond`/`Aim` on Dorm and Star Tower quests). Nebula fills these gaps via internal JSON arrays in `src/main/resources/defs/*.json` mapped by `src/main/java/emu/nebula/data/custom/*Def.java` (`useInternal = true, loadPriority = LoadPriority.LOW`).

---

## 1. Audit Missing Custom Params & Error Codes (`audit_custom_params.py`)

Run [audit_custom_params.py](./scripts/audit_custom_params.py) to compare `./resources/bin/` against `src/main/resources/defs/` and `PlayerErrorCode.java`, complete with resolved `en_US` titles and descriptions:

```bash
# Run full audit across Achievements, DormQuests, StarTowerQuests, and PlayerErrorCode
python .agents/skills/nebula-custom-params/scripts/audit_custom_params.py --target all

# Audit only achievements and print ready-to-paste JSON stubs for missing entries
python .agents/skills/nebula-custom-params/scripts/audit_custom_params.py --target achievements --emit-stubs

# Audit DormQuest or StarTowerQuest gaps with JSON stubs
python .agents/skills/nebula-custom-params/scripts/audit_custom_params.py --target dorm --emit-stubs
python .agents/skills/nebula-custom-params/scripts/audit_custom_params.py --target startower --emit-stubs

# Check resources/bin/ErrorCode.json against PlayerErrorCode.java
python .agents/skills/nebula-custom-params/scripts/audit_custom_params.py --target errors --emit-stubs
```

---

## 2. Resolving Target IDs (`Param1`, `Param2`, `CondParams`)

When `audit_custom_params.py` identifies a missing entry:
1. Read the resolved `en_US` `Title` and `Desc` output by the audit script (e.g., `"Read all episodes of Story Chapter 9"`).
2. Look up the corresponding target ID in `./resources/bin/` using `inspect_table.py`:
   ```bash
   python .agents/skills/nebula-data-extraction/scripts/inspect_table.py Story --search "Chapter 9"
   ```
3. Consult [references/param-patterns.md](./references/param-patterns.md) for the exact `CompleteCond`, `ParamCond1`, and `CondParams` layout used by each subsystem.
4. Add the entry to `src/main/resources/defs/<Name>.json` and verify compilation with:
   ```bash
   bash ./gradlew injectGitHash compileJava
   ```
