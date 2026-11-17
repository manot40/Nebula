# Game Data, Custom Defs & Protobufs

Nebula separates static game tables from player state using reflection-loaded `BaseDef` classes stored in `GameData` (`DataTable<T>`).

## 1. How `ResourceLoader` Maps Files to `GameData`

`ResourceLoader.loadResources()` (`src/main/java/emu/nebula/data/ResourceLoader.java`) scans `emu.nebula.data` for all classes annotated with `@ResourceType`:

```java
@Retention(RetentionPolicy.RUNTIME)
public @interface ResourceType {
    String name();
    LoadPriority loadPriority() default LoadPriority.NORMAL;
    boolean useInternal() default false;
}
```

### Reflection Naming Rule
`ResourceLoader.getTableForResource()` determines which `DataTable<T>` field in `src/main/java/emu/nebula/data/GameData.java` receives the loaded definitions:
1. Take the class's simple name (e.g., `DormQuestDef`).
2. If it ends with `"Def"`, replace `"Def"` with `"Data"` -> `DormQuestData`.
3. Append `"Table"` -> `DormQuestDataTable` (or lower-camel-case `dormQuestDataTable`).

If a matching static `DataTable<T>` field exists on `GameData`, every loaded record is inserted via `table.add(def)` keyed by `def.getId()`.

### Load Priorities
Classes are sorted descending by `LoadPriority` (`HIGHEST(4)` -> `HIGH(3)` -> `NORMAL(2)` -> `LOW(1)` -> `LOWEST(0)`).
- Use `HIGH` / `HIGHEST` when another `Def.onLoad()` looks up records from this table.
- Use `LOW` / `LOWEST` for custom defs (`data/custom/*Def.java`) that enrich an already-loaded `resources/*Def.java` table.

## 2. External `bin/` Resources vs. Internal `defs/`

| Source | `useInternal` | File Location | Format |
| :--- | :--- | :--- | :--- |
| Client datamined tables | `false` (default) | `./resources/bin/<name>` | JSON object (`Map<String, T>`) |
| Server-only / custom defs | `true` | `src/main/resources/defs/<name>` (bundled in jar) | JSON array (`List<T>`) |

### Example: Standard Resource Def
In `src/main/java/emu/nebula/data/resources/DormQuestDef.java`:
- `@ResourceType(name = "DormQuest.json")` loads from `./resources/bin/DormQuest.json`.
- Field names match the JSON keys (often PascalCase like `Id`, `DormQuestGroup`, `Reward1`) because Gson deserializes directly into fields.
- Override `onLoad()` to pre-build transient caches (e.g., `ItemParamMap rewards`).

### Example: Internal Custom Def (`data/custom/`)
When client `bin/*.json` omits server-evaluated fields (such as quest completion conditions), place a JSON array under `src/main/resources/defs/` and a `BaseDef` under `src/main/java/emu/nebula/data/custom/`:

```java
@Getter
@ResourceType(name = "DormQuestParam.json", useInternal = true, loadPriority = LoadPriority.LOW)
public class DormQuestParamDef extends BaseDef {
    private int Id;
    private int CompleteCond;
    private int[] CondParams;

    @Override
    public int getId() {
        return Id;
    }

    @Override
    public void onLoad() {
        var quest = GameData.getDormQuestDataTable().get(this.Id);
        if (quest != null) {
            quest.setParams(this);
        }
    }
}
```
Notice `LoadPriority.LOW` guarantees `DormQuestDataTable` is already populated before `DormQuestParamDef.onLoad()` runs.

## 3. Game Versions & Region Config

Region data versions live in `src/main/resources/versions.json`:
```json
{
  "global": 149,
  "kr": 157,
  "jp": 152,
  "tw": 157,
  "cn": 149
}
```
- `Nebula.loadDataVersions()` checks for an external `./versions.json` first and falls back to classpath `/versions.json`.
- `GameConstants.VERSION` (`src/main/java/emu/nebula/GameConstants.java`) holds the base client version string (e.g., `"2.1.0"`).
- Users can override the region data version in `./data/config.json` via `customDataVersion`.

## 4. Quickbuf Proto Generation (`build.gradle`)

Generated Quickbuf Java sources live in `src/generated/main/emu/nebula/proto/` and **are committed to git**, while raw `.proto` files live in gitignored `./proto/`.

In `build.gradle`:
```groovy
var shouldGenerateProto = System.getenv("GENERATE_PROTO") == "true"
```
- By default (`GENERATE_PROTO` unset), `./gradlew jar` compiles the committed Java files in `src/generated/` without needing `./proto/`.
- When you have `.proto` files in `./proto/` and want to regenerate Java classes:
  ```bash
  GENERATE_PROTO=true ./gradlew updateProto
  ```
- **Warning**: Only set `GENERATE_PROTO=true` when `./proto/` contains valid `.proto` sources; `clean` deletes `src/generated/` when `GENERATE_PROTO=true`.
- After regenerating protos, commit the updated files under `src/generated/main/emu/nebula/proto/` along with any `NetMsgId.java` updates.
