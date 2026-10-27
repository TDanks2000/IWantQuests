# Architecture and extension contracts

The server is the source of truth. `IWantQuests` only wires mod events and payload
registration. Client classes are constructed from a separate `Dist.CLIENT`
entrypoint and are never referenced by common/server handlers.

| Package | Responsibility |
| --- | --- |
| `quest` | Immutable definitions, catalogue indexes, validation, data-pack reload listener |
| `engine` | Plain-Java progression/claim rules and Minecraft server adapters |
| `objective` | Extensible objective validation and event matching |
| `reward` | Domain validation registry, Minecraft providers, inventory preflight/delivery |
| `team` | UUID memberships, invitations, owner transfer and solo restoration |
| `progress` | Persisted domain state and Minecraft `SavedData` codec |
| `network` | Bounded action/sync payloads and per-player quest snapshots |
| `commands` | Player-scoped book, Check and team commands |
| `client` / `client.ui` | Read-only synchronized state, quest-book screen, buttons and canvas transform |
| `testing` | Development-only real-game checks; excluded from release JAR |

## Persistence

One server-scoped `SavedData` stores versioned team UUIDs, memberships, invitations,
objective counters/signatures, completions, and player-wide reward claim sets.
`QuestSavedData.TYPE` uses a Codec to serialize the domain state inside Minecraft's
compressed saved-data format. The file is
`<world>/data/iwantquests/progress.dat`. Ordinary progress marks saved data dirty
and uses the game's normal save lifecycle. No write occurs for unchanged counters.
Reward claims trigger a server save containing the claim ledger and player inventory.
Clean restarts are supported; the system is not a filesystem transaction across
world/player files during a power loss or process kill halfway through a save.

All mutation and delivery runs on the server thread. Item rewards are prepared
against a copied main inventory, then applied as a single synchronous operation.
The claim is recorded before delivery so reentrant/repeated requests cannot pay
again. Providers must not fail after a successful preflight or execute arbitrary
commands. Player reward claims are independent of teams, so membership switches
cannot reset them. Objective counters retain selector signatures to avoid treating
old progress as progress for an unrelated edited objective.

## Networking

Protocol `1.0.0` is required on both sides. Server-to-client messages are JSON documents that
`SyncAssembler` splits into parts of at most 120,000 characters and joins on arrival, so the
size of a catalogue is not bounded by the 1 MiB packet limit. `ActionPayload` contains a bounded
whitelisted action and identifiers, never client-provided counts or rewards.
Actions cover sync, claim, Check, submit, manual and team management. The server validates
team, availability, completion, definition and objective type before any action.
Requests have a per-player rate limit; duplicate claims have a persistent guard.

Initial join/reload synchronization sends definitions once and a personal state
snapshot. Updates contain changed objective counts/completion/claims/availability
and team metadata. Each online member receives a view with their own claim flags.
A client disconnect clears cached data. Rejoining reads server persistence.
Test/fake-player connections without a negotiated channel are not sent mod payloads.

Editor access is disabled by default through `editorEnabled` in the server's common
config. When enabled, `ServerQuests.canEdit` requires gamemaster (level 2) permission
or a UUID in `editorPlayers`. The server checks this before parsing or applying every
`EditPayload` operation; client UI state is never an authorization source. Denied
players receive `EditorInfo.NONE`. Access changes are synchronized once per second,
and the client exits editor mode when access is revoked. Local multiplayer clients
cannot grant themselves access through their own configuration.

## Adding objective types

During mod construction, before the first resource reload, register a unique type
with `ObjectiveRegistry.register(id, Type)`. Implement definition validation and
matching against `Contribution`. A trusted server event adapter calls
`ServerQuests.contribute(player, contribution)` with its registered type.
Generic contributions accumulate and cap counts at the goal. `possess` uses
snapshot aggregation; `submit` and `manual` use validated targeted actions.
The current definition record exposes target/count, description and region fields;
a future schema revision can add type-specific structured parameters.
Do not accept contributions supplied by an arbitrary client packet.

## Adding reward types

Register domain validation with `RewardDefinitionRegistry.register(id, validator)`
and delivery with `RewardRegistry.register(id, provider)` before resource loading.
The validator receives registered-target validation from the loader. A provider's
`items` describes items for capacity preflight; `afterItems` applies deterministic
non-item effects. Current implementations are items and XP. Future command,
random, loot-table, currency and choice rewards need explicit transactional/preflight
contracts before being exposed to pack authors; they are not implemented here.

## Editor boundary

Definitions carry independent chapter IDs, node positions, prerequisite lists,
icons, descriptions and stable objective IDs. The screen reads those definitions
without owning authoritative gameplay state. `QuestEditing` handles canvas gestures and
`QuestInspector` builds the property panel. Unfinished text survives sync and resizing;
accepted definitions always come back from the server. Keyboard movement is grouped into
one undo step and flushed when leaving the book.

`EditPayload` carries an operation and bounded JSON body. `ServerQuests` checks creator
access on every request. Clients supply IDs, field patches or copied definitions, never
file paths. `QuestDraft` holds a private persistent branch per author UUID, with its own
`QuestEditor` and 50-step undo history. `QuestEditor` validates the entire proposed
catalogue before writing staged files. Bundled and data-pack records remain read-only.
Copies receive new IDs and remap internal links; templates store definitions separately
from all progression data.

Accepted draft edits synchronize only the author. Review computes additions, changed
fields, removals and progress/reward warnings. Publication requires unchanged live
definitions and exact baseline file contents, preventing stale drafts from overwriting
another author or external update. The complete batch is validated and backed up before
the live engine is reconciled and players are synchronized. File replacements are atomic;
batch failures attempt rollback. A power failure across multiple files is not a transaction.
Drafts survive restarts; undo history is released when the author disconnects.

Player search filters hidden locked quests. Preview uses the draft with fresh-player
progress and disables gameplay actions. Pins live in a local preference file scoped by
world path or server address. The tracker renders only published, player-visible quests
using authoritative snapshots. Export, extension-specific editor fields and independently
connected multiplayer regression checks remain future work.
