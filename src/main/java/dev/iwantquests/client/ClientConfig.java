package dev.iwantquests.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Player-facing client options, stored in {@code config/iwantquests-client.toml}. */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue BACKGROUND_OPACITY;
    private static final ModConfigSpec.BooleanValue BLUR;
    private static final ModConfigSpec.ConfigValue<String> THEME;
    private static final ModConfigSpec.BooleanValue TRACKER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        BACKGROUND_OPACITY = builder.comment("Percentage of the theme background opacity to apply (0-100).")
                .defineInRange("backgroundOpacity", 100, 0, 100);
        BLUR = builder.comment("Blur the world behind the quest book. The theme must also allow blur.")
                .define("blur", true);
        THEME = builder.comment("Theme ID such as iwantquests:midnight. Empty uses the chapter's theme.")
                .define("theme", "");
        TRACKER = builder.comment("Show up to three pinned quests on the in-game HUD.")
                .define("showPinnedTracker", true);
        SPEC = builder.build();
    }

    /** 0-100: percentage of the theme's background alpha to apply. Default 100. */
    public static int backgroundOpacity() {
        try {
            return BACKGROUND_OPACITY.get();
        } catch (IllegalStateException notLoaded) {
            return 100;
        }
    }

    /** Whether the world behind the book is blurred (also requires the theme's blur option). Default true. */
    public static boolean blur() {
        try {
            return BLUR.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** Theme ID forced by the player, or empty to use chapter/pack themes. */
    public static String themeOverride() {
        try {
            return THEME.get();
        } catch (IllegalStateException notLoaded) {
            return "";
        }
    }

    /** Changes the forced theme for this session. The smoke test uses it to capture each bundled theme. */
    public static void overrideTheme(String id) {
        THEME.set(id);
    }

    private ClientConfig() {}

    public static boolean tracker() {
        try {
            return TRACKER.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }
}
