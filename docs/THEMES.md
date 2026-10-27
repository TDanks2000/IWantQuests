# IWantQuests Themes

A theme controls how the quest book looks and where its pieces sit: colours, GUI sprites, node
shapes, fonts, the position of the chapter list, title and buttons, and a set of on/off options.
Text wording is changed separately through the language file (see [Wording](#wording)).

Themes load from resource packs on the client, so modpack and resource pack authors can restyle
the book without code. Press **F3 + T** in game to reload a pack and see theme edits straight away.

The built-in look is deliberately minimal: a chapter list on the left, the quest tree filling the
screen, and a few small floating controls. Everything below can be changed or moved.

## Where theme files go

Put theme JSON files under the `iwantquests/themes` folder of any namespace in a resource pack or your mod's assets:

```
assets/<namespace>/iwantquests/themes/<name>.json
```

The theme ID is `<namespace>:<name>`. For example, `assets/mypack/iwantquests/themes/dark.json` is the theme `mypack:dark`. Subfolders work too: `assets/mypack/iwantquests/themes/book/dark.json` is `mypack:book/dark`.

If two packs define the same ID, the one loaded last wins, as with other resources.

Three themes ship with the mod: `iwantquests:default` (flat, dark), `iwantquests:midnight` (blue) and
`iwantquests:classic` (vanilla-style buttons and frames with a top bar). They are good starting points.

## Inheritance with `parent`

Every key is optional. Omitted values come from the parent theme:

```json
{
  "parent": "iwantquests:default",
  "colors": {
    "heading": "#FF55FFFF"
  }
}
```

- `parent` defaults to `iwantquests:default` when omitted.
- `iwantquests:default` inherits from the built-in values in the code, so it is always complete even if its file is missing keys.
- A theme with a missing, invalid or circular parent is skipped and a warning is written to the log.
- A value that cannot be read (a bad colour, an unknown anchor) skips the theme and names the key in the log.

## Keys

Colours are ARGB. See [Colour formats](#colour-formats).

### `colors`

| Key | Default | Meaning |
|---|---|---|
| `text` | `#FFFFFFFF` | Main text |
| `muted` | `#FFAAB4C0` | Secondary text |
| `subtle` | `#FF7A8594` | Least important text |
| `heading` | `#FF7FD6C2` | Section headings |
| `complete` | `#FF5BD18B` | Completed status, node edge and check badge |
| `ready` | `#FFFFB347` | Ready-to-claim status, node edge and glow |
| `in_progress` | `#FFE8D66B` | In-progress status text |
| `locked` | `#FFFF6B6B` | Locked status text |
| `background` | `#D9111720` | Full-screen background tint |
| `overlay` | `#99080B10` | Dimming behind the quest window |
| `panel` | `#F21A212C` | Quest window |
| `header` | `#F2161C26` | Optional top bar (see `header_height`) |
| `sidebar` | `#E612171F` | Chapter list background |
| `sidebar_selected` | `#FF1D2633` | Selected chapter row |
| `border` | `#FF2E3A4A` | Panel borders and dividers |
| `tab` | `#00000000` | Unselected chapter row (transparent by default) |
| `tab_selected` | `#FF4FD1B5` | Marker on the selected chapter row |
| `line_complete` | `#FF5BD18B` | Connector to a completed quest |
| `line_available` | `#FF8FA3B8` | Connector to an available quest |
| `line_locked` | `#FF39424F` | Connector to a locked quest, and a locked node's edge |
| `line_outline` | `#00000000` | Outline around connectors (needs `line_outlines`) |
| `progress_fill` | `#FF4FD18B` | Progress bar fill |
| `progress_back` | `#FF0B0F14` | Progress bar background |
| `selection` | `#FFFFFFFF` | Selected node edge and halo |
| `accent` | `#FF4FD1B5` | Edge of an available node, primary buttons |
| `card` | `#FF202A37` | Cards in custom UI |
| `node_face` | `#FF1A222D` | Inside of a node |
| `node_face_locked` | `#FF12171E` | Inside of a locked node |
| `button` | `#FF2C3848` | Flat button fill |
| `button_hover` | `#FF3A4A5E` | Hovered flat button fill |
| `button_disabled` | `#FF1D2531` | Disabled flat button fill |

### `sprites`

GUI sprite IDs. See [Custom GUI sprites](#custom-gui-sprites). Sprites marked *optional* default to
`null`, which means "draw it flat from the colours instead".

| Key | Default | Meaning |
|---|---|---|
| `slot` | `minecraft:container/slot` | Item slot frames |
| `notification` | `minecraft:icon/unseen_notification` | Unseen-update marker |
| `button` | `minecraft:widget/button` | Buttons (when `flat_buttons` is off) |
| `button_highlighted` | `minecraft:widget/button_highlighted` | Hovered buttons |
| `button_disabled` | `minecraft:widget/button_disabled` | Disabled buttons |
| `background` | optional | Texture behind the quest tree (a chapter's own `background` wins) |
| `tooltip` | optional | Tooltip style. `null` uses the vanilla tooltip. |
| `panel` | optional | Nine-slice background of the quest window and Team screen |
| `sidebar` | optional | Nine-slice background of the chapter list |
| `header` | optional | Nine-slice background of the top bar |
| `tab` | optional | Unselected chapter row |
| `tab_selected` | optional | Selected chapter row |
| `badge_complete` | optional | Badge on completed nodes (10x10) |
| `badge_locked` | optional | Badge on locked nodes (10x10) |

### `shapes`

Node shapes, keyed by name. A quest picks one with its `shape` field. Every entry is optional and
inherits from `task` when it has no parent entry of the same name.

| Key | Meaning |
|---|---|
| `geometry` | The flat outline: `circle`, `square`, `rounded`, `octagon`, `hexagon` or `diamond` |
| `scale` | Size multiplier for this shape, 0.25 to 3 |
| `complete` | Sprite drawn when completed (sprite mode, see `flat_nodes`) |
| `incomplete` | Sprite drawn otherwise. Defaults to `complete`. |

Built in: `task` (circle), `goal` (rounded square) and `challenge` (hexagon).

```json
"shapes": {
  "boss": { "geometry": "diamond", "scale": 1.4 }
}
```

### `layout`

Sizes are in GUI pixels. Out-of-range values are clamped.

| Key | Default | Range | Meaning |
|---|---|---|---|
| `node_size` | `30` | 8-128 | Quest node size |
| `node_border` | `2` | 1-6 | Thickness of a flat node's edge |
| `node_icon_scale` | `0.56` | 0.25-0.95 | Icon size as a fraction of the node |
| `details_width` | `170` | 100-400 | Base quest window width (the window uses twice this plus 32, limited to screen width) |
| `sidebar_width` | `30` | 0-128 | Collapsed chapter rail width |
| `sidebar_expanded_width` | `140` | 48-260 | Expanded chapter list width (also limited to a third of the screen) |
| `tab_height` | `24` | 16-48 | Height of a chapter row |
| `header_height` | `0` | 0-96 | A top bar of this height. `0` means no bar. |
| `line_width` | `2` | 1-8 | Connector line width |

Anchors place a floating piece of the screen. Each accepts only the values listed.

| Key | Default | Allowed |
|---|---|---|
| `sidebar_position` | `left` | `left`, `right`, `hidden` |
| `title_position` | `top_left` | `top_left`, `top_center`, `top_right`, `bottom_left`, `bottom_center`, `bottom_right`, `hidden` |
| `actions_position` | `top_right` | `top_left`, `top_right`, `bottom_left`, `bottom_right` (the Team and close buttons and reward notice) |
| `controls_position` | `bottom_right` | `top_left`, `top_right`, `bottom_left`, `bottom_right`, `hidden` (Check, zoom and Fit) |

With a top bar (`header_height` above 0) the pieces anchored to the top sit inside the bar.

### `options`

| Key | Default | Meaning |
|---|---|---|
| `font` | `null` | A font ID, such as `mypack:quest`, used for all book text. `null` is the normal font. |
| `blur` | `true` | Allows the world behind the book to be blurred. The player's `blur` option also applies. |
| `text_shadow` | `true` | Draws text shadows |
| `chapter_title` | `true` | Shows the chapter name and progress |
| `sidebar_progress` | `true` | A thin progress line on each chapter row |
| `node_progress_bars` | `true` | A thin bar under in-progress nodes |
| `node_titles` | `false` | Writes each quest's title under its node (when zoomed in) |
| `animations` | `true` | Pulsing and bobbing markers |
| `canvas_grid` | `true` | A faint dot grid that moves and scales with the tree |
| `direct_connections` | `true` | Straight dependency lines; `false` uses elbow links |
| `arrowheads` | `false` | Arrowheads on straight dependency lines |
| `line_outlines` | `false` | Draws a `line_outline` outline around connectors |
| `flat_buttons` | `true` | Flat buttons from the theme colours. `false` uses the button sprites. |
| `flat_nodes` | `true` | Flat nodes in each shape's `geometry`. `false` uses the shape sprites. |
| `locked_alpha` | `0.6` | Opacity of locked sprite nodes, 0 to 1. Flat nodes use `node_face_locked` instead. |

## Colour formats

Use a quoted string, with or without `#`:

- `"#RRGGBB"`: fully opaque.
- `"#AARRGGBB"`: with alpha. `00` is transparent, `FF` is opaque.

Any other length is an error and the theme is skipped.

## Wording

Every fixed word the book shows ("Tasks", "Rewards", "Claim rewards", "Team", status names) is a
language key under `iwantquests.ui.`. Override them in your own resource pack:

```json
// assets/iwantquests/lang/en_us.json (in your pack)
{
  "iwantquests.ui.details.tasks": "Objectives",
  "iwantquests.ui.details.rewards": "Spoils",
  "iwantquests.ui.claim": "Collect"
}
```

Translations work the same way with the matching language file name (`de_de.json`, `fr_fr.json`).
The full key list is in the mod's own `en_us.json`. Quest, chapter and objective text comes from your
quest files, so write that in whichever language you like. Messages the server sends back after an
action (for example "Rewards claimed") are English for now.

## Custom fonts

Set `options.font` to a font ID. Fonts use the vanilla format
(`assets/<ns>/font/<name>.json`), so `"font": "mypack:quest"` uses `assets/mypack/font/quest.json`.
The book measures and clips text with that font, so wider fonts still fit.

## Custom node shapes

A quest's `shape` field (in quest data) can be:

1. `task`, `goal`, `challenge`, or any shape defined in the theme's `shapes`.
2. A namespaced ID such as `mypack:gem`. It uses the `task` geometry, and the sprites `mypack:gem_complete` and `mypack:gem_incomplete` when `flat_nodes` is off.

Unknown shapes fall back to `task`.

## Custom GUI sprites

A sprite ID `<ns>:<path>` loads from:

```
assets/<ns>/textures/gui/sprites/<path>.png
```

For example, `mypack:shapes/gem_complete` is `assets/mypack/textures/gui/sprites/shapes/gem_complete.png`.

Sprites that stretch (buttons, panels) use nine-slice scaling, set with a `.png.mcmeta` file next to the texture in the same format as vanilla GUI sprites:

```json
{
  "gui": {
    "scaling": {
      "type": "nine_slice",
      "width": 16,
      "height": 16,
      "border": 4
    }
  }
}
```

## Recipes

**A parchment book with a top bar and the chapter list on the right:**

```json
{
  "parent": "iwantquests:default",
  "colors": {
    "background": "#E0261D12", "panel": "#FF3A2C1A", "sidebar": "#F0261D12",
    "border": "#FF1A130A", "text": "#FFF3E3C2", "muted": "#FFC9B48C",
    "accent": "#FFE0B050", "heading": "#FFE0B050"
  },
  "layout": { "sidebar_position": "right", "header_height": 28, "title_position": "top_left" },
  "shapes": { "task": { "geometry": "square" } },
  "options": { "canvas_grid": false, "font": "mypack:serif" }
}
```

**An almost invisible UI (tree only):**

```json
{
  "layout": { "sidebar_position": "hidden", "controls_position": "hidden", "title_position": "hidden" },
  "options": { "sidebar_progress": false }
}
```

## Restyling everything: overriding `iwantquests:default`

Theme IDs come from the namespace of their file, so `iwantquests:default` is the file `assets/iwantquests/iwantquests/themes/default.json`. Because every theme inherits from `iwantquests:default`, replacing that file restyles every book. Put it in a resource pack that loads after IWantQuests and include only the keys you want to change; any key you omit keeps the built-in value.

Themes in your own namespace are usually the better choice. Use `"parent": "iwantquests:default"` and override only what differs, so your theme keeps working if the defaults change.

## Choosing a theme for a chapter

In a quest data file, set the chapter's `theme` field to a theme ID:

```json
{
  "chapters": [
    { "id": "mining", "title": "Mining", "theme": "mypack:dark" }
  ],
  "quests": []
}
```

If the chapter has no theme, the book uses `iwantquests:default`. The whole book, including the
chapter list and the Team screen, uses the theme of the chapter being viewed.

## Player options

Players can change these in `config/iwantquests-client.toml`, or in **Mods > IWantQuests > Config**:

| Option | Default | Meaning |
|---|---|---|
| `backgroundOpacity` | `100` | Percentage of the theme's background opacity to apply, 0-100 |
| `blur` | `true` | Blur the world behind the book, if the theme allows it |
| `theme` | `""` | Theme ID that overrides every chapter's theme. Empty uses the chapter theme. |

The override is applied first, then the chapter theme, then `iwantquests:default`.
