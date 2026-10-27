# IWantQuests

A standalone questing mod for **Minecraft Java 26.2** on **NeoForge 26.2.0.90** (Java 25).
Quests are plain JSON files, so modpack makers can build their own chapters, objectives
and rewards without writing any code. No FTB libraries or other gameplay mods are required.

- **Cooperative progress.** Everything a team does counts for the whole team.
- **Personal rewards.** Every player claims their own rewards, once per quest. Rewards can be items,
  experience, loot tables, commands, weighted random rewards or a choice of several.
- **Repeatable quests** that start over once the whole team has claimed.
- **A quest tree you can pan and zoom**, with chapters, branching prerequisites,
  progress bars and ready-to-claim markers.
- **Easy to customise.** Edit JSON, run `/reload`, and the book updates. No JAR editing.
- **In-game visual editor.** Authorised creators can create chapters and quests, arrange the tree,
  link requirements, and edit tasks and rewards from the book.

> Status: **1.0.0**, the first stable release. See [Known limits](#known-limits).

**Contents**
[Install](#install) ·
[Playing](#playing) ·
[Teams](#teams) ·
[Visual editor](#visual-editor) ·
[Make your own quests (5 minutes)](#make-your-own-quests-in-5-minutes) ·
[Quest file reference](#quest-file-reference) ·
[Objectives](#objectives) ·
[Rewards](#rewards) ·
[Layout and look](#layout-and-look) ·
[Shipping in a modpack](#shipping-in-a-modpack) ·
[Troubleshooting](#troubleshooting) ·
[Config and commands](#config-and-commands) ·
[Build from source](#build-from-source)

---

## Install

The mod must be installed on **both the client and the server**. Singleplayer needs it once.

1. Create a **Minecraft 26.2** instance and install **NeoForge 26.2.0.90** with a **Java 25** runtime
   (in Prism Launcher: edit the instance, then *Version*, then *Install NeoForge*).
2. Put `iwantquests-1.0.0.jar` in the instance's `mods` folder.
3. On a dedicated server, put the same JAR in the server's `mods` folder.
4. Launch the game. IWantQuests generates its config on first start.

Quest definitions live on the **server** (or in your singleplayer instance) and are sent to
players when they join. Players do not need copies of your quest files.

---

## Playing

Press **K** (rebindable under *Controls*) or run `/iwantquests` to open the quest book.

| Do this | To |
|---|---|
| Click a chapter icon on the left | Switch chapter. Locked chapters show what they need. |
| Drag empty canvas | Pan the tree |
| Scroll wheel | Zoom around the cursor |
| **Fit** (or press **F**) | Frame the whole chapter |
| Click a quest | Open its window: description, tasks, rewards |
| `<` `>` in the quest window, or the arrow keys | Move to the previous or next quest |
| Click a name under *Requires* | Jump to a prerequisite quest |
| **Claim rewards** | Collect your rewards for a completed quest |
| **Check** (or `/iwantquests check`) | Re-scan your inventory for *possess* tasks |
| **Esc** | Close the quest window, then the book |
| `<` / `>` button (bottom of the chapter list) | Collapse the list to icons, or expand it to names |
| **Team** button (top right) | Manage your team |
| **Check**, **-**, **+**, **Fit** (bottom right) | Re-scan inventory, zoom out, zoom in, frame the chapter |

### Reading the tree

| What you see | It means |
|---|---|
| Teal edge | Available: you can work on it now |
| Grey edge and padlock | Locked: finish its prerequisites first |
| Green edge and check | Complete and claimed |
| Gold edge and bobbing marker | Complete, rewards waiting for you |
| Bar under a node | Progress on an available quest |
| Circle, rounded square, hexagon | Task, goal, challenge |

The "2 rewards to claim" notice at the top right opens the first quest waiting for you when clicked. A gold dot on a chapter in the list shows where rewards are waiting.

**Search** or **Ctrl+F** finds quests across chapters by title, description, ID or task target.
Filter results by available, completed, unclaimed rewards or pinned quests. Hidden locked
quests stay hidden. Open a result to jump to its chapter and details. Use **Pin to HUD**
in quest details to track up to three quests while playing. Pins are saved separately for
each singleplayer world or server; `showPinnedTracker` controls HUD visibility.

### Rewards

- Tasks are **shared by the team**; rewards are **yours alone**. Each teammate claims their own,
  once per quest, even if they joined the team after it was completed.
- Item rewards need room in your **main inventory**. If it is full the whole claim is refused
  (including XP) so nothing is lost: free some space and claim again.
- Some quests offer a **choice**: pick one of the listed rewards, then claim. Others roll a
  **random** reward or a **loot table** when you claim.
- A **repeatable** quest starts over once every member of your team has claimed it, so you can earn
  its rewards again.

---

## Teams

Everyone starts solo. Open the **Team** screen from the Team button at the top right of the book, or use commands.

```text
/iwantquests team create Explorers     (founder, then invite friends)
/iwantquests team invite PlayerB
/iwantquests team accept               (PlayerB, within 5 minutes)
```

The Team screen lists members and who is online, shows invitations with a countdown,
and lets you create a team, invite online players, cancel invitations, remove members and
leave, all without commands.

- Creating a team **copies the founder's solo progress** into it.
- Joining a team uses **that team's** progress. Your old solo progress is kept and comes
  back if you leave.
- Reward claims belong to the player, not the team, and are permanent for each quest ID.
- Only the owner can invite. If the owner leaves, ownership moves to another member.

---

## Visual editor

Editing is **disabled by default**, including in singleplayer worlds with cheats enabled.
To author quests, set `editorEnabled = true` in `config/iwantquests-common.toml` on the
server (or in your singleplayer installation). Operators with level 2 permissions can
then open the book with **K** or `/iwantquests` and click **Edit**. To give a trusted
creator access without operator privileges, add their full UUID to `editorPlayers`.
Creative mode and team ownership do not grant editor access. The server checks every
change and updates access when permissions or settings change, closing a revoked editor.
Set `editorEnabled = false` before distributing your modpack to keep the authoring UI hidden.

Click empty canvas to inspect the current chapter. **New chapter** creates a chapter;
the inspector lets you change its title, icon, sort order, theme and background.

Edits now save to a **private draft** under `config/iwantquests/drafts/<player UUID>/`.
Players continue using the published book. **Preview** shows the draft as a fresh player,
with gameplay actions disabled. **Review** lists added, changed and removed content and
progress/reward warnings. **Publish…** asks for confirmation, validates the batch, backs
up affected files, and updates the live book. Saved drafts survive restarting the game.
If published quests change after your draft began, publication is blocked to protect
newer work. Copy any work you want to keep, discard the stale draft through Review, then
paste into a new draft. See [creator workflows](docs/CREATOR_WORKFLOWS.md).

| Tool or shortcut | Does |
|---|---|
| **Select** / **V** | Select a quest; drag it to move it |
| **Shift-click** / **Shift-drag** | Select several quests individually or with a rectangle |
| **Ctrl+A** | Select all quests in the current chapter |
| **Add** / **A**, then click empty canvas | Create a quest |
| Right-click empty canvas | Create a quest with any tool |
| **Shift-click** with Add | Create a quest requiring the selected quest |
| **Link** / **L** | Click the prerequisite, then the quest requiring it; repeat to unlink |
| **G** | Toggle snapping to an 8-unit grid; hold **Alt** during a drag to toggle it temporarily |
| Arrow keys / **Shift+arrow** | Move selected quests by 8 units / 1 unit |
| **Delete** | Delete selected quests |
| **Ctrl+Z** / **Ctrl+Y** or **Ctrl+Shift+Z** | Undo / redo |
| **Ctrl+D** | Duplicate selected quests with new IDs and remapped internal links |
| **Ctrl+C** / **Ctrl+V** | Copy selection and paste into the current chapter |

Select a quest and use **Details**, **Tasks**, and **Rewards** in the inspector to reach
the fields you need. The tool palette always offers **New chapter**, and contextual hints
explain selecting, linking and creating quests. Scroll within a tab for longer lists.
Item fields accept item
IDs; task targets also accept tags where supported. **Held** uses your main-hand item.
**Browse items…** and **…** beside item task targets open a searchable item/tag picker.
Tags show example matching items. **Template** saves a selection to the server's reusable
template library; **Quest templates** places a saved group in the current chapter.
Region tasks have **Set around me** to set a box around your current position.
**Same column** and **Same row** align a group of selected quests.

The inspector shows **Unsaved text**, **Saving...**, **Saved**, or **Edit rejected**.
Click **Save** or press **Ctrl+S** to apply unfinished text. Text also saves when you
press **Enter** in a single-line field, leave the field, switch tabs, or close the
book. **Esc** while typing discards that field's unfinished change. Other changes save
immediately within your draft. Invalid changes leave the saved draft intact and retain
rejected input for correction. Only publishing updates the book for everyone online.

Only content in `config/iwantquests/quests/` can be changed. Bundled and data-pack content
is read-only, but you can add your own quests to those chapters. Locked chapters and hidden
quests remain visible in the editor. New chapters go into `editor.json`; new quests join
their chapter's config file, or `editor.json` for a read-only chapter. Set `editorNamespace`
in `config/iwantquests-common.toml` to choose the namespace of new chapters. IDs stay stable
when you rename content, preserving progress and personal claims.

The server keeps the last 50 changes in a shared undo history until restart or a successful
reload. Undo affects the latest edit by any operator. Replaced files are backed up under
`config/iwantquests/backups/`, keeping the latest 100 copies. Undo and redo refuse to overwrite
files changed outside the editor. JSON remains available for fields without editor controls,
including chapter prerequisites, custom sprite icons and exact region coordinates.

---

## Make your own quests in 5 minutes

You only need a text editor. These steps work for a modpack, a server and singleplayer.

**1. Launch once.** This creates `config/iwantquests-common.toml`.

**2. Remove the sample quests (optional).** In `config/iwantquests-common.toml` set:

```toml
includeDefaultQuests = false
```

The same switch is in-game under *Mods, IWantQuests, Config*. With it off and no quest files
of your own, the book is empty.

**3. Create a quest file.** Make `config/iwantquests/quests/my_quests.json`
(the folder is created for you on launch). Start from the ready-made example:
copy [`examples/starter/config/iwantquests/quests/welcome_chapter.json`](examples/starter/config/iwantquests/quests/welcome_chapter.json)
into that folder. It has seven quests showing every objective type, branching and merging
prerequisites, all three node shapes, an optional quest and a colour-coded title.
Subfolders work, and every `.json` file in the folder is loaded.

**4. Reload.** Run `/reload` as an operator (or restart). Open the book with **K**.

**5. Edit and repeat.** Change titles, positions, counts or rewards, save, run `/reload`.
The book updates for everyone online without a restart.

If something is wrong, `/reload` **rejects the whole change and keeps the old quests**.
The server log (`logs/latest.log`) names the file and quest at fault. See
[Troubleshooting](#troubleshooting).

### The smallest possible quest file

```json
{
  "chapters": [
    { "id": "mypack:basics", "title": "Basics", "icon": "minecraft:oak_sapling", "order": 0 }
  ],
  "quests": [
    {
      "id": "mypack:first_logs",
      "chapter": "mypack:basics",
      "title": "First Logs",
      "description": "Gather some wood.",
      "icon": "minecraft:oak_log",
      "x": 0,
      "y": 0,
      "prerequisites": [],
      "objectives": [
        { "id": "logs", "type": "obtain", "target": "#minecraft:logs", "count": 8,
          "description": "Collect 8 logs" }
      ],
      "rewards": [
        { "type": "item", "target": "minecraft:bread", "count": 4 }
      ]
    }
  ]
}
```

To add a second quest that unlocks after the first, add another object to `quests` with
`"prerequisites": ["mypack:first_logs"]` and an `x` about 72 units to the right.

---

## Quest file reference

A file holds two arrays: `chapters` and `quests`. All files (config folder, data packs and the
bundled sample) are merged into one catalogue. **IDs must be unique across everything**, and
each chapter should be declared once and referenced by quests in any file.

IDs look like `namespace:name`, using lowercase letters, digits and `_ . -`. Use your pack's
name as the namespace, for example `mypack:wood`. **Keep IDs stable**: progress and reward
claims are stored against them.

### Chapter fields

| Field | Required | Meaning |
|---|---|---|
| `id` | yes | Unique ID, such as `mypack:basics` |
| `title` | yes | Name shown in the chapter list and above the tree |
| `icon` | yes | Item ID shown on the chapter tab (modded items work) |
| `order` | no | Sort position in the chapter list. Lowest first. |
| `subtitle` | no | Short text under the title (up to 128 characters) |
| `prerequisites` | no | Quest IDs that must be complete before the chapter tab unlocks |
| `theme` | no | A client theme ID. See [themes](docs/THEMES.md). |
| `background` | no | A texture ID drawn behind the tree |

### Quest fields

| Field | Required | Meaning |
|---|---|---|
| `id` | yes | Unique ID |
| `chapter` | yes | The chapter ID this quest belongs to |
| `title` | yes | Name of the quest |
| `icon` | yes* | Item ID drawn on the node. *Optional if `iconTexture` is set. |
| `prerequisites` | yes | Quest IDs that must all be complete first. Use `[]` for none. Cycles are rejected. |
| `objectives` | yes | One or more tasks. See [Objectives](#objectives). All must be done to complete the quest. |
| `rewards` | yes | Zero or more rewards. Use `[]` for none. See [Rewards](#rewards). |
| `description` | no | Text shown in the quest window |
| `subtitle` | no | Short line under the title (up to 128 characters) |
| `x`, `y` | no | Position on the canvas. See [Layout](#layout-and-look). |
| `shape` | no | `task` (default), `goal` or `challenge` |
| `size` | no | Node scale from `0.5` to `3.0`. Default `1`. |
| `iconTexture` | no | A GUI sprite ID drawn instead of the item icon |
| `optional` | no | `true` to leave this quest out of the chapter's progress total |
| `hidden` | no | `true` to hide the node until it unlocks (or is completed) |
| `retroactive` | no | `true` to collect progress while locked, completing only once unlocked |
| `repeatable` | no | `true` to start over once every team member has claimed (see [Repeatable quests](#repeatable-quests)) |

`title`, `description` and `subtitle` accept Minecraft colour codes written with the section
sign, for example `"§6Gold title§r"`.

### Branching and merging

`prerequisites` is a list, and **all** of its quests must be complete:

```text
read_guide ── wood ──┬── tools ──┬── nether
                     └── hunt  ──┘
```

The example's `mypack:nether` has `"prerequisites": ["mypack:tools", "mypack:hunt"]`, so it needs both.

---

## Objectives

Each objective needs a unique `id` within its quest (a short word is fine), a `type`, and a
`count` of at least 1. `description` is the line the player reads, so always write one.
A quest may have several objectives, and every one must be finished.

`target` is an exact ID such as `minecraft:iron_pickaxe`, or an **item tag** such as
`#minecraft:logs` for "any log". Modded items and tags work the same way.

| Type | `target` | Counts when | Example |
|---|---|---|---|
| `obtain` | item or `#tag` | Anyone on the team picks up or crafts it. Items are **not** taken. | `{"id":"a","type":"obtain","target":"#minecraft:logs","count":16}` |
| `possess` | item or `#tag` | The team's combined inventories hold that many. Press **Check** to refresh. | `{"id":"a","type":"possess","target":"minecraft:diamond","count":5}` |
| `craft` | item or `#tag` | Taken out of a crafting result slot | `{"id":"a","type":"craft","target":"minecraft:furnace","count":1}` |
| `kill` | entity ID | A team member kills it | `{"id":"a","type":"kill","target":"minecraft:zombie","count":10}` |
| `dimension` | dimension ID | A member is in that dimension | `{"id":"a","type":"dimension","target":"minecraft:the_nether","count":1}` |
| `region` | none | A member is inside the box (see below) | see below |
| `submit` | item or `#tag` | The player presses **Submit items**, which **takes** the whole remaining amount | `{"id":"a","type":"submit","target":"minecraft:diamond","count":3}` |
| `manual` | none | The player presses **Mark complete** | `{"id":"a","type":"manual","count":1}` |

A region needs its dimension and an inclusive box:

```json
{ "id": "area", "type": "region", "count": 1, "description": "Reach the spawn area",
  "dimension": "minecraft:overworld",
  "minX": -32, "minY": -64, "minZ": -32, "maxX": 32, "maxY": 320, "maxZ": 32 }
```

**Which to choose**

- *Collect wood, ore, crops*: `obtain`. It rewards gathering, never takes items, and is easy to read.
- *Make the player keep something*: `possess`.
- *Make them pay or hand something in*: `submit`.
- *Anything the mod cannot detect* (read a book, build something): `manual`.

Notes: `obtain` only sees real pickups and crafting, not `/give` or chest transfers.
`possess` is checked on join, pickup, closing a container and **Check**, not every tick.
Only quests that are *available* (unlocked) collect progress unless they set `"retroactive": true`.

---

## Rewards

```json
"rewards": [
  { "type": "item", "target": "minecraft:bread", "count": 16 },
  { "type": "xp", "count": 100 }
]
```

| Type | Fields | Gives |
|---|---|---|
| `item` | `target` (item ID), `count` | The items, split into stacks automatically |
| `xp` | `count` | Experience **points** (not levels) |
| `loot_table` | `target` (loot table ID), `count` (rolls, 1-64) | Whatever the loot table drops, rolled when the player claims |
| `command` | `target` (command), `count` (times to run, 1-100) | Runs the command as the claiming player with full permission |
| `random` | `options`, `count` (picks, 1-16) | Picks from `options` by weight when the player claims |
| `choice` | `options` | The one option the player picks before claiming |

Give as many rewards as you like. They are delivered together or not at all.
Reward items use default item data; custom NBT or components are not supported yet.

Every reward can also have a `name` and an `icon` (an item ID) to control how it shows in the book.
Commands and loot tables have no natural label, so give them a `name`.

```json
"rewards": [
  { "type": "choice", "name": "Choose your prize", "options": [
      { "type": "item", "target": "minecraft:bread", "count": 16 },
      { "type": "xp", "count": 200 } ] },
  { "type": "random", "count": 1, "options": [
      { "type": "item", "target": "minecraft:bread", "count": 4, "weight": 70 },
      { "type": "item", "target": "minecraft:diamond", "count": 2, "weight": 5 } ] },
  { "type": "loot_table", "target": "minecraft:chests/spawn_bonus_chest", "name": "A bonus chest", "icon": "minecraft:chest" },
  { "type": "command", "target": "give {player} minecraft:emerald 2", "name": "2 Emeralds", "icon": "minecraft:emerald" }
]
```

- `options` hold ordinary rewards (`item`, `xp`, `loot_table`, `command`). `random` and `choice` cannot nest.
  `weight` (default 1) is an option's share of a random pick. The book lists the odds as percentages.
- A `choice` reward shows its options as buttons in the quest window. The claim button waits until every
  choice is made. The server checks the pick, and loot and random rewards are rolled on the server.
- `loot_table` rolls use the chest loot context at the player's position. A table that does not exist is
  reported when the player claims, not when the pack loads, because loot tables load separately.
- **Commands** run with owner-level permission as the claiming player, so `@s` is the player and `{player}` is
  their name. Command output is hidden. Because that is powerful, the in-game editor only lets **server
  owners (permission level 4)** add or change command rewards; everyone else must edit the quest files, which
  only the server's administrators can reach. Keep commands in packs you trust.
- Item and loot rewards need inventory room. Experience and commands do not.
- The in-game editor can add and edit `item`, `xp`, `command` and `loot_table` rewards. It keeps `random` and
  `choice` rewards intact but they are authored in JSON. See `examples/rewards`.

### Repeatable quests

Set `"repeatable": true` and a quest starts over for a team once **every current member has claimed** its
rewards, so a solo player can repeat it straight after claiming. Progress, and each member's claim for that
round, are cleared. A teammate who has not claimed yet keeps the rewards waiting, and the quest does not start
over until they do or leave the team.

To stop rewards being farmed, the rules are strict. A repeatable quest:

- may only use `craft`, `kill` and `submit` tasks. Holding items, being somewhere, visiting a dimension or
  clicking a button would simply finish it again at once, and picking items up can be farmed by dropping them;
- cannot be `retroactive`;
- cannot be required by another quest or chapter, because a quest that keeps resetting cannot stay unlocked.

Breaking a rule is reported when the pack loads. Repeat claims are stored with the team's progress, not in a
player's permanent claim list.

---

## Layout and look

### Positioning

`x` and `y` place the **centre** of a node on the canvas. `0, 0` has no special meaning; the
book frames the tree automatically. **`x` grows to the right and `y` grows downwards.**
One unit is one GUI pixel at 100% zoom, and a normal node is 32 units wide.

| Spacing | Result |
|---|---|
| About **72** apart horizontally, **48** vertically | Comfortable gaps for normal nodes |
| 64 or less | Nodes crowd each other |
| Quests with a bigger `size` | Leave extra room around them |

Tips: keep a main line (`y: 0`), and put side branches at `y: ±48`. Zigzagging `y` between
`0` and `48` makes a long chain easy to follow. Use **Fit** in the book to check your layout.

### Shape, size, icon

- `shape`: `task` (circle), `goal` (rounded square), `challenge` (hexagon). Themes can change these outlines. Use
  `challenge` sparingly for milestones.
- `size`: `1.5` makes a boss quest stand out; `0.75` shrinks a side quest.
- `iconTexture`: a custom sprite instead of an item, such as `mypack:quests/star_icon`,
  stored at `assets/mypack/textures/gui/sprites/quests/star_icon.png` in a resource pack.

### Themes

The whole look is controlled by a theme in a resource pack: colours, node outlines and sprites,
fonts, panel textures, and where the chapter list, title and buttons sit (or whether they show at
all). Set a chapter's `theme` to choose one. All fixed wording is in the language file, so it can be
reworded or translated by a pack. Players can also override the theme, blur and opacity in
`config/iwantquests-client.toml`. Three themes ship with the mod: `iwantquests:default`,
`iwantquests:midnight` and `iwantquests:classic`. See [docs/THEMES.md](docs/THEMES.md).

---

## Shipping in a modpack

1. Set `includeDefaultQuests = false` so players see only your quests.
   Set `editorEnabled = false` after authoring to keep the editor hidden for players.
2. Put your files in `config/iwantquests/quests/` and `config/iwantquests-common.toml`
   in your pack's **config overrides** (the `overrides/config` folder on CurseForge, Modrinth
   `.mrpack` and Prism exports). Players of your pack receive them with the pack.
3. **Servers need the files too.** Quest definitions come from the server's config folder.
4. Optional extras:
   - A **resource pack** with a custom theme or sprites (see [themes](docs/THEMES.md)).
   - **Data packs** are an alternative to config files, and are per world. They go in
     `<world>/datapacks` as `data/<namespace>/iwantquests/quests/<name>.json`. See the
     [example data pack](examples/creator-pack).

### Updating a live pack

Progress and reward claims are saved separately from the quest definitions, so most edits are safe.

- **Keep IDs**. Renaming a quest ID makes it a brand-new quest, and players could claim it again.
- Changing an objective's `type`, `target`, dimension or region resets that objective's progress.
  Changing only its `count` keeps progress.
- Removing a quest does not delete saved progress, so bringing it back restores it.
- Reward claims **never** reset, even if you change or remove the quest. (A `repeatable` quest resets by design.)
- Back up a live world before large changes.

Large catalogues are sent to players in parts, so packs with thousands of quests work. The whole catalogue
is limited to about 12 million characters of JSON, and players download it when they join.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| Book is empty | `includeDefaultQuests` is off and the folder is empty, or a reload failed. Check `logs/latest.log`. |
| `/reload` changed nothing | A file was invalid, so the whole reload was rejected. The log names the file and the problem. |
| Edited the `.toml` but nothing changed | Wait a moment for NeoForge to re-read it, run `/reload`, or restart. |
| "Invalid identifier" | IDs need `namespace:name`, lowercase, no spaces. |
| "missing prerequisite" / "missing chapter" | Quest `prerequisites` and `chapter` must match an `id` that exists somewhere. |
| "Duplicate quest" | Two files define the same ID, often a copied file left in the folder. |
| "Unknown objective type" | Check spelling: `obtain` `possess` `craft` `kill` `dimension` `region` `submit` `manual`. |
| A quest never completes | A tag target may be empty or misspelt. The server reports it by quest name. |
| Items from `/give` do not count | `obtain` counts pickups and crafting. Use `possess` and press **Check**. |
| Cannot claim | Main inventory is full. Make room and claim again. |
| Chapter shows "Locked" | It has `prerequisites`. Hover its tab to see which quests are needed. |

JSON mistakes to watch for: a trailing comma after the last item, missing quotes, and a missing
comma between objects. Any JSON validator finds these in seconds.

---

## Config and commands

### `config/iwantquests-common.toml` (server)

| Option | Default | Meaning |
|---|---|---|
| `includeDefaultQuests` | `true` | Load the bundled *A New Beginning* sample quests. Quest files in `config/iwantquests/quests` always load. |
| `editorNamespace` | `"custom"` | Namespace for chapters created in the visual editor. Quests use their chapter's namespace. |
| `editorEnabled` | `false` | Enable authoring for level 2 operators and trusted authors. Keep disabled in distributed packs. |
| `editorPlayers` | `[]` | Full UUIDs of trusted authors allowed to edit without operator privileges when editing is enabled. |

### `config/iwantquests-client.toml` (each player)

| Option | Default | Meaning |
|---|---|---|
| `backgroundOpacity` | `100` | Percentage of the theme's background opacity, 0 to 100 |
| `blur` | `true` | Blur the world behind the book, if the theme allows it |
| `theme` | `""` | Force a theme for every chapter, such as `iwantquests:midnight`. Empty uses each chapter's theme. |
| `showPinnedTracker` | `true` | Show up to three pinned quests on the HUD. Pins remain saved when hidden. |

### Commands

Team commands need no operator rights and only affect your own team.

| Command | Does |
|---|---|
| `/iwantquests` | Open the quest book |
| `/iwantquests check` | Re-scan your inventory and position |
| `/iwantquests team` | Show your team |
| `/iwantquests team create <name>` | Create a team from your progress |
| `/iwantquests team invite <player>` | Invite a player (owner only) |
| `/iwantquests team cancel <player>` | Withdraw an invitation |
| `/iwantquests team accept` / `decline` | Answer an invitation |
| `/iwantquests team kick <player>` | Remove a member (owner only) |
| `/iwantquests team leave` | Leave and restore your solo progress |
| `/reload` | Operator only. Re-read quest files and data packs. |

---

## More documentation

- [Quest authoring reference](docs/QUEST_AUTHORING.md): every field, in depth, and safe-update rules
- [JSON schema](docs/quest-pack.schema.json): for editor auto-completion
- [Themes](docs/THEMES.md): colours, sprites, node shapes and layout
- [Architecture and extensions](docs/ARCHITECTURE.md): custom objective and reward types for mod authors
- [Verification](docs/VERIFICATION.md): what has been tested

---

## Build from source

Use a 64-bit JDK 25. The Gradle wrapper pins Gradle 9.2.1 and ModDevGradle 2.0.148.

```powershell
# Windows, from the project folder
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat runGameTestServer
```

```sh
# Linux / macOS
chmod +x gradlew
./gradlew build
./gradlew runClient
./gradlew runServer
./gradlew runGameTestServer
```

Code style is enforced by Spotless (palantir-java-format). `build` fails on unformatted code; run
`./gradlew spotlessApply` to fix it.

The release artifact is `build/libs/iwantquests-1.0.0.jar`. Run folders are isolated under
`runs/`. Development clients have no paid-session login, so use an isolated local server with
`online-mode=false` for them. Public servers should use `online-mode=true`. For a dedicated
server, accept Minecraft's EULA in its run folder first.

`runSmokeClient` is an opt-in, development-only check. It creates its own world, performs a
real pickup, opens the book, claims a reward (including a choice reward from `examples/rewards`), exercises visual editing through the real
client/server connection, saves screenshots to `runs/smoke/screenshots` and exits. Editor
checks create and clean up their own chapter and quests. Running it twice also verifies
that progress survives a restart. It is excluded
from release JARs.

## Known limits

The quest, team, personal-reward, reload and editing loops are complete and covered by automated
tests, dedicated-server GameTests and an integrated-server client scenario. These are intentionally
outside 1.0:

- Reward types beyond item, experience, command, loot table, random and choice, such as custom
  components on reward items.
- Repeatable quests that use hold, location, visit or click tasks (see [Repeatable quests](#repeatable-quests)).
- Translations beyond English. The whole book is translatable through language keys; only
  English ships, and server chat messages are English only.
- Catalogues larger than about 12 million characters of quest JSON (tens of thousands of quests). Larger packs
  are rejected with a clear error.
- Extension-specific task and reward fields in the visual editor; use JSON for those.

Testing so far covers Windows, with the integrated server and a dedicated server. Linux, macOS,
two independent human clients on one dedicated server, and very large modpacks have not been
tested by the maintainers. If you hit a problem there, please report it with your logs.

License: All Rights Reserved. Dependency licences are in [third-party notices](THIRD_PARTY_NOTICES.md).
