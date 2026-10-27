package dev.iwantquests;

import java.util.List;
import java.util.UUID;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-authoritative quest loading options, shared across worlds in this installation. */
public final class QuestConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue INCLUDE_DEFAULT_QUESTS;
    public static final ModConfigSpec.ConfigValue<String> EDITOR_NAMESPACE;
    public static final ModConfigSpec.BooleanValue EDITOR_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EDITOR_PLAYERS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        INCLUDE_DEFAULT_QUESTS = builder.comment(
                        "Load the bundled A New Beginning quests. Set false for a blank book or your own quest pack.",
                        "Custom JSON files in config/iwantquests/quests always load. Run /reload after changing quest settings or files.")
                .define("includeDefaultQuests", true);
        EDITOR_NAMESPACE = builder.comment(
                        "Namespace for chapters created with the in-game quest editor, for example \"mypack\" gives mypack:getting_started.",
                        "Quests keep the namespace of the chapter they are created in. Lowercase letters, digits, underscores, dots and dashes only.")
                .define(
                        "editorNamespace",
                        "custom",
                        value -> value instanceof String text && text.matches("[a-z0-9_.-]+"));
        EDITOR_ENABLED = builder.comment(
                        "Enable the in-game editor for quest authors. Leave false in distributed modpacks.",
                        "When enabled, operators with level 2 permission and players in editorPlayers can edit.",
                        "Multiplayer uses the server's settings, never a joining player's local settings.")
                .define("editorEnabled", false);
        EDITOR_PLAYERS = builder.comment(
                        "UUIDs of trusted quest authors who may edit without operator privileges when editorEnabled is true.",
                        "Use full player UUIDs, not names. An empty list grants no additional players access.")
                .defineListAllowEmpty(
                        "editorPlayers",
                        List.of(),
                        () -> "00000000-0000-0000-0000-000000000000",
                        QuestConfig::validPlayerId);
        SPEC = builder.build();
    }

    private static boolean validPlayerId(Object value) {
        if (!(value instanceof String text)) return false;
        try {
            return UUID.fromString(text).toString().equalsIgnoreCase(text);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private QuestConfig() {}
}
