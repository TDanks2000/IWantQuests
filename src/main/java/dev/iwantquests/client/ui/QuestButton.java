package dev.iwantquests.client.ui;

import dev.iwantquests.client.theme.QuestTheme;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** A button drawn flat from the current theme's colours, or with its nine-sliced sprites when {@code flat_buttons} is off. */
public class QuestButton extends Button {
    private static final int ENABLED_TEXT = 0xffffffff;
    private static final int DISABLED_TEXT = 0xff6f7b8a;

    private final Supplier<QuestTheme> theme;
    private final QuestText text;
    /** Draws the button in the theme's accent colour; used for the one action a screen wants the player to take. */
    public boolean primary;

    public QuestButton(Supplier<QuestTheme> theme, int x, int y, int width, int height, String title, OnPress press) {
        super(x, y, width, height, Component.literal(title), press, DEFAULT_NARRATION);
        this.theme = theme;
        this.text = new QuestText(theme);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        QuestTheme current = theme.get();
        if (current.flatButtons) {
            drawFlat(g, current);
        } else {
            Identifier sprite = spriteFor(current);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, getX(), getY(), getWidth(), getHeight());
        }
        drawLabel(g, current);
    }

    private void drawFlat(GuiGraphicsExtractor g, QuestTheme current) {
        boolean hot = active && isHoveredOrFocused();
        int fill;
        int border;
        if (!active) {
            fill = current.buttonDisabledFill;
            border = current.border;
        } else if (primary) {
            fill = QuestDraw.shade(current.accent, hot ? .8f : .62f);
            border = current.accent;
        } else {
            fill = hot ? current.buttonHoverFill : current.buttonFill;
            border = hot
                    ? QuestDraw.mix(current.border, 0xffffffff, .45f)
                    : QuestDraw.mix(current.border, 0xffffffff, .12f);
        }
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), fill);
        if (primary || hot) g.outline(getX(), getY(), getWidth(), getHeight(), border);
        if (isFocused() && active) g.outline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, 0xffffffff);
    }

    private Identifier spriteFor(QuestTheme current) {
        if (!active) return current.buttonDisabled;
        return isHoveredOrFocused() ? current.buttonHighlighted : current.button;
    }

    private void drawLabel(GuiGraphicsExtractor g, QuestTheme current) {
        Font font = Minecraft.getInstance().font;
        Component message = text.component(getMessage());
        int textX = getX() + (getWidth() - font.width(message)) / 2;
        int textY = getY() + (getHeight() - 9) / 2 + 1;
        g.text(font, message, textX, textY, active ? ENABLED_TEXT : DISABLED_TEXT, current.textShadow && active);
    }
}
