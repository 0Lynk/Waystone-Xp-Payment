package com.olynk.waystonexppayment.client;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A flat, hand-drawn header button.
 *
 * <p>Vanilla's button sprite is a nine-slice of a textured widget: it cannot carry a colour, and the tank
 * screen has no texture of its own ({@code assets/waystone_xp_payment} ships one model and two lang files).
 * Drawing the button here keeps the accent colour as the only thing that varies between the three buttons,
 * and the accent is deliberately the colour of the row the button adds.
 *
 * <p>Position and size are set by the caller through the inherited {@code setX/setY/setWidth/setHeight}:
 * the builder-based route cannot carry the accent colour, because {@code Button.Builder.build(Function)} hands
 * the factory a fresh builder rather than the accent this class needs.
 */
class AccessButton extends Button {

    private static final int COLOR_PANEL = 0xE0101010;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DISABLED = 0xFF8A8A8A;
    private static final int COLOR_HIGHLIGHT = 0x1AFFFFFF;
    private static final int COLOR_SHADOW = 0x33000000;
    private static final int COLOR_BORDER = 0xFF555555;
    private static final int COLOR_BORDER_HIGHLIGHT = 0xFF9A9A9A;
    private static final int COLOR_BORDER_DISABLED = 0xFF3A3A3A;
    private static final int BODY_IDLE = 0x33FFFFFF;
    private static final int BODY_HOVER = 0x55FFFFFF;
    private static final int BODY_DISABLED = 0x14FFFFFF;
    private static final int ACCENT_DISABLED = 0x40FFFFFF;

    private final int accent;
    /** Drawn instead of the message when set; the message stays as the narration a screen reader reads out. */
    @Nullable
    private final String glyph;

    AccessButton(Component message, int accent, Button.OnPress onPress) {
        this(message, accent, onPress, null);
    }

    AccessButton(Component message, int accent, Button.OnPress onPress, @Nullable String glyph) {
        super(0, 0, 0, 0, message, onPress, DEFAULT_NARRATION);
        this.accent = accent;
        this.glyph = glyph;
    }

    @Override
    public int getFGColor() {
        return this.active ? COLOR_TEXT : COLOR_TEXT_DISABLED;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();

        boolean enabled = this.active;
        boolean highlighted = enabled && (this.isHovered || this.isFocused());
        int body = !enabled ? BODY_DISABLED : highlighted ? BODY_HOVER : BODY_IDLE;
        int border = !enabled ? COLOR_BORDER_DISABLED : highlighted ? COLOR_BORDER_HIGHLIGHT : COLOR_BORDER;
        int stripe = enabled ? this.accent : this.accent & ACCENT_DISABLED;

        guiGraphics.fill(x, y, x + width, y + height, COLOR_PANEL);
        guiGraphics.fill(x, y, x + width, y + height, body);
        guiGraphics.fill(x, y, x + width, y + 1, COLOR_HIGHLIGHT);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, COLOR_SHADOW);
        // The accent stripe stays at full opacity while enabled so it reads as a category, not as a state.
        guiGraphics.fill(x, y, x + 3, y + height, stripe);

        // The label is drawn here rather than through AbstractButton.renderWidget: that method blits vanilla's
        // widget/button sprite first, which would repaint the grey button over the body drawn above and leave
        // the screen with two button styles again. Only the text placement is copied from it.
        Font font = Minecraft.getInstance().font;
        int textX = x + 3 + (width - 3) / 2;
        int textY = y + (height - 8) / 2;
        if (glyph != null) {
            guiGraphics.drawCenteredString(font, glyph, textX, textY, getFGColor());
        } else {
            guiGraphics.drawCenteredString(font, getMessage(), textX, textY, getFGColor());
        }

        guiGraphics.fill(x, y, x + width, y + 1, border);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, border);
        guiGraphics.fill(x, y, x + 1, y + height, border);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, border);
    }
}
