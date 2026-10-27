# API research and version decisions

Verified 2026-10-11 against the official repositories, Maven artifacts and actual
compiled/decompiled Minecraft 26.2 sources. The published NeoForge guide currently
labels its newest pages 26.1. Use it for concepts; the 26.2 MDK and sources determine
actual signatures. No assumptions from older 1.21 tutorials are used as build proof.

- [Official 26.2 ModDevGradle MDK](https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle):
  Java 25, Gradle 9.2.1, ModDevGradle 2.0.148. Pinned NeoForge to 26.2.0.90 from
  [official Maven](https://maven.neoforged.net/releases/net/neoforged/neoforge/26.2.0.90/).
- [Getting started](https://docs.neoforged.net/docs/gettingstarted/): development
  setup and run/build tasks. Minecraft 26.2 ships unobfuscated source names;
  no separate third-party mapping layer is configured.
- [Payload registration](https://docs.neoforged.net/docs/networking/payload/):
  RegisterPayloadHandlersEvent, client-only RegisterClientPayloadHandlersEvent,
  StreamCodec, PacketDistributor and ClientPacketDistributor. Confirmed channel
  negotiation with the actual 26.2 NetworkRegistry and connection extensions.
- [Saved data](https://docs.neoforged.net/docs/datastorage/saveddata/):
  server-scoped SavedDataStorage and identifier/Codec-based SavedDataType.
- [Key mappings](https://docs.neoforged.net/docs/misc/keymappings/): registered
  KeyMapping.Category, RegisterKeyMappingsEvent and ClientTickEvent.Post.
- [Resources](https://docs.neoforged.net/docs/resources/): data-pack layout and
  resource reloads. Checked AddServerReloadListenersEvent, ListenerKey and
  SimplePreparableReloadListener directly in 26.2 source.
- [Game tests](https://docs.neoforged.net/docs/misc/gametest/): official headless
  server harness, test functions and RegisterGameTestsEvent. Runtime tests use
  real ItemEntity.playerTouch and CraftingMenu result-slot taking.

The 26.2 source changes GUI rendering to Screen.extractRenderState and
GuiGraphicsExtractor. Mouse/key handlers use MouseButtonEvent and KeyEvent.
Item tags are accessed through ItemStack.typeHolder().tags(). Commands use
Brigadier; future administrative operations should use the current
PermissionCheck/Permissions API, not removed integer hasPermission methods.
Client classes remain behind a Dist.CLIENT entrypoint.

Item and entity targets are checked against BuiltInRegistries. Coloured bed/wool
items are registered through colour collections in 26.2; definitions use actual
registered identifiers rather than assuming old static field names.
Minecraft's own version.json confirms Java 25, world data version 4903, and
26.2 data-pack format 107.1.

[FTB Quests](https://github.com/FTBTeam/FTB-Quests) was examined for chapter/canvas
navigation, scroll and zoom behaviour, prerequisite trees, UUID team data and
per-player rewards. These are functional references. No source or artwork was
copied, and no FTB dependency was introduced. See third-party notices for licence
boundaries. The initial editor is deferred until the playable foundation is tested.
