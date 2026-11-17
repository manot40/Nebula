# Player Lifecycle & MongoDB Persistence

Nebula stores state in MongoDB via **Morphia** (`dev.morphia.morphia:morphia-core`), backed either by an external MongoDB instance or the embedded `mongo-java-server` (H2 file `./data/database.mv`).

## 1. Account vs. Game Database Split

`Nebula.initDatabases()` initializes two `DatabaseManager` instances (`accountDatabase` and `gameDatabase`), which may point to the same database when `config.useSameDatabase = true`:

- **`@AccountDatabaseOnly`** (e.g., `Account` in `src/main/java/emu/nebula/game/account/Account.java`): Mapped **only** when `serverType.runLogin()` is true.
- **All other `@Entity` classes**: Mapped **only** when `serverType.runGame()` is true.

Always annotate Morphia entities with:
```java
@Entity(value = "collection_name", useDiscriminator = false)
```
Without `useDiscriminator = false`, Morphia injects `_t` class-discriminator fields into every document.

## 2. `Player` Lifecycle

Managed by `PlayerModule` (`src/main/java/emu/nebula/game/player/PlayerModule.java`):

1. **Creation** (`PlayerModule.createPlayer()`):
   - Reserves `uid` (via `account.getReservedPlayerUid()` or `Nebula.getGameDatabase().getNextObjectId(Player.class)`).
   - Instantiates `Player`, calls `player.onLoad()`, `player.save()`, `player.onCreate()` (sends welcome mail), and caches the player.
2. **Loading** (`PlayerModule.loadPlayer(Account)`):
   - Checks `cachedPlayersByAccount`; on miss, loads `Player` from `players` collection and calls `player.onLoad()`.
   - `Player.onLoad()` loads `Inventory` first (so items/migrations exist before dependent managers), then loads all `PlayerManager` fields via `loadManagerFromDatabase(ManagerClass.class)`.
3. **Login** (`GameSession.setPlayer()` -> `Player.onLogin()`):
   - Runs `checkResetDailies()`, `activityManager.onLogin()`, `traceHuntManager.updateControl()`, `checkBrokenHonor()`, and updates `lastLogin`.
4. **Post-response** (`Player.afterResponse()`):
   - Flushes deferred saves (e.g., `achievementManager.isQueueSave()`, `dormManager.isQueueSave()`).
5. **Cleanup** (`GameContext.cleanupInactiveSessions()` -> `session.clearPlayer()` -> `Player.setSession(null)`):
   - Evicts the player from `PlayerModule` cache once the session expires.

## 3. Two Patterns for Player Persistence

### Pattern A: Single-Document `PlayerManager` (1 doc per player, `_id = player.uid`)
Used for `DormManager`, `QuestManager`, `AchievementManager`, `Mailbox`, `Inventory`, `GachaManager`, `ActivityManager`, etc.

Reference pattern from `src/main/java/emu/nebula/game/dorm/DormManager.java`:

```java
@Getter
@Entity(value = "dorm", useDiscriminator = false)
public class DormManager extends PlayerManager implements GameDatabaseObject {
    @Id
    private int uid;

    private transient boolean queueSave;

    @Deprecated // Morphia only
    public DormManager() {}

    public DormManager(Player player) {
        super(player);
        this.uid = player.getUid();
        // Initialize fields/collections...
        this.save();
    }
}
```

**Mandatory wiring checklist for a new `PlayerManager`**:
1. Add `private transient MyManager myManager;` (with `@Getter` on `Player`) in `src/main/java/emu/nebula/game/player/Player.java`.
2. Load it in `Player.onLoad()`:
   ```java
   this.myManager = this.loadManagerFromDatabase(MyManager.class);
   ```
   `loadManagerFromDatabase()` queries `_id == player.getUid()`, calls `manager.setPlayer(this)` + `manager.onLoad()`, or creates a new instance via the `(Player)` constructor if missing.
3. Delete its collection document in `PlayerModule.deletePlayer(int uid)` (`src/main/java/emu/nebula/game/player/PlayerModule.java`):
   ```java
   datastore.getCollection(MyManager.class).deleteOne(idFilter);
   ```
4. If using `queueSave` for batched writes, check and call `save()` inside `Player.afterResponse()`.

### Pattern B: Multi-Document Collection Entities (N docs per player, indexed by `playerUid`)
Used when a player owns many independent records (`GameCharacter`, `GameDisc`, `GameItem`, `GameResource`, `StarTowerBuild`):
- Primary key is `@Id private ObjectId uid;`
- Player link is `@Indexed private int playerUid;`
- Held in memory by a non-`@Entity` `PlayerManager` (such as `CharacterStorage` or `StarTowerManager`) initialized in `Player()` constructor and loaded via `loadFromDatabase()`.
- Must also be cleaned up in `PlayerModule.deletePlayer()` using `deleteMany(Filters.eq("playerUid", uid))`.

## 4. Custom Fastutil BSON Codecs

`DatabaseManager` (`src/main/java/emu/nebula/database/DatabaseManager.java`) registers custom Morphia codecs for primitive collections so you do not need boxed `HashMap<Integer, Integer>` or `HashSet<Integer>` on hot paths:

| Java Field Type | Codec Class |
| :--- | :--- |
| `it.unimi.dsi.fastutil.ints.IntSet` | `IntSetCodec` |
| `it.unimi.dsi.fastutil.ints.IntList` | `IntListCodec` |
| `it.unimi.dsi.fastutil.ints.Int2IntMap` | `Int2IntMapCodec` |
| `emu.nebula.game.inventory.ItemParamMap` | `ItemParamMapCodec` |
| `emu.nebula.util.ints.String2IntMap` | `String2IntMapCodec` |
| `emu.nebula.util.Bitset` | `BitsetCodec` |

> [!TIP]
> Avoid `null` collections on Morphia entities—initialize fastutil maps/sets in the `(Player)` constructor (and guard/backfill in `onLoad()` when adding new persisted fields to existing player documents).

## 5. Partial Updates vs. Full `save()`

- **`GameDatabaseObject.save()`** calls `Nebula.getGameDatabase().save(this)` and upserts the entire document.
- **`DatabaseManager.update(...)`** performs targeted `$set` updates by `_id` (e.g., `Nebula.getGameDatabase().update(this, this.getUid(), "level", this.level)`), or batched multi-field `$set` via `update(obj, uid, Map<String, Object>)`, `$unset` via `updateUnset(...)`, and `$addToSet` via `addToSet(...)`.
- For subsystems that trigger multiple mutations during a single packet (like quests/achievements/dorm), set `this.queueSave = true` and flush in `Player.afterResponse()` instead of saving repeatedly mid-request.

## 6. Daily, Weekly & Monthly Resets

- Reset boundaries are anchored to `config.serverOptions.dailyResetHour` (default `4` = 04:00 server local time).
- Use `Utils.getResetEpochDay()` and `ResetCycle` (`DAILY`, `WEEKLY`, `MONTHLY`) via `Utils.getResetPeriodIndex(...)`.
- `GameContext.run()` checks every second for day rollover and calls `player.checkResetDailies()` on online sessions; offline players catch up on login in `Player.onLogin()`.
- Quest and achievement progress fires through `player.trigger(...)` (`QuestCondition`, `AchievementCondition`), which broadcasts to `QuestManager`, `BattlePass`, and `AchievementManager`.
