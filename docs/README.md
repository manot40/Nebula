# Nebula Developer Documentation

Welcome to the developer documentation for **Nebula**. These guides cover server architecture, core conventions, and step-by-step workflows for implementing new features and maintaining game-version compatibility.

> For a condensed, always-loaded summary of build commands and invariants, see [`AGENTS.md`](../AGENTS.md).

## Documentation Index

| Document | Topics Covered |
| :--- | :--- |
| [architecture.md](architecture.md) | Boot sequence (`Nebula.main`), `ServerType` (`LOGIN`/`GAME`/`BOTH`), `HttpServer` routing, `GameContext` vs. `Player` managers, and the 1-second tick loop. |
| [networking-and-handlers.md](networking-and-handlers.md) | `AgentZoneHandler` packet lifecycle, AEAD crypto, `@HandlerId` auto-registration, dummy handlers, Quickbuf usage, `PlayerChangeInfo`, and `nextPackage` chaining. |
| [player-and-persistence.md](player-and-persistence.md) | `Player` lifecycle, `PlayerManager` vs. multi-document entities, Morphia rules, custom fastutil BSON codecs, deferred saves, and `ResetCycle` resets. |
| [game-data-and-protos.md](game-data-and-protos.md) | `BaseDef`, `@ResourceType`, `GameData` reflection mapping, `src/main/resources/defs/` internal overrides, `versions.json`, and Quickbuf regeneration. |
| [commands-plugins-and-dev-env.md](commands-plugins-and-dev-env.md) | `@Command` handlers, `Plugin` API, `./gradlew jar` build gotchas, embedded H2 vs. MongoDB (`compose.yaml`), `mitmproxy`, and packet logging. |
| [reverse-engineering.md](reverse-engineering.md) | Integrating reverse-engineered packets, opcodes (`NetMsgId`), client `bin/*.json` schemas, custom `defs/*.json` params, condition enums, and version bumps. |

## Recommended Reading Paths by Task

- **First time in the codebase**: [architecture.md](architecture.md) -> [commands-plugins-and-dev-env.md](commands-plugins-and-dev-env.md)
- **Implementing a new client request/feature**: [networking-and-handlers.md](networking-and-handlers.md) -> [player-and-persistence.md](player-and-persistence.md) -> [game-data-and-protos.md](game-data-and-protos.md)
- **Adding a new `bin/*.json` table or server-side param def**: [game-data-and-protos.md](game-data-and-protos.md) -> [reverse-engineering.md](reverse-engineering.md)
- **Updating Nebula for a new game client version**: [game-data-and-protos.md](game-data-and-protos.md) -> [reverse-engineering.md](reverse-engineering.md)
- **Adding a server/player command or plugin**: [commands-plugins-and-dev-env.md](commands-plugins-and-dev-env.md)
