# Creator workflows

Enable `editorEnabled` in the server's `config/iwantquests-common.toml`. Only level 2
operators and UUIDs in `editorPlayers` can author quests. Clients cannot grant themselves
permission. Keep authoring disabled in distributed packs unless a server intentionally enables it.

## Pick items without memorising IDs

Use **Browse items…** for quest/chapter icons and item rewards, or **…** beside item task
targets. Search by translated item name, namespace or item ID. The item/tag toggle switches
to tags and previews matching item names and an example icon. Selecting a result applies
it to the draft; Back/Esc cancels. Typed IDs and the Held shortcut remain available.

## Copy and reuse quest groups

Select quests with Shift-click or a selection rectangle. Ctrl+D duplicates the selection;
Ctrl+C copies it and Ctrl+V pastes it into the current chapter. Copies get unique IDs,
retain relative positions, and remap links between copied quests. Existing external links
are retained; references absent from the destination book are dropped. A paste is one undo step.
Duplication places the group near its source; clipboard and template pastes start near
the current view. Placement avoids existing nodes and reveals the newly selected quest.
Selections are limited to 128 quests and the network request size; split larger groups.

The Details inspector offers Duplicate and Template actions. Template saves a reusable
selection using the first selected quest's title. Quest templates opens the shared server
library to place groups into the current chapter. Switch the library to Delete templates
to remove a template after confirmation. Files live under `config/iwantquests/templates/`.
Templates contain definitions, never player progress or claims.

## Save privately, then publish

Each author has a separate draft under `config/iwantquests/drafts/<UUID>/`. Draft edits,
undo and redo affect staged quest files only. Drafts survive restarts; undo history lasts
for the connected authoring session. Editing cannot reset live progress or pay live rewards.

Save/Ctrl+S commits unfinished fields to the draft. Preview opens a fresh-player view of
the draft, including normal hidden/locked presentation. It deliberately disables quest
submissions, reward claims and team actions. Esc returns to the editor after closing any
open quest details. Preview does not simulate later progression stages.

Review lists additions, edits, removals, chapter changes and progress/reward warnings.
Publish becomes available when a saved draft has changes and its published baseline is
still current. Confirming publishes the validated file batch and refreshes connected players.
Existing reward claims stay attached to their quest IDs. New quest IDs have independent claims.
Replaced files are backed up in `config/iwantquests/backups/`.

Publishing compares both the original live definitions and original file contents. Another
publication, data-pack reload or external file change can make a draft stale. It is then
blocked from publishing. Copy useful selections first, discard through Review, and paste
into the new baseline. Discard removes unpublished work only and asks for confirmation.

Individual file replacements are atomic. A failed batch attempts to restore files and
does not switch the live catalogue. A power failure during a multi-file publication is
not a filesystem transaction; keep world/config backups for live deployments.

## Find and track quests

Search/Ctrl+F searches the whole book. Choose All, Available, Completed, Unclaimed rewards
or Pinned. Hidden locked quests are excluded from player results; editors can search all
draft quests. Selecting a result switches chapter and opens its details or inspector.

Pin to HUD in quest details tracks up to three quests with live objective counts and a
progress bar. Pins are local preferences under `config/iwantquests-pins.json`, scoped by
world path or server address. They survive restarts and never change shared progression.
The HUD obeys F1 and `showPinnedTracker`; it is hidden while a screen is open.

Both client and server must run the same 1.0.0 protocol version. Mixing older authoring
clients with the draft workflow is rejected during network negotiation.
