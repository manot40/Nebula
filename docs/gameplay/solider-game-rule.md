# Soldier Mode (`Through Hostile Depths` / 2.0+) Gameplay Rules

This document summarizes the current understanding of how the `2.0+` Soldier auto-chess mode (**Through Hostile Depths**) works, based on the in-game tutorial/rule slides, client bin tables (`resources/bin/Soldier*.json`, `resources/bin/Config.json`), protobuf definitions (`PublicSoldier.java`, `Soldier*.java`), and live packet captures.

---

## 1. Run Initialization & Tyrant Authority

- **Difficulty Tier (`SoldierGradeChallenge.json`)**: Entering a Depth/Challenge initializes a run with starting SAN (`InitialHp`, e.g. `100`), starting node group (`NodeGroupId`), check stage group (`CheckStageGroupId`), and difficulty scaling.
- **Starting Resources (`Config.json`)**:
  - `SoldierInitialCoinNumber`: `4` Talent Gold.
  - `SoldierInitialChessNumber`: `3` starter 1★ members placed in the **Reserve Area**.
  - `SoldierBaseWeightToCost`: `{"1":180,"2":180,"3":90,"4":0,"5":0}` (starter units are weighted toward 1–3 cost members; 4–5 cost members have weight `0`).
  - Starting Shop / Tyrant Level: `Lv. 3` (`0` EXP).
- **Tyrant Authority (`SelectBossAbilityCase` / `SoldierBossTemplate.json`)**:
  - At the start of a run, the player selects a Boss/Tyrant template (`101` unlocked by default; `102`–`104` unlocked via Strategy Tree nodes `10003`–`10005`), setting initial personality stats (`Personality_Intuition`, `Personality_Analysis`, `Personality_Chaos`), command buffs (`AbilityBuff` `4101`–`4104`), and passive economy/shop effects.

---

## 2. Board Layout, Reserve Area, & Deployment Limit

- **Combat Area (`9` total slots in `SoldierPositionEffect.json`)**:
  - **Offense Zone (`Slots 1–3`, `PositionType = 1`)**: Up to 3 frontline members who fight directly on the field and cast Offense Skills when Energy is full. Individual Offense slots provide position stat bonuses (e.g. Max HP/DEF, ATK/Crit Rate, ATK SPD/Energy Regen). At least 1 Offense member is required to fight.
  - **Assist Zone (`Slots 4–9`, `PositionType = 2`)**: Up to 6 support members who do not stay on the field, but cast Assist Skills when Energy is full.
- **Reserve Area (`Slots 10–17`, `8` slots)**: Waiting bench at the bottom of the screen for holding recruited members prior to deploying, merging, or selling.
- **Board Deployment Limit (`SoldierShopLevel.json` -> `Count`)**:
  - The total number of members allowed on the Combat Board (`Slots 1–9` combined) is capped by Tyrant Level:
    - Lv 1–3: `3`
    - Lv 4: `4`
    - Lv 5: `5`
    - Lv 6: `6`
    - Lv 7: `7`
    - Lv 8: `8`
    - Lv 9–10: `9`
  - **No duplicate deployed members & free slot assignment**: Any member can be placed in either an Offense (`1–3`) or Assist (`4–9`) slot regardless of `CharacterType` (which governs archetype/skill behavior). However, the same member `Tid` cannot be deployed on the Combat Board more than once at the same time (duplicates may stay in Reserve for star merging).

---

## 3. Shop, Drop Rates, & Pool Rules

- **Fixed 5 Shop Slots**: The shop always offers `5` member slots, regardless of Tyrant Level.
- **Member Cost (`1–5`)**: Recruitment price equals the member's cost tier (`1` to `5` Gold). Higher-cost members have stronger stats/skills and lower appearance rates.
- **Level-Based Drop Rates (`SoldierShopLevel.json`)**:
  - Upgrading Tyrant Level (`SoldierBuyExp = "4,4"`: spend `4` Gold for `+4` EXP) increases both the **board deployment limit (`Count`)** and the drop percentages for higher-cost tiers (`Rarity1`–`Rarity5`):
    - Lv 1–3: `100% / 0% / 0% / 0% / 0%`
    - Lv 4: `65% / 25% / 10% / 0% / 0%`
    - Lv 5: `45% / 33% / 20% / 2% / 0%`
    - Lv 6: `30% / 40% / 25% / 5% / 0%`
    - Lv 7: `19% / 30% / 40% / 10% / 1%`
    - Lv 8: `18% / 25% / 32% / 23% / 2%`
    - Lv 9: `15% / 20% / 25% / 30% / 10%`
    - Lv 10: `5% / 10% / 20% / 43% / 22%`
- **Refresh & Lock**:
  - Manual refresh costs `2` Gold (`SoldierRefreshCost = 2`).
  - The shop automatically refreshes once upon entering each new node unless **Lock** is toggled on.
- **Shared Pool Quantity (`Config.json`)**:
  - `SoldierChessCostToQuantity`: `{"1":9,"2":9,"3":9,"4":9,"5":9}` — 9 total copies per character in the pool for a run (recruiting/merging consumes copies from the pool; selling returns them).

---

## 4. Member Stars (Merging), Traits, & Prime Stance

- **Member Stars**: Having `3` identical copies at the same star level automatically combines them into the next star tier (`3 × 1★ -> 2★`, `3 × 2★ -> 3★`), granting only the evolved member (no extra card). `3★` is normally the maximum tier (`SoldierCharacter.MaxStar`).
- **Traits (`SoldierPartner.json`, `SoldierPartnerGroup.json`)**: Members carry trait affixes (`PartnerType`). Deploying multiple distinct members sharing a Trait activates tiered synergy buffs (e.g. `2 / 4 / 5 / 6`).
- **Track Traits & Lineup Guides (`SoldierRecommendBuilds.json`)**: Players can track Traits or apply Lineup Guides so matching members are highlighted in the Shop.
- **Prime Stance**: Known Trekkers recruited on the player's account (`CharacterId` in `SoldierCharacter.json`) contribute attribute bonuses based on Trekker Level and Talent Level.

---

## 5. Combat, SAN, & Talent Gold Economy

- **Automatic Combat**: Auto-attacks, Offense Skills, and Assist Skills run automatically. Victory occurs when all enemies are defeated; defeat occurs if all Offense members fall. A battle countdown triggers overtime full-firepower mode when it expires.
- **Sanity (SAN)**:
  - Winning restores a small amount of SAN (`AddHp`); losing reduces SAN (`LoseHp`).
  - Reaching `0` SAN ends the investigation immediately. Remaining SAN at the Final Battle contributes to score (`SoldierHpScoreRange`).
- **Node Income (`Config.json`)**:
  - **Node Income** = Base Gold (`SoldierNodePlan.Coin`) + Interest + Win/Loss Streak Bonus + extra bonuses (Blessings / special members / Traits).
  - **Interest (`SoldierCoinInterest = "10,1,2"`)**: `+1` Gold per `10` Gold saved, capped at `+2` Gold (may be modified by Blessings/Effects).
  - **Win/Loss Streaks (`SoldierCoinWinningStreak`, `SoldierCoinLosingStreak`)**: `0+: +1`, `2+: +2`, `5+: +3`.
  - **Automatic EXP**: Tyrant EXP (`SoldierNodePlan.Experience`) is granted per node.

---

## 6. Phases, Nodes, & Blessings

- **Node Types (`SoldierNodePlan.json`)**:
  - `1` (`SoldierNodeType_Event`): **Opportunity / Turning Point** — personality dice-check events (`StoryEventOfPersonalityCase`, up to 3 selectable options), costly events (`StoryEventOfCostlyCase`), or dispute Risk Battles (`EventSettlementOfDispute`).
  - `2` (`SoldierNodeType_Battle`): **Encounter** — standard combat node.
  - `3` (`SoldierNodeType_MidBossBattle`): **Lord** — mid-phase boss combat.
  - `4` (`SoldierNodeType_FinalBossBattle`): **Final Battle** — phase-ending boss combat.
  - `5`: **Beyond the Limit** (`IsDpsCheckStage` / `SoldierCheckStageAddScore`) — timed DPS check stage to push final score.
- **Blessings (`SoldierStrategyCard.json` / `SelectCardData`)**:
  - Risk Battles (`StrategyCardCount`) and Opportunity rewards grant Strategy Cards (`SelectCardData` with reroll support or direct `Card` rewards) that add match-wide effects (`CardBuff`, Gold/HP/shop/refresh modifiers).

---

## 7. Meta Progression & Strategy Tree

- **Season Rewards (`SoldierTotalReward.json`)**: Runs award Season EXP (`RewardExp`) to unlock tiered milestone rewards.
- **Strategy Nodes (`SoldierTechNode.json`)**: Runs grant tech currency (`TechPointTid`) used to unlock permanent seasonal Strategy Tree passives and extra Tyrant Authorities (`10003`–`10005`). Economy/run passives (`Type = 1`) apply server-side and sync as `TechNodeBuff` (`110xxx`), while combat stat passives (`Type = 2`) and `Squad Combat` (`10010`) apply client-side from the unlocked tech bitset.
- **Battle Reports**: Recent runs (limit `10`) and favorite runs (limit `10`) can be saved, viewed, and favorited (`Config.json`).
