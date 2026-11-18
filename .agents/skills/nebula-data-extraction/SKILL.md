---
name: nebula-data-extraction
description: >-
  Use this skill when inspecting, querying, or searching datamined game data in resources/bin/*.json and resources/language/ (e.g. en_US, zh_CN), scaffolding new *Def.java classes in src/main/java/emu/nebula/data/resources/ and GameData.java DataTable fields, or auditing schema drift and unmapped tables.
---

# Nebula Game Data Extraction & Def Scaffolding

Nebula loads datamined client tables from `./resources/bin/<Table>.json` and localized strings from `./resources/language/<locale>/<Table>.json` into reflection-mapped `BaseDef` classes in `src/main/java/emu/nebula/data/resources/` and `DataTable<T>` fields in `src/main/java/emu/nebula/data/GameData.java`.

> **CRITICAL**: `./resources/bin/` contains 549 JSON files (many 10,000+ lines). **Never** read large `resources/bin/*.json` or `resources/language/*.json` files raw into context. Always use the helper scripts below to inspect schemas, resolve localized text, scaffold `*Def.java` files, and detect field drift.

---

## 1. Inspect Schema & Query Records (`inspect_table.py`)

Run [inspect_table.py](./scripts/inspect_table.py) to view a table's inferred Java types, value ranges, and records with inline resolved `resources/language/<lang>/<Table>.json` strings:

```bash
# Inspect full field schema, inferred Java types, presence frequency, and min/max values
python .agents/skills/nebula-data-extraction/scripts/inspect_table.py <TableName> --schema-only

# Look up specific record IDs with resolved en_US localization strings
python .agents/skills/nebula-data-extraction/scripts/inspect_table.py Achievement --id 657,663 --lang en_US

# Search records by keyword across both raw fields and localized strings
python .agents/skills/nebula-data-extraction/scripts/inspect_table.py Story --search "Chapter" --limit 15

# Filter records by exact field value (supports multiple --where filters)
python .agents/skills/nebula-data-extraction/scripts/inspect_table.py Achievement --where CompleteCond=63 --limit 25
```

---

## 2. Scaffold `*Def.java` & Diff Existing Defs (`scaffold_def.py`)

Run [scaffold_def.py](./scripts/scaffold_def.py) to generate an idiomatic `BaseDef` class and `GameData.java` field, or check an existing `*Def.java` for newly added JSON fields:

```bash
# Preview generated <Table>Def.java + GameData.java DataTable line on stdout (read-only)
python .agents/skills/nebula-data-extraction/scripts/scaffold_def.py <TableName>

# Compare an existing *Def.java against resources/bin/<Table>.json to see missing/new JSON keys
python .agents/skills/nebula-data-extraction/scripts/scaffold_def.py <TableName> --diff

# Create a brand-new src/main/java/emu/nebula/data/resources/<Table>Def.java (refuses to overwrite existing files)
python .agents/skills/nebula-data-extraction/scripts/scaffold_def.py <TableName> --write
```

### `ResourceLoader` & `GameData` Checklist
1. **Naming invariant**: `ResourceLoader.getTableForResource()` strips `"Def"` and appends `"DataTable"` (e.g., `DormQuestDef` -> `GameData.DormQuestDataTable`).
2. **Load priority**: Set `@ResourceType(name = "<Table>.json", loadPriority = LoadPriority.HIGH)` when another `Def.onLoad()` queries this table, or `LoadPriority.LOW` when this `Def.onLoad()` attaches data onto another table.
3. **Pre-compute in `onLoad()`**: Build transient structures (`ItemParamMap rewards`, parsed `int[] condParams`, lookup caches) inside `onLoad()` rather than re-parsing on every packet.

---

## 3. Audit Unmapped Tables & Schema Drift (`audit_tables.py`)

Run [audit_tables.py](./scripts/audit_tables.py) to compare all `./resources/bin/*.json` files against `src/main/java/emu/nebula/data/resources/*Def.java` and `GameData.java`:

```bash
# Check existing *Def.java classes for schema drift (JSON keys present in bin/ but not declared in Java)
python .agents/skills/nebula-data-extraction/scripts/audit_tables.py --drift

# List unmapped resources/bin/*.json tables (with optional prefix filter)
python .agents/skills/nebula-data-extraction/scripts/audit_tables.py --unmapped --filter Activity
```

---

## 4. Verification
After adding or modifying any `*Def.java` or `GameData.java` entry, verify compilation with:
```bash
bash ./gradlew injectGitHash compileJava   # or .\gradlew.bat injectGitHash compileJava on Windows
```
