# Quest authoring

## In-game visual editor

Enable `editorEnabled = true` in the server's `config/iwantquests-common.toml` to author
quests. Editing is disabled by default. When enabled, level 2 operators and trusted
authors listed by UUID in `editorPlayers` can open the quest book and click **Edit**
to create chapters, lay out quests,
link requirements, and edit built-in objectives and rewards. Changes save to a private
draft. Review and publish the draft to update `config/iwantquests/quests/` and connected players.
The server validates each change and supports undo/redo and automatic file backups.
Quest properties are grouped into **Details**, **Tasks**, and **Rewards** tabs. The
inspector shows save status; **Save** or **Ctrl+S** applies unfinished text. Disable
`editorEnabled` before shipping the pack. Players always retain normal quest-book access.
See [creator workflows](CREATOR_WORKFLOWS.md) for visual pickers, templates, duplication,
preview, publication and recovering a stale draft.
See the [visual editor guide](../README.md#visual-editor) for controls and file ownership.

The JSON reference below also covers fields with no visual control yet, such as chapter
prerequisites, custom icon sprites, and exact region bounds. Bundled and data-pack content
is read-only in the editor; copy it into a config file and disable its source to edit it.

## Editable config files (quickest)

1. Launch once to generate `config/iwantquests-common.toml`.
2. Set `includeDefaultQuests = false` to start with no bundled quests. In multiplayer,
   use the server's config. The setting is also in **Mods > IWantQuests > Config**.
3. Create `config/iwantquests/quests/my_quests.json` on the server (or your local
   installation for singleplayer). Subfolders are supported; only `.json` files load.
4. Save changes and run `/reload` as an operator. If the TOML file was edited externally,
   allow NeoForge's config watcher to read it before reloading; restarting also applies it.

For a starter file, copy the JSON from
`examples/creator-pack/data/creator/iwantquests/quests/cooperation.json` into that folder.
Edit titles, descriptions, positions, objectives and rewards in any text editor.
An empty folder with the bundled content disabled gives a completely empty book.
You can also use `{"chapters":[],"quests":[]}` for an empty file.

Config files and data-pack files merge, so IDs must remain unique across both.
To edit the default chapter, disable the bundled content and copy
`src/main/resources/data/iwantquests/iwantquests/quests/a_new_beginning.json` into
this config folder. Keep the quest IDs to retain compatible progress and claims.
Disabling the bundled file also excludes data-pack overrides at its exact resource path;
use a different file path for your replacement when that setting is false.
Config files apply to every world on the installation. Invalid JSON or invalid quest
references reject the entire reload, leaving the previous live catalogue in place.
Removal and incompatible objective edits can discard associated team progress when
reconciled; personal reward claims remain attached to quest IDs. Back up live worlds
before removing or replacing published content.

## World-specific data packs

Place your data pack in `<world>/datapacks`. Files use:

```text
creator-pack/
  pack.mcmeta
  data/<your_namespace>/iwantquests/quests/<filename>.json
```

Each JSON file contains `chapters` and `quests` arrays. All files merge into one
catalogue; declare each chapter once and reference it from other files. IDs must
be unique across the catalogue. Standard data-pack priority applies: to override
the bundled chapter, use the exact path
`data/iwantquests/iwantquests/quests/a_new_beginning.json` in a higher-priority pack.
To remove the sample chapter, override that file with `{"chapters":[],"quests":[]}`.
Do not add an unchanged copy under another path: duplicate IDs are errors.

The example pack's metadata uses 26.2 data format 107.1. Copy
`examples/creator-pack` into your world's data packs directory, run `/reload` as
an operator, and check the server log. The book receives the updated definitions
and state automatically. Invalid packs fail the reload with an error; they are
not partially applied. Keep a world backup before changing a published quest pack.

## Chapter and quest fields

A chapter requires `id`, `title`, `icon`, and can have an integer `order`.
Icons are registered item IDs, including modded items. Chapter icons render in
the sidebar; quest icons render on tree nodes.

A quest requires `id`, `chapter`, `title`, `prerequisites`, `objectives`, and
`rewards`, plus `icon` or `iconTexture` (or both). Set `description`, `x`, and `y`
for readable content and tree layout.
Coordinates are logical canvas units, independent of screen size or GUI scale.
One unit is one GUI pixel at 100% zoom and a normal node is 32 units wide, so
space nodes at least 72 units apart horizontally and 48 vertically.
All prerequisite quests must complete before the quest becomes available.
Multiple prerequisites form branching/merging progression; cycles are rejected.

- `optional`: exclude the quest from required chapter progress totals.
- `hidden`: omit locked nodes until they unlock or complete.
- `retroactive`: accumulate gameplay contributions while locked, but complete only
  after prerequisites complete. Default `false`.
- `repeatable`: the quest starts over for a team once every current member has claimed its
  rewards. Only `craft`, `kill` and `submit` objectives are allowed, the quest cannot be
  `retroactive`, and no other quest or chapter may require it. Default `false`. See the
  README section "Repeatable quests".

Unknown extra JSON fields are currently ignored for forwards compatibility.
The schema is an authoring aid; runtime validation also checks loaded item/entity
registries, duplicate IDs, prerequisites, cycles, types, quantities, and icons.
Missing or empty item tags produce an explicit server error naming the quest.
Such an objective cannot advance until its tag is corrected.

## Presentation

All presentation fields are optional and do not change progression rules.

- `shape`: the node frame. `task` (default) uses the vanilla advancement task
  frame, `goal` uses the vanilla goal frame, and `challenge` uses the vanilla
  challenge frame. A namespaced ID such as `mypack:star` uses the GUI sprites
  `mypack:star_complete` and `mypack:star_incomplete`, or a shape a theme defines.
- `size`: node scale from `0.5` to `3.0`. Omit it or use `0` for `1.0`.
- `iconTexture`: a GUI sprite ID, such as `mypack:quests/star_icon`, drawn instead
  of the item icon. The sprite lives at `assets/<ns>/textures/gui/sprites/<path>.png`.
  When `iconTexture` is set, `icon` is optional.
- `subtitle`: up to 128 characters shown under the title, on quests and chapters.
- Quest `title`, `description`, and `subtitle` accept Minecraft `§` formatting codes,
  such as `§6Expedition§r`.
- Chapter `theme`: a client theme ID, such as `iwantquests:default` or `mypack:dark`.
  Chapter `background`: a texture ID drawn behind the chapter's tree.
- Chapter `prerequisites`: quest IDs that must be complete before the chapter tab
  unlocks. This is visual gating only; each quest still uses its own prerequisites.

Data packs choose a theme by ID, but the theme JSON and its textures belong in a
resource pack, not a data pack. Theme definitions live at
`assets/<ns>/iwantquests/themes/<name>.json`, and sprites live under
`assets/<ns>/textures/gui/sprites/`. See `docs/THEMES.md` for the theme format.

## Objectives

Every objective needs a stable local `id`, a `type`, and a positive `count`.
Use `description` to explain the task. Item selectors are exact registered IDs,
such as `minecraft:iron_pickaxe`, or item tags, such as `#minecraft:logs`.

| Type | Meaning and detection |
| --- | --- |
| `obtain` | Cumulative successful ground pickups and crafted outputs while available; does not consume items. Picking up the same dropped items again counts as another collection event. |
| `possess` | Team sum of each member's last checked inventory snapshot. Rechecking the same stack does not add progress. Explicit Check, join, pickup and container-close events refresh the snapshot. Offline members retain their last checked snapshot. Once completed, completion is latched. |
| `craft` | Item taken from the vanilla crafting result slot; pickups alone do not count. Modded workstations must emit NeoForge's crafting event to participate. |
| `kill` | Entity death attributed to the contributing server player; exact entity ID in `target`. Cancelled deaths are ignored. |
| `dimension` | Presence in the dimension named by `target`, on login/change and once per second when this type exists. |
| `region` | Enter inclusive XYZ bounds in `dimension`. Checked once per second when regions exist. |
| `submit` | Server checks inventory and consumes the entire remaining quantity when the Submit button is pressed. Partial submissions are not supported. |
| `manual` | Explicitly author-enabled checkbox, completed through its Mark complete button. |

`region` uses `dimension`, `minX`, `minY`, `minZ`, `maxX`, `maxY`, and `maxZ`.
`manual` does not need a target. For one checkbox, set `count` to 1.
Submitted items advance only the chosen objective; matching objectives in other
quests do not receive free contributions. Possession differs from collection:
a player's current checked quantity replaces their previous snapshot, rather
than adding the full quantity again.

## Rewards

Rewards are a list, allowing item rewards and XP together:

```json
[
  {"type":"item","target":"minecraft:bread","count":16},
  {"type":"xp","count":100}
]
```

Working examples of every reward type and a repeatable quest are in `examples/rewards`.

Besides `item` and `xp`, rewards can be `loot_table`, `command`, `random` and `choice`; see the
README section "Rewards" for fields and examples. A reward may have a `name` and `icon` for display.
`random` and `choice` hold `options` (ordinary rewards, optionally with a `weight`); the player's
choice is validated and any random roll happens on the server.

Command rewards run with owner-level permission as the claiming player, so the in-game editor
accepts new or changed commands only from server owners (permission level 4).

Every teammate can claim once per quest ID. XP is points, not levels.
Main-inventory capacity is preflighted for all items together. A full inventory
rejects the entire claim, including XP, so the player can free space and retry.
Items never silently disappear or drop as a fallback. Current item rewards use
default item components; custom NBT/components are not exposed in JSON yet.

## Safe content changes

Progress and reward claims are stored independently of definitions in the world.
Removed quest data is retained, so a temporarily disabled pack does not destroy
progress. Use stable quest and objective IDs. Changing an objective's type,
target, dimension, or bounds invalidates that objective's old counter; changed
quantities are compared against retained progress. Prerequisite changes are
reconciled before rewards can be claimed. Reward claims never reset when a quest
changes, is removed/reintroduced, or the player changes team.

Use a new quest ID for intentionally new content. Do not reuse a retired ID for
an unrelated quest. Counts are capped at the configured goal; increasing a goal
cannot reconstruct previously discarded excess contributions.

Definitions are sent to each player when they join or after a reload, split into parts of
about 120,000 characters, so a catalogue is not limited by the network packet size. The
complete catalogue is limited to 12 million characters of JSON. Progress updates send only
changed quest views and directly affected unlocks; they do not resend all definitions.
