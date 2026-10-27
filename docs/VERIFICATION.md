# Verification — 1.0.0

## 1.0.0 additions: rewards, repeatable quests, chunked sync (2026-10-27)

- `gradlew.bat build`: 123 JUnit tests passed. New tests cover: splitting and rejoining large
  messages (exact chunk boundaries, interleaved or missing parts, absurd part counts); reward
  validation for every type; weighted random rolls, including the statistical distribution;
  choice resolution and malformed picks; command text being withheld from players; repeatable
  quests for solo and team play, a leaving member, save and reload, failed delivery and the
  definition restrictions.
- `gradlew.bat runGameTestServer`: all 7 required tests passed. The new `reward_types` test uses the
  real server: a choice delivers only the picked option, random rewards roll per count, a command
  reward runs as the claiming player, a loot table delivers items, a missing loot table is
  reported, a full inventory refuses loot, and the editor guard refuses new command rewards from
  players who are not level 4 operators (level 2 included).
- `gradlew.bat runSmokeClient`: passed on a fresh world and again on the same world (restart
  persistence). It now also loads `examples/rewards` and a generated catalogue of 2,500 quests
  (about 1.5 million characters, well over the old 900 KB limit). The client received all 2,500
  quests intact in parts over the real integrated-server connection. A choice reward claim without
  a pick is refused, and a claim with a pick delivers only that option. Screenshots
  `reward-choice*.png`, `reward-random.png` and `reward-repeatable.png` were inspected by eye (stored
  in `docs/design/after`).
- Not exercised in a running game: a repeatable quest restarting after several real players claim,
  a command reward claimed by a real player (the GameTest runs it as a mock player), and a
  catalogue joined over a real network connection to a dedicated server. The repeat rule is
  covered by the unit tests above.

## 1.0.0 release candidate (2026-10-27)

- `gradlew.bat build`: 89 JUnit tests passed, zero failures. The new test checks that a reward
  delivery that throws does not consume the player's claim. The jar is `iwantquests-1.0.0.jar`
  and contains no development test classes.
- `gradlew.bat runGameTestServer`: all 6 required tests passed on the dedicated-server distribution.
- `gradlew.bat runSmokeClient`: passed against an integrated server with the 1.0.0 network protocol
  (initial sync, pickup, unlock, claim, team creation, themes, editor, drafts, publish review,
  search and pinned HUD). Screenshots were regenerated under `runs/smoke/screenshots`; they were
  not visually re-inspected in this pass.
- Hardening: unexpected runtime errors in quest actions and editor requests are logged and reported
  to the player instead of reaching the network layer.
- Still untested by the maintainers: Linux and macOS, two independent human clients on a dedicated
  server, and very large modpacks.

## Creator workflows and player navigation (2026-10-24)

- `gradlew.bat build`: 88 JUnit tests passed. New domain tests cover private draft
  isolation and restart recovery, publication and backups, two-author conflicts,
  external edits before reload, malformed published files, discard, no-op publication,
  safe draft directory boundaries, copied-link remapping and undo, template persistence
  and safe deletion, and warning about existing claims when rewards change.
  Placement tests cover repeated pastes, larger nodes and a full search area.
- `gradlew.bat runGameTestServer`: all 6 required tests passed in the dedicated-server
  distribution. The authorization scenario rejects all authoring and publication
  operations for players without access before their request bodies are parsed.
- The integrated-server smoke scenario exercises real item/tag chooser widgets,
  template save/place/delete, Ctrl+D/C/V, isolated authoring, fresh-player preview,
  review and confirmation, actual publication, search selection using Enter, pinning,
  preference-file reload, HUD rendering and cleanup publication. The previous editor
  checks for focus, resize, validation recovery, task/reward editing and access
  revocation remain part of this scenario.
- Screenshots under `runs/smoke/screenshots/creator-*.png` were inspected at 1280×800
  and 854×480. The default theme was used for these new workflows. Client and server
  now negotiate protocol 0.3.0; development test classes remain excluded from release jars.
- Remaining coverage gaps: independent remote clients, concurrent publication with real
  network latency, very large quest books, additional languages/themes, non-Windows
  platforms and power loss during a multi-file publish. Preview is a fresh-player view,
  not a progression simulator. See [creator workflows](CREATOR_WORKFLOWS.md).

## Creator access and editor usability (2026-10-24)

- Authoring is disabled by default. Enabling `editorEnabled` permits level 2 operators
  and UUIDs in `editorPlayers`. Dedicated-server GameTests verify disabled access,
  trusted authors, creative players without permission, level 1 denial, level 2 access,
  empty editor metadata for unauthorised players, and rejection of all six mutation
  operations without changing the catalogue. All 6 required GameTests passed.
- Quest properties use Details, Tasks and Rewards tabs with a fixed save/status footer.
  The grouped toolbar includes New chapter and contextual tool guidance. Flags show
  On/Off in text. Small editor windows collapse chapter names to preserve canvas space.
- `gradlew.bat build`: 73 JUnit tests passed, zero failures. The integrated-server
  smoke scenario exercises the tab actions, Ctrl+S, focus/draft preservation, resize,
  undo/redo, reward/task editing, and closing an active editor after access is revoked.
  An invalid item icon is rejected, its input survives the response, and correcting it
  saves successfully. Screenshots are stored in `runs/smoke/screenshots/editor-*.png`.
- Remaining coverage gap: independent remote clients and simultaneous authoring on a
  normal dedicated server. The editor was exercised using the default theme.

## Initial in-game visual editor (2026-10-23)

- Operators can create chapters and quests, move and select nodes, link prerequisites,
  edit built-in tasks and rewards, and undo/redo changes. Config files are validated
  before writing and replaced files are backed up. IDs cannot be renamed.
- Fixed unfinished text and focus being lost on sync/resize, locked chapters being
  inaccessible while editing, closing before keyboard movement was saved, and
  acknowledgements of other edits discarding pending keyboard movement. Narrow layouts
  use full-width flag buttons and keep feedback above the canvas controls.
- `gradlew.bat build`: passed with 73 JUnit tests, zero failures. Editor tests cover file
  ownership, creation, updates, deletion, validation, backups, undo/redo, external-file
  conflict protection, and clearing history on reload.
- `gradlew.bat runGameTestServer`: all 5 required tests passed.
- `gradlew.bat runSmokeClient`: passed against an actual integrated server. The editor
  scenario creates a chapter and quests through widgets, preserves a focused draft
  through a state sync and resize, saves on Enter and closing, nudges nodes, undoes/redoes,
  adds item and XP rewards, adds a task and edits its numeric count, and creates a linked
  quest with Shift-add. It removes its own content before exiting.
- Screenshots under `runs/smoke/screenshots/editor-*.png` were visually reviewed at
  1280×800 and 854×480. Development smoke classes are excluded from the release JAR.
- Still unverified: simultaneous editing by two independent clients on a dedicated
  server, non-Windows platforms, large catalogues, and extension-specific editor fields.
  Exact region coordinates, chapter prerequisites and custom icon sprites use JSON.

## Minimal quest book and theme system (2026-10-22)

- The book was redesigned around a minimal layout (chapter list, full-screen tree, floating
  title and controls) and the theme system now controls layout anchors, node geometry, panel
  sprites, fonts and colours. All fixed wording moved to `iwantquests.ui.*` language keys.
  Before/after screenshots: `docs/design/before-after-*.png`.
- `gradlew.bat build`: passed with 49 JUnit tests, including new checks that every language key
  used in code exists and that the bundled theme files are valid.
- `gradlew.bat runGameTestServer`: all 5 required tests passed.
- `gradlew.bat runSmokeClient` on a fresh world, then again on the same world: both passed
  (real pickup, sync, claim, duplicate-claim rejection, team creation, restart persistence).
  The run also rendered the `classic` and `midnight` themes (`docs/design/after/theme-*.png`)
  and the screenshots were inspected by eye.
- Still unverified, unchanged from before: two independent human clients on a dedicated server,
  Linux and macOS, large modpacks, and custom fonts and nine-slice panel sprites (the code
  paths exist but no bundled theme exercises them).


## Quest book visual redesign (2026-10-19)

- Flat modern theme: dark slate panels, teal accent, flat buttons, dot-grid canvas.
- Quest nodes are 32px flat shapes (square, octagon, large octagon) with status
  colours, a check badge when complete, a padlock when locked, a bobbing marker when
  rewards are ready, hover and selection rings, and a progress bar under in-progress
  nodes. Sprite shapes still work through `flat_nodes: false` or custom shapes.
- The quest window has one title bar (navigation, type, position) and the title is
  shown once below it, with a status chip, task and reward cards and a clear
  claim button. It uses the whole window height, so fewer quests need scrolling.
- The chapter list defaults to a compact icon rail on windows under 520 GUI pixels
  wide, or when there is only one chapter. The expanded list shows the full title and
  progress. The header no longer truncates the subtitle; it hides it when it won't fit.
- The Team screen gained top padding and flat buttons.
- Checked with real client screenshots (`runs/smoke/screenshots`): tree, expanded and
  collapsed chapter list, completed and locked quest windows, narrow window, team screen.

## Quest book and editable configuration update (2026-10-19)

- `gradlew.bat build runGameTestServer`: passed, with 45 JUnit tests and all five
  required GameTests (including the harness self-test).
- The new `config_quests` GameTest uses actual game resources and registries. It
  verifies enabled defaults, an empty book with defaults disabled, a custom quest
  loaded from a temporary config directory, and re-reading an edited file.
- Three new file-loading tests cover creating the directory, nested UTF-8 JSON,
  ignoring non-JSON files, and errors that name the broken file.
- Real client screenshots verify the centred task/reward window, expanded chapter
  list, plain straight connections, and scrollable details. The smoke harness also
  exercises chapter collapse and a 640×480 window for the stacked layout.
- Custom file loading and sample removal preserve server-side validation, the
  900 KB catalogue limit, team progression and personal reward authority.

The visual authoring editor and freely positioned chapter decorations remain
unimplemented. Custom chapter backgrounds and resource-pack node shapes continue
to work through the existing authoring format.

Executed on Windows x64 with Amazon Corretto Java 25.0.3, Minecraft 26.2,
NeoForge 26.2.0.90, Gradle 9.2.1 and ModDevGradle 2.0.148.

## Commands and outcomes

- `gradlew.bat createMinecraftArtifacts`: successful; downloaded and built the
  actual patched 26.2 development classes/sources.
- `gradlew.bat compileJava`: successful after fixing changed item-tag and narrator APIs.
- `gradlew.bat build`: successful, with 20 JUnit tests passing and none skipped.
- `gradlew.bat build runGameTestServer`: successful; all 4 required tests passed
  (3 IWantQuests runtime tests plus the harness's built-in self-test).
- `gradlew.bat runClient`: mod loaded and the client reached its initial screen.
- `gradlew.bat runSmokeClient`: successful on a fresh development world and on
  subsequent restarts; assertions and screenshots were checked, not just exit codes.

Earlier failures were corrected rather than ignored: API name changes, an explicit
Gson dependency for plain-JVM tests, unnegotiated GameTest player payload channels,
and the vanilla item entity restoring its stack count after discard.

## JUnit coverage

Actual sample JSON parsing; useful invalid-definition errors; prerequisite locking;
no spill into newly unlocked successors; retroactive configuration; complete
five-quest progression; two-player aggregation (including 8 + 12 wheat); separate
teams; independent reward claims; repeated claim rejection; inventory-preflight
rejection; team switching; owner transfer; solo restoration; expired invitations;
progress/claims JSON round trips; incompatible-definition reconciliation; targeted
submission; dimension/region/kill/manual logic; possession snapshots; and
cursor-centred zoom invariance.

## Real Minecraft GameTests

`playable_loop` loads the real quest definitions and item tags. Two mock server
players perform real item pickups and take actual wooden-pickaxe, red-bed, and
iron-pickaxe recipes from CraftingMenu result slots. Assertions cover all five
completions, per-player bread and 250 XP, duplicate claim rejection, full-inventory
reward preflight, and authoritative per-player snapshots.

`disk_persistence` writes QuestSavedData through Minecraft's SavedDataStorage,
closes it, constructs a new storage instance and reloads team membership,
completion, and individual claims from disk.

`registry_validation` checks useful rejection of nonexistent item/entity IDs
against actual Minecraft registries and accepts real identifiers.

These tests run in the physical DEDICATED_SERVER distribution and therefore verify
that loading the common mod does not require client rendering classes.
Mock players do not negotiate the mod channel, so this harness alone does not
prove two-client network delivery.

## Real client/server smoke test

The client uses its actual integrated server, creates a separate test world,
performs a genuine 16-log ground pickup, receives initial and incremental payloads,
verifies unlock state, renders the quest book, claims bread through the serverbound
payload, and checks the returned claimed state. A repeated network claim is
rejected and the client inventory still contains exactly eight bread.

The next process launch opens the same saved world and verifies that completion,
claim flags, and bread survive an actual client/integrated-server restart.
Screenshots of the tree, details, claimed state and requested GUI-scale change are
stored under `runs/smoke/screenshots`. The screenshots were visually inspected;
chapter wrapping, detail clipping, reward previews and narrow-screen layout were
corrected. Actual scale is limited by Minecraft's minimum GUI dimensions.

## Still unverified

- Two independently connected human clients on a normal dedicated server.
- A normal dedicated server process restart with real remote-player files; disk
  reload and integrated-server process restart are verified separately.
- Multiplayer latency/disconnect races during inventory submissions and claims.
- Real gameplay detection for every custom objective adapter beyond sample pickup
  and crafting; remaining domain types have automated logic tests.
- Large catalogue profiling, 50–100-mod compatibility, Linux, macOS and Apple Silicon.
- Power loss halfway through a multi-file save. Minecraft saves are not a database
  transaction; backups remain appropriate for live worlds.

## Not implemented

Visual authoring editor, team GUI, repeatable quests, advanced reward types, custom
item components in rewards, every inventory-transfer source for Obtain, translations
beyond English, and final marketplace listing/artwork. These are not claimed as
completed milestones. The next priorities are dedicated two-client regression
checks, then administrator-only draft editing and data-pack export.
