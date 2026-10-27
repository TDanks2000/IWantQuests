package dev.iwantquests.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.iwantquests.quest.QuestFiles;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

/** Small local preference file, isolated by world path or server address. Never changes progression. */
public final class QuestPins {
    private static final Gson JSON = new Gson();
    private static Map<String, List<String>> books;

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/iwantquests-pins.json");
    }

    private static String scope() {
        var mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null)
            return "world:"
                    + mc.getSingleplayerServer()
                            .getWorldPath(LevelResource.ROOT)
                            .toAbsolutePath()
                            .normalize();
        return mc.getCurrentServer() == null
                ? "disconnected"
                : "server:" + mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
    }

    public static List<String> ids() {
        if (books == null) {
            try {
                books = Files.exists(file())
                        ? JSON.fromJson(
                                Files.readString(file()), new TypeToken<Map<String, List<String>>>() {}.getType())
                        : new HashMap<>();
                if (books == null) books = new HashMap<>();
            } catch (Exception error) {
                books = new HashMap<>();
                dev.iwantquests.IWantQuests.LOGGER.warn("Could not read pinned quests", error);
            }
        }
        List<String> saved = books.get(scope());
        return saved == null
                ? List.of()
                : saved.stream().filter(Objects::nonNull).distinct().limit(3).toList();
    }

    public static boolean pinned(String id) {
        return ids().contains(id);
    }

    public static void reload() {
        books = null;
    }

    public static void toggle(String id) {
        List<String> next = new ArrayList<>(ids());
        if (!next.remove(id)) {
            if (next.size() >= 3) {
                ClientQuestState.feedback = dev.iwantquests.client.ui.QuestText.t("pins.limit");
                ClientQuestState.feedbackSerial++;
                return;
            }
            next.add(id);
        }
        Map<String, List<String>> changed = new HashMap<>(books);
        changed.put(scope(), List.copyOf(next));
        try {
            QuestFiles.write(file(), JSON.toJson(changed));
            books = changed;
        } catch (Exception error) {
            ClientQuestState.feedback = dev.iwantquests.client.ui.QuestText.t("pins.save_failed");
            ClientQuestState.feedbackSerial++;
        }
        ClientQuestState.revision++;
    }

    private QuestPins() {}
}
