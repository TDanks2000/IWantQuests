## IWantQuests 1.0.0

First stable release for Minecraft Java 26.2 and NeoForge 26.2.0.90 or newer within 26.2. Requires Java 25 and installation on both client and server.

### New since 0.2.0-beta
- **More reward types.** Command, loot table, random (weighted) and choice rewards join items and experience. Players pick their option for a choice reward in the quest window, and the book shows the odds of random rewards. Rewards can have their own name and icon.
- **Repeatable quests.** A repeatable quest starts over once every team member has claimed it. Only craft, kill and submit tasks are allowed, so a repeat cannot be farmed by standing still or dropping and picking up items.
- **Large catalogues.** Quest definitions are sent in parts, so catalogues are no longer limited to about 900 KB. The new limit is about 12 million characters of JSON.
- **In-game visual editor** for chapters, quests, links, tasks and rewards, with undo/redo. Authoring is off by default; enable `editorEnabled` on the server.
- Private drafts with review, conflict detection, backups and explicit publishing, so editing never disturbs live progress.
- Reusable quest templates, copy and paste, quest search, pinned quests and an on-screen tracker.
- Item and tag choosers in the editor.

### Safety
- Command rewards run with full permission as the claiming player. The in-game editor only lets server owners (permission level 4) add or change them, and players never receive command text.
- A failed reward delivery no longer uses up the player's claim; they can claim again.
- Unexpected errors in quest actions or editor requests are logged on the server and reported to the player instead of reaching the network layer.
- The network protocol is now version 1.0.0. 1.0.0 clients and servers do not interoperate with 0.x betas. Existing worlds and quest files keep working.

### Known limits
Translations beyond English are not included, and server chat messages are English only. Repeatable quests cannot use hold, location, visit or click tasks. Windows is the only platform tested by the maintainers; independent multiplayer, Linux, macOS and very large modpacks have not been tested.

---

## IWantQuests 0.2.0-beta

Beta release for Minecraft Java 26.2 and NeoForge 26.2.0.90 or newer within 26.2. Requires Java 25 and installation on both client and server.

### Quest book redesign
- A minimal layout: the quest tree fills the screen, with a chapter list on the left and a few floating controls. The heavy header bar and boxed zoom row are gone.
- Circle, rounded-square, and hexagon nodes with status-coloured edges, a glow for ready-to-claim rewards, and a progress bar under in-progress nodes.
- Chapters show a thin progress line and a gold dot when rewards are waiting.
- A cleaner quest window with tasks and rewards side by side, and feedback messages that fade on their own.
- The Team screen matches the new look and follows the chapter's theme.

### Full customisation for modpack creators
- Themes can move the chapter list (left, right, or hidden), the title, and the button groups, or bring back a top bar.
- New theme options: nine-slice panel and sidebar sprites, node geometry and scale per shape, custom fonts, arrowheads, node titles, outlines, and more colours.
- Every fixed word in the book is a language key, so packs can reword or translate it.
- A third bundled theme, `iwantquests:classic`, shows the vanilla-style look. See docs/THEMES.md.

### Carried over from 0.1.0-alpha
- Cooperative team progression, personal reward claims, eight objective types, item and XP rewards, JSON quests in config folders or data packs, live /reload with validation.

### Known limits
Quest content is authored in JSON. A visual editor, search, repeatable quests, advanced reward types, and translations beyond English are not included, and server messages are English only. Large catalogues are limited by the current sync size. Independent multiplayer, broad modpack compatibility, Linux, and macOS testing remain outstanding.

---

## IWantQuests 0.1.0-alpha

First public alpha for Minecraft Java 26.2 and NeoForge 26.2.0.90 or newer within 26.2. Requires Java 25 and installation on both client and server.

- Pan-and-zoom quest tree with chapters, branching prerequisites, progress indicators, and personal reward claims.
- Cooperative team progression with invitations and in-game team management.
- Eight objective types: obtain, possess, craft, kill, dimension, region, submit, and manual.
- Item and experience-point rewards.
- Custom quest JSON in config folders or data packs, with live /reload support and validation.
- Resource-pack themes, configurable quest nodes, and an included sample chapter.

### Known alpha limits
Quest content is authored in JSON. A visual editor, search, repeatable quests, advanced reward types, and translations beyond English are not included. Large catalogues are limited by the current sync size. Independent multiplayer, broad modpack compatibility, Linux, and macOS testing remain outstanding.

