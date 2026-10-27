package dev.iwantquests.client.ui;

import dev.iwantquests.client.theme.QuestTheme;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.util.FormattedCharSequence;

/**
 * Every string the quest screens draw goes through here, so the current theme's font and text shadow apply
 * everywhere and widths are measured with the font that will actually draw them.
 * Fixed UI wording lives in the language file ({@code iwantquests.ui.*}) so resource packs can change it.
 */
public final class QuestText {
    private static final String ELLIPSIS = "…";

    private final Supplier<QuestTheme> theme;

    public QuestText(Supplier<QuestTheme> theme) {
        this.theme = theme;
    }

    private Font font() {
        return Minecraft.getInstance().font;
    }

    /** Looks up a UI string from the language files. */
    public static String t(String key, Object... args) {
        return I18n.get("iwantquests.ui." + key, args);
    }

    /** Wraps text in the theme's font, when it sets one. Quest text keeps its own colour codes. */
    public Component component(String text) {
        return component(Component.literal(text));
    }

    public Component component(Component text) {
        var id = theme.get().font;
        return id == null ? text : text.copy().withStyle(style -> style.withFont(new FontDescription.Resource(id)));
    }

    public int width(String text) {
        return font().width(component(text));
    }

    public int lineHeight() {
        return font().lineHeight;
    }

    /** Text cut with an ellipsis so it is at most {@code maxWidth} wide. */
    public String clip(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (width(text) <= maxWidth) return text;
        int low = 0, high = text.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (width(text.substring(0, mid) + ELLIPSIS) <= maxWidth) low = mid;
            else high = mid - 1;
        }
        return low == 0 ? "" : text.substring(0, low) + ELLIPSIS;
    }

    public List<FormattedCharSequence> wrap(String text, int maxWidth) {
        return font().split(component(text), Math.max(1, maxWidth));
    }

    public void draw(GuiGraphicsExtractor g, String text, int x, int y, int colour) {
        g.text(font(), component(text), x, y, colour, theme.get().textShadow);
    }

    public void drawClipped(GuiGraphicsExtractor g, String text, int x, int y, int maxWidth, int colour) {
        draw(g, clip(text, maxWidth), x, y, colour);
    }

    /** Draws text so that it ends at {@code right}. */
    public void drawRight(GuiGraphicsExtractor g, String text, int right, int y, int colour) {
        draw(g, text, right - width(text), y, colour);
    }

    public void drawCentered(GuiGraphicsExtractor g, String text, int centre, int y, int colour) {
        draw(g, text, centre - width(text) / 2, y, colour);
    }

    public void drawLine(GuiGraphicsExtractor g, FormattedCharSequence line, int x, int y, int colour) {
        g.text(font(), line, x, y, colour, theme.get().textShadow);
    }
}
