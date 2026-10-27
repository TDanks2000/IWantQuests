package dev.iwantquests.client;

import dev.iwantquests.client.ui.QuestText;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;

/** Registry-backed item/tag selection. Tags preview actual matching items, including modded content. */
public final class ItemPickerScreen extends QuestListScreen {
    private record Choice(String id, String name, List<ItemStack> examples) {}

    private final List<Choice> items, tags;
    private final Consumer<String> selected;
    private final boolean allowTags;
    private boolean tagMode;

    public ItemPickerScreen(QuestBookScreen parent, boolean allowTags, Consumer<String> selected) {
        super(parent, QuestText.t("picker.title"));
        this.allowTags = allowTags;
        this.selected = selected;
        items = BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR)
                .map(item -> {
                    ItemStack stack = new ItemStack(item);
                    return new Choice(
                            BuiltInRegistries.ITEM.getKey(item).toString(),
                            stack.getHoverName().getString(),
                            List.of(stack));
                })
                .sorted(Comparator.comparing(Choice::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        tags = allowTags
                ? BuiltInRegistries.ITEM
                        .getTags()
                        .filter(tag -> tag.size() > 0)
                        .map(tag -> new Choice(
                                "#" + tag.key().location(),
                                "#" + tag.key().location(),
                                tag.stream()
                                        .limit(8)
                                        .map(h -> new ItemStack(h.value()))
                                        .toList()))
                        .sorted(Comparator.comparing(Choice::id))
                        .toList()
                : List.of();
    }

    @Override
    protected String modeLabel() {
        return allowTags ? QuestText.t(tagMode ? "picker.tags" : "picker.items") : "";
    }

    @Override
    protected void nextMode() {
        tagMode = !tagMode;
    }

    @Override
    protected List<Entry> entries() {
        String term = query.strip().toLowerCase(Locale.ROOT);
        return (tagMode ? tags : items)
                .stream()
                        .filter(c -> (c.name() + " " + c.id() + " "
                                        + c.examples().stream()
                                                .map(s -> s.getHoverName().getString())
                                                .toList())
                                .toLowerCase(Locale.ROOT)
                                .contains(term))
                        .map(c -> new Entry(
                                c.name(),
                                tagMode
                                        ? c.id() + " · "
                                                + c.examples().stream()
                                                        .map(s ->
                                                                s.getHoverName().getString())
                                                        .reduce((a, b) -> a + ", " + b)
                                                        .orElse("")
                                        : c.id(),
                                c.examples().getFirst(),
                                () -> {
                                    selected.accept(c.id());
                                    onClose();
                                }))
                        .toList();
    }
}
