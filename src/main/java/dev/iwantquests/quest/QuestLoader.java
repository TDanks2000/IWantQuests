package dev.iwantquests.quest;

import com.google.gson.Gson;
import dev.iwantquests.IWantQuests;
import dev.iwantquests.QuestConfig;
import java.io.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.resource.ListenerKey;

public final class QuestLoader extends SimplePreparableReloadListener<QuestCatalog> {
    public static final Gson JSON = new Gson();
    /** Larger catalogues are synchronised in parts; this keeps client memory and join time reasonable. */
    public static final int MAX_PACK_CHARS = 12_000_000;

    public static final ListenerKey<QuestLoader> KEY =
            ListenerKey.create(Identifier.fromNamespaceAndPath("iwantquests", "quests"));
    private QuestCatalog catalog;

    public QuestCatalog catalog() {
        return Objects.requireNonNull(catalog, "Quest definitions have not loaded");
    }

    @Override
    protected QuestCatalog prepare(ResourceManager manager, ProfilerFiller profiler) {
        return load(
                manager,
                FMLPaths.CONFIGDIR.get().resolve("iwantquests/quests"),
                QuestConfig.INCLUDE_DEFAULT_QUESTS.get());
    }

    /** Shared loading pipeline, also exercised against real game resources by GameTests. */
    public static QuestCatalog load(ResourceManager manager, java.nio.file.Path directory, boolean includeDefaults) {
        List<QuestPack.Chapter> chapters = new ArrayList<>();
        List<QuestPack.Quest> quests = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        manager.listResources("iwantquests/quests", id -> id.getPath().endsWith(".json"))
                .forEach((id, resource) -> {
                    if (!includeDefaults
                            && id.equals(Identifier.fromNamespaceAndPath(
                                    "iwantquests", "iwantquests/quests/a_new_beginning.json"))) return;
                    try (Reader reader = resource.openAsReader()) {
                        QuestPack pack = QuestFiles.read(reader);
                        chapters.addAll(pack.chapters());
                        quests.addAll(pack.quests());
                    } catch (Exception error) {
                        errors.add(id + ": " + error.getMessage());
                    }
                });
        try {
            for (QuestPack pack : QuestFiles.loadDirectory(directory)) {
                chapters.addAll(pack.chapters());
                quests.addAll(pack.quests());
            }
        } catch (Exception error) {
            errors.add(error.getMessage());
        }
        if (!errors.isEmpty())
            throw new IllegalArgumentException("IWantQuests reload failed:\n" + String.join("\n", errors));
        try {
            return build(new QuestPack(List.copyOf(chapters), List.copyOf(quests)));
        } catch (Exception error) {
            throw new IllegalArgumentException(
                    "IWantQuests definition validation failed: " + error.getMessage(), error);
        }
    }

    /** Validates a merged pack against the live registries and the network size limit. Used by reloads and the editor. */
    public static QuestCatalog build(QuestPack pack) {
        QuestCatalog result = new QuestCatalog(pack, QuestLoader::validateTarget);
        if (JSON.toJson(result.pack()).length() > MAX_PACK_CHARS)
            throw new IllegalArgumentException(
                    "Quest pack exceeds the limit of " + MAX_PACK_CHARS / 1_000_000 + " million characters of JSON");
        return result;
    }

    /** Makes an editor-validated catalogue the live one without a resource reload. */
    public void replace(QuestCatalog replacement) {
        catalog = Objects.requireNonNull(replacement);
        IWantQuests.LOGGER.info(
                "Quest editor applied: {} quests in {} chapters",
                catalog.quests().size(),
                catalog.pack().chapters().size());
    }

    public static void validateTarget(String kind, String value) {
        if (value == null) throw new IllegalArgumentException("Missing " + kind + " identifier");
        if (kind.equals("item_selector") && value.startsWith("#")) {
            if (Identifier.tryParse(value.substring(1)) == null)
                throw new IllegalArgumentException("Invalid item tag: " + value);
            return; // Tags are bound after prepare. Missing/empty tags are reported at server sync.
        }
        Identifier id = Identifier.tryParse(value);
        boolean valid = id != null
                && (kind.equals("entity")
                        ? BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                        : BuiltInRegistries.ITEM.containsKey(id));
        if (!valid || value.equals("minecraft:air"))
            throw new IllegalArgumentException("Unknown " + kind + ": " + value);
    }

    @Override
    protected void apply(QuestCatalog prepared, ResourceManager manager, ProfilerFiller profiler) {
        catalog = prepared;
        IWantQuests.LOGGER.info(
                "Loaded {} quests in {} chapters",
                catalog.quests().size(),
                catalog.pack().chapters().size());
    }
}
