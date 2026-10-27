package dev.iwantquests.client.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.iwantquests.IWantQuests;
import dev.iwantquests.client.ClientConfig;
import java.io.Reader;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.Nullable;

/** Loads quest book themes from {@code assets/<namespace>/iwantquests/themes/<name>.json} and resolves them. */
public final class ThemeManager {
    public static final Identifier DEFAULT_ID = Identifier.fromNamespaceAndPath("iwantquests", "default");
    private static final String DIRECTORY = "iwantquests/themes";
    private static volatile Map<Identifier, QuestTheme> themes = Map.of();

    /** Resolves: client config override, then the chapter's theme ID, then iwantquests:default, then QuestTheme.DEFAULT. */
    public static QuestTheme get(@Nullable String chapterTheme) {
        Map<Identifier, QuestTheme> loaded = themes;
        for (String candidate : new String[] {ClientConfig.themeOverride(), chapterTheme}) {
            if (candidate == null || candidate.isBlank()) continue;
            Identifier id = Identifier.tryParse(candidate.strip());
            if (id == null) continue;
            QuestTheme theme = loaded.get(id);
            if (theme != null) return theme;
        }
        QuestTheme fallback = loaded.get(DEFAULT_ID);
        return fallback != null ? fallback : QuestTheme.DEFAULT;
    }

    /** Loaded theme IDs, sorted. */
    public static Set<Identifier> ids() {
        Set<Identifier> sorted = new TreeSet<>(Comparator.comparing(Identifier::toString));
        sorted.addAll(themes.keySet());
        return sorted;
    }

    /** Reads every theme JSON from all resource packs. Apply resolves parents and publishes the result. */
    public static final class Loader extends SimplePreparableReloadListener<Map<Identifier, JsonObject>> {
        @Override
        protected Map<Identifier, JsonObject> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<Identifier, JsonObject> sources = new HashMap<>();
            manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json"))
                    .forEach((file, resource) -> {
                        try (Reader reader = resource.openAsReader()) {
                            String path = file.getPath()
                                    .substring(
                                            DIRECTORY.length() + 1,
                                            file.getPath().length() - ".json".length());
                            Identifier themeId = Identifier.fromNamespaceAndPath(file.getNamespace(), path);
                            JsonElement element = JsonParser.parseReader(reader);
                            if (!element.isJsonObject())
                                throw new IllegalArgumentException("Theme must be a JSON object");
                            sources.put(themeId, element.getAsJsonObject());
                        } catch (Exception error) {
                            IWantQuests.LOGGER.warn("Skipping theme file {}: {}", file, error.getMessage());
                        }
                    });
            return sources;
        }

        @Override
        protected void apply(Map<Identifier, JsonObject> prepared, ResourceManager manager, ProfilerFiller profiler) {
            Map<Identifier, QuestTheme> resolved = new HashMap<>();
            Set<Identifier> failed = new HashSet<>();
            for (Identifier id : prepared.keySet()) resolve(id, prepared, resolved, failed, new LinkedHashSet<>());
            themes = Map.copyOf(resolved);
            IWantQuests.LOGGER.info("Loaded {} quest themes: {}", resolved.size(), ids());
        }
    }

    /** Returns the resolved theme, or null (after logging) when it or one of its parents is invalid. */
    private static @Nullable QuestTheme resolve(
            Identifier id,
            Map<Identifier, JsonObject> sources,
            Map<Identifier, QuestTheme> resolved,
            Set<Identifier> failed,
            Set<Identifier> path) {
        QuestTheme done = resolved.get(id);
        if (done != null) return done;
        if (failed.contains(id)) return null;
        if (!path.add(id)) {
            IWantQuests.LOGGER.warn("Skipping theme {}: circular parent chain {}", id, path);
            failed.add(id);
            return null;
        }
        JsonObject json = sources.get(id);
        if (json == null) {
            IWantQuests.LOGGER.warn("Skipping theme {}: not found", id);
            failed.add(id);
            return null;
        }

        QuestTheme parent;
        if (id.equals(DEFAULT_ID)) {
            parent = QuestTheme.DEFAULT;
        } else {
            Identifier parentId = DEFAULT_ID;
            if (json.has("parent")) {
                JsonElement value = json.get("parent");
                Identifier parsed = value.isJsonPrimitive() ? Identifier.tryParse(value.getAsString()) : null;
                if (parsed == null) {
                    IWantQuests.LOGGER.warn("Skipping theme {}: invalid parent {}", id, value);
                    failed.add(id);
                    return null;
                }
                parentId = parsed;
            }
            QuestTheme resolvedParent = resolve(parentId, sources, resolved, failed, path);
            if (resolvedParent == null) {
                IWantQuests.LOGGER.warn("Skipping theme {}: parent {} could not be loaded", id, parentId);
                failed.add(id);
                return null;
            }
            parent = resolvedParent;
        }

        try {
            QuestTheme theme = QuestTheme.parse(json, parent);
            resolved.put(id, theme);
            return theme;
        } catch (RuntimeException error) {
            IWantQuests.LOGGER.warn("Skipping theme {}: {}", id, error.getMessage());
            failed.add(id);
            return null;
        }
    }

    private ThemeManager() {}
}
