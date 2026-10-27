package dev.iwantquests.network;

import java.util.List;

/**
 * Tells a client whether it may edit and which quests and chapters live in editable config files.
 * Sent to every player; clients that may not edit receive {@code enabled = false} and empty lists.
 */
public record EditorInfo(
        boolean enabled, List<String> quests, List<String> chapters, boolean canUndo, boolean canRedo) {
    public static final EditorInfo NONE = new EditorInfo(false, List.of(), List.of(), false, false);
}
