# Custom Server Parameter Patterns (`src/main/resources/defs/`)

This reference documents how Nebula's internal parameter files in `src/main/resources/defs/*.json` map to client tables in `./resources/bin/*.json` and server-side condition evaluators.

---

## 1. `AchievementParam.json` -> `AchievementDef`

- **Loaded by**: `src/main/java/emu/nebula/data/custom/AchievementParamDef.java` (`@ResourceType(name = "AchievementParam.json", useInternal = true, loadPriority = LoadPriority.LOW)`)
- **Attaches to**: `GameData.getAchievementDataTable().get(this.Id).setParams(this)`
- **Schema**:
  ```json
  {
    "Id": 663,
    "Param1": 1123,
    "ParamCond1": 0,
    "Param2": 0,
    "ParamCond2": 0
  }
  ```
- **`ParamCond1` / `ParamCond2` (`AchievementParamCond.java`)**:
  - `0` = `EQUALS` (`value == param`) — default when omitted
  - `1` = `ANY` (`true`)
  - `2` = `NOT_EQUALS` (`value != param`)
  - `3` = `GREATER_THAN` (`value > param`)
  - `4` = `GREATER_THAN_OR_EQ` (`value >= param`)
  - `5` = `LESS_THAN` (`value < param`)
  - `6` = `LESS_THAN_OR_EQ` (`value <= param`)
- **Common `CompleteCond` -> `Param1` Target Table Mappings**:
  - `63` (`StoryClear`): `Param1` = `Story.json` `Id` (e.g., `"Id": 663` -> `"Param1": 1123` for final episode of a story chapter).
  - `15` (`CharacterWithSpecificUpLevel`) / `45` (`GiftGiveTotal`): `Param1` = `Character.json` `Id` or level threshold.
  - `58` (`RegionBossClearSpecificTotal`) / `56` (`RegionBossClearSpecificFullStarWithBossIdAndDifficulty`): `Param1` = `RegionBossLevel.json` / `RegionBoss.json` ID.
  - `106` (`TowerClearSpecificLevelWithDifficultyAndTotal`) / `114` (`TowerBossChallengeSpecificWithTotal`): `Param1` = `StarTower.json` ID.
  - `200` (`ClientReport`): Handled directly via client-reported `Events` in `AchievementManager.handleClientEvents()` — does not require `AchievementParam.json`.

---

## 2. `DormQuestParam.json` -> `DormQuestDef`

- **Loaded by**: `src/main/java/emu/nebula/data/custom/DormQuestParamDef.java`
- **Attaches to**: `GameData.getDormQuestDataTable().get(this.Id).setParams(this)`
- **Schema**:
  ```json
  {
    "Id": 500010,
    "CompleteCond": 163,
    "CondParams": [1, 701001]
  }
  ```
- **Convention**:
  - `CondParams[0]` is always the **required count/aim** (`getAim()`).
  - `CondParams[1]` (optional) is the **specific target ID** (e.g., Dorm room ID `701001`, furniture item ID `700003`, character ID, etc.).

---

## 3. `StarTowerQuestParam.json` -> `StarTowerQuestDef`

- **Loaded by**: `src/main/java/emu/nebula/data/custom/StarTowerQuestParamDef.java`
- **Attaches to**: `GameData.getStarTowerQuestDataTable().get(this.Id).setParams(this)`
- **Schema**:
  ```json
  {
    "Id": 100,
    "CompleteCond": 106,
    "Aim": 1,
    "Param1": 101,
    "ParamCond1": 0,
    "Param2": 0,
    "ParamCond2": 0
  }
  ```
- **Retroactive completion note**: `QuestManager.addQuest()` retroactively checks `TowerGrowthSpecificNote` (`123`), `TowerGrowthWithSpecificNote` (`124`), and `TowerBookWithSpecificEvent` (`122`) against `StarTowerManager` state when a quest is unlocked.

---

## 4. Parameter Ordering Gotcha in `bin/*.json` Quest Tables

When a `resources/bin/*.json` quest table includes its own `CompleteCondParams` string (such as `StarTowerBookEventReward.json`), inspect how the values are ordered:
- Some client tables store `[targetId, requiredAmount]`, whereas `GameQuest` expects `condParams[0] = requiredAmount` and `condParams[1] = targetId` (see `StarTowerBookEventRewardDef.onLoad()`).
- Always check existing `QuestData` implementations (`DailyQuestDef`, `WeeklyQuestDef`, `BattlePassQuestDef`, `StarTowerBookEventRewardDef`, `WorldClassDef`) when implementing `getCompleteCondParams()`.
