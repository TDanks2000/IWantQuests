# IWantQuests

### Your quests. Your progression. Everyone gets rewarded.

IWantQuests is a standalone questing mod for players, servers, and modpack creators. Build branching quest trees, work towards goals together, and let every teammate claim their own rewards.

## Progress together, claim for yourself

Team members contribute to shared quest objectives. Gathering resources, crafting equipment, and reaching milestones all help your group move forward. Rewards are personal: each player claims their own items and experience once per quest.

Create a team, invite friends, and manage members from the quest book. Prefer to play solo? You start with your own progression automatically.

## A clear view of your next goal

Explore a quest tree with pan and zoom, chapter navigation, branching prerequisites, progress bars, and clear locked, completed, and ready-to-claim states. Open a quest to see its description, objectives, and rewards, or jump straight to a prerequisite.

The book is minimal by design: a chapter list, the quest tree, and a few small controls. Modpack creators can restyle all of it with resource-pack themes: colours, node shapes, panel textures, fonts, wording, and where the chapter list, title, and buttons sit. Three themes are included.

## Made for your modpack

Write quests in plain JSON, using config files or data packs. Define your own chapters, objectives, and rewards without changing the mod JAR. Modded items and item tags are supported.

Rewards can be items, experience, loot tables, commands, weighted random rewards, or a choice of several. Mark a quest repeatable and it starts over once every teammate has claimed it.

Available objective types:

- **Obtain:** collect items through pickups or crafting.
- **Possess:** hold items across your team's inventories.
- **Craft:** produce items through crafting.
- **Kill:** defeat specified entities.
- **Dimension:** visit a dimension.
- **Region:** reach a defined area.
- **Submit:** hand in items.
- **Manual:** mark a task complete yourself.

Prefer to build in-game? Enable `editorEnabled` in the server config and authorised creators can create chapters and quests, arrange the tree, link requirements, and edit tasks and rewards from the book. Edits stay in a private draft until you review and publish them.

Run `/reload` to update quest definitions for connected players. Invalid definitions are rejected while the previous catalogue stays active. Quest definitions are supplied by the server, so players do not need separate copies of your quest files.

A small sample chapter is included to get you started. Disable it with `includeDefaultQuests = false` in `config/iwantquests-common.toml`, then place your own JSON files in `config/iwantquests/quests/`.

## Getting started

1. Install **Minecraft Java 26.2**, **NeoForge 26.2.0.90 or newer within 26.2**, and **Java 25**.
2. Put IWantQuests in the `mods` folder on **both the client and server**. For singleplayer, install it in your client instance.
3. Press **K** or run `/iwantquests` to open the quest book.
4. Try the sample chapter, or add your own quests and run `/reload`.

No FTB libraries or additional gameplay mods are required.

## Status and limits

**1.0.0** is the first stable release. It includes the full quest, team, and reward loop, JSON and data-pack authoring, live `/reload`, and an in-game visual editor for authorised creators.

Not included: translations beyond English, and repeatable quests that use hold, location, visit, or click tasks. Quest catalogues are limited to about 12 million characters of JSON. Testing so far covers Windows with the integrated and dedicated server; Linux, macOS, and very large modpacks have not been tested by the maintainers.

Found a problem? Leave a comment with your Minecraft and NeoForge versions, what happened, and the relevant log details.

