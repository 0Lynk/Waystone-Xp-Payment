package com.olynk.waystonexppayment.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.olynk.waystonexppayment.menu.XpPaymentTankMenu;
import com.olynk.waystonexppayment.net.XppAccessPayload;
import com.olynk.waystonexppayment.net.XppStatePayload;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Screen for the payment tank: the pool on top, the access list below.
 *
 * <p>There is no text input on purpose. Every entry is an identity the game already knows - the player's own
 * game-profile UUID or one of their FTB teams - so the list cannot be typo'd into something unpayable.
 *
 * <p>The list is drawn directly instead of being a widget tree. Rows carry a colour bar, an icon, a delete hit
 * box and a hover dwell tooltip, none of which {@code AbstractWidget} models; a widget per row would need a
 * custom widget anyway, and the list is short and fixed-height. [ASSUMPTION: this is the smaller of the two
 * implementations, not a performance choice - five rows are redrawn per frame either way.]
 */
public class XpPaymentTankScreen extends AbstractContainerScreen<XpPaymentTankMenu> {

    private static final int PANEL_WIDTH = 256;
    private static final int PANEL_HEIGHT = 214;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_FLUID_TEXT = 0xFF55FF99;
    private static final int COLOR_ROW_TEXT = 0xFFFFFFFF;
    private static final int COLOR_PLAYER = 0xFF4A9BE8;
    private static final int COLOR_TEAM = 0xFFE8A33D;
    private static final int COLOR_ROW_BG_PLAYER = 0x33FFFFFF;
    private static final int COLOR_ROW_BG_TEAM = 0x33E8A33D;
    private static final int COLOR_ROW_BG_HOVER_PLAYER = 0x55FFFFFF;
    private static final int COLOR_ROW_BG_HOVER_TEAM = 0x55E8A33D;
    private static final int COLOR_MUTED = 0xFFAAAAAA;
    private static final int COLOR_SEPARATOR = 0xFF555555;
    private static final int COLOR_BORDER = 0xFF9A9A9A;
    private static final int COLOR_PANEL = 0xE0101010;
    private static final int COLOR_GROOVE = 0xFF2A5A3A;
    private static final int COLOR_DELETE = 0xFFE05555;
    private static final int COLOR_DELETE_HOVER = 0xFFFF8888;
    private static final int COLOR_CHECK = 0xFF7CFF9A;
    private static final int COLOR_TOOLTIP_UUID = 0xFFAAAAAA;

    private static final int LIST_TOP = 88;
    private static final int ROW_HEIGHT = 21;
    private static final int VISIBLE_ROWS = 5;
    private static final int ROW_LEFT = 10;
    private static final int ROW_RIGHT = 246;
    private static final int LABEL_RIGHT = 205;
    private static final int DELETE_LEFT = 230;
    private static final int SCROLLBAR_X = 248;
    private static final int SCROLLBAR_WIDTH = 3;

    /** Vanilla sprite reused as the team icon; the player icon is a filled square drawn with {@code fill}. */
    private static final ResourceLocation TEAM_ICON =
            ResourceLocation.withDefaultNamespace("container/enchanting_table/level_1");

    private final List<Button> headerButtons = new ArrayList<>();
    private XppStatePayload state;
    private Button modeButton;
    private Button addMeButton;
    private Button addTeamsButton;
    private int scrollOffset;
    private int hoveredRow = -1;
    private long hoveredSince;

    public XpPaymentTankScreen(XpPaymentTankMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
        this.titleLabelX = 8;
        this.titleLabelY = 8;
    }

    @Override
    protected void init() {
        super.init();
        // Positions are panel-relative, which is also the space this screen draws its own content in.
        this.modeButton = addRenderableWidget(new Button.Builder(modeLabel(),
                button -> send(XppAccessPayload.Action.SET_MODE, null,
                        state != null && state.whitelist() ? "blacklist" : "whitelist"))
                .pos(leftPos + 8, topPos + 54).size(84, 20).build());
        this.addMeButton = addRenderableWidget(new Button.Builder(
                Component.translatable("access.add_me"), button -> addSelf())
                .pos(leftPos + 100, topPos + 54).size(72, 20).build());
        this.addTeamsButton = addRenderableWidget(new Button.Builder(
                Component.translatable("access.add_team"), button -> addOwnTeams())
                .pos(leftPos + 180, topPos + 54).size(72, 20).build());
        headerButtons.clear();
        headerButtons.add(modeButton);
        headerButtons.add(addMeButton);
        headerButtons.add(addTeamsButton);
        refreshWidgets();
    }

    /** Called by the payload handler whenever the server sends a new snapshot. */
    public void onState(XppStatePayload payload) {
        this.state = payload;
        clampScroll();
        if (minecraft != null && minecraft.player != null) {
            refreshWidgets();
        }
    }

    private void refreshWidgets() {
        if (modeButton == null) {
            return;
        }
        modeButton.setMessage(modeLabel());
        // Adding "me" is only meaningful while this player is not listed yet; a greyed-out button also answers
        // "am I on the list?" without the player having to read the whole list.
        addMeButton.active = state != null && !isSelfListed(state);
        addTeamsButton.active = state != null && hasTeamToAdd(state);
        for (Button button : headerButtons) {
            button.visible = state != null;
        }
    }

    private Component modeLabel() {
        if (state == null) {
            return Component.translatable("access.mode", Component.translatable("access.mode.unknown"));
        }
        String key = state.whitelist() ? "access.mode.whitelist" : "access.mode.blacklist";
        return Component.translatable("access.mode", Component.translatable(key));
    }

    /**
     * True when the snapshot already lists this player's own UUID.
     *
     * <p>Only a row that carries an id can be the player: a legacy name-only row is not proof of identity, and
     * treating it as one would disable the button for a player who is in fact not on the list yet.
     */
    private static boolean isSelfListed(XppStatePayload payload) {
        UUID self = selfId();
        if (self == null) {
            return false;
        }
        for (XppStatePayload.Entry entry : payload.entries()) {
            if (!entry.team() && self.equals(entry.id())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static UUID selfId() {
        return net.minecraft.client.Minecraft.getInstance().player != null
                ? net.minecraft.client.Minecraft.getInstance().player.getUUID()
                : null;
    }

    private void addSelf() {
        UUID self = selfId();
        if (self == null || minecraft == null || minecraft.player == null) {
            return;
        }
        send(XppAccessPayload.Action.ADD_PLAYER, self, minecraft.player.getGameProfile().getName());
    }

    /**
     * Adds every team this player is currently in that is not on the list yet.
     *
     * <p>The server accepts a team only from one of its members, so this cannot be used to grant a team the
     * player has nothing to do with; it only saves them from adding three teams one row at a time.
     */
    private void addOwnTeams() {
        if (state == null) {
            return;
        }
        for (XppStatePayload.Entry team : state.ownTeams()) {
            if (!listedAsTeam(state, team.id())) {
                send(XppAccessPayload.Action.ADD_TEAM, team.id(), team.label());
            }
        }
    }

    /** True while at least one of the player's teams can still be added. */
    private static boolean hasTeamToAdd(XppStatePayload payload) {
        for (XppStatePayload.Entry team : payload.ownTeams()) {
            if (!listedAsTeam(payload, team.id())) {
                return true;
            }
        }
        return false;
    }

    /**
     * An id-less entry counts as listed: an old name-only row cannot be matched against, and a team without an
     * id cannot be addressed by the payload at all.
     */
    private static boolean listedAsTeam(XppStatePayload payload, @Nullable UUID id) {
        if (id == null) {
            return true;
        }
        for (XppStatePayload.Entry entry : payload.entries()) {
            if (entry.team() && id.equals(entry.id())) {
                return true;
            }
        }
        return false;
    }

    private void send(XppAccessPayload.Action action, @Nullable UUID target, String label) {
        if (state != null) {
            PacketDistributor.sendToServer(new XppAccessPayload(state.pos(), action, target, label));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Background, widgets and this screen's own content are drawn in one place so the list cannot end up
        // under a widget the player is expected to click.
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int localX = mouseX - leftPos;
        int localY = mouseY - topPos;
        updateHover(localX, localY);
        drawPool(guiGraphics);
        drawList(guiGraphics, localX, localY);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(0, 0, PANEL_WIDTH, PANEL_HEIGHT, COLOR_PANEL);
        guiGraphics.fill(0, 0, PANEL_WIDTH, 1, COLOR_BORDER);
        guiGraphics.fill(0, PANEL_HEIGHT - 1, PANEL_WIDTH, PANEL_HEIGHT, COLOR_BORDER);
        guiGraphics.fill(8, 196, 248, 197, COLOR_SEPARATOR);
    }

    private void drawPool(GuiGraphics guiGraphics) {
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, COLOR_TEXT, false);

        String fluidName = state == null || state.fluid().isEmpty()
                ? ""
                : state.fluid().getHoverName().getString();
        int amount = state == null || state.fluid().isEmpty() ? 0 : state.fluid().getAmount();
        int capacity = state == null ? 0 : state.capacityOrDefault();
        guiGraphics.drawString(font, fluidName + "  " + amount + " / " + capacity + " mB",
                8, 20, COLOR_FLUID_TEXT, false);

        String xp = state == null
                ? ""
                : Component.translatable("access.pool_xp", state.poolXp(), state.mbPerXp()).getString();
        guiGraphics.drawString(font, xp, 8, 32, COLOR_FLUID_TEXT, false);

        guiGraphics.fill(8, 44, 248, 48, COLOR_GROOVE);
        // Nothing but the groove when there is no fill to show: a zero-width bar would still be drawn as a
        // one-pixel sliver by rounding, which reads as "a little bit of fluid".
        if (state != null && capacity > 0 && amount > 0) {
            int filled = Math.max(1, Math.min(240, (int) ((long) 240 * amount / capacity)));
            guiGraphics.fill(8, 44, 8 + filled, 48, COLOR_FLUID_TEXT);
        }
    }

    private void drawList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        List<XppStatePayload.Entry> entries = state == null ? List.of() : state.entries();
        int count = state == null ? 0 : entries.size();
        guiGraphics.drawString(font, Component.translatable("access.list_header", count).getString(),
                12, 78, COLOR_MUTED, false);

        if (state == null) {
            String waiting = Component.translatable("access.waiting").getString();
            guiGraphics.drawString(font, waiting, (PANEL_WIDTH - font.width(waiting)) / 2, LIST_TOP + 30,
                    COLOR_MUTED, false);
            return;
        }
        if (entries.isEmpty()) {
            String empty = Component.translatable("access.list.empty").getString();
            guiGraphics.drawString(font, empty, (PANEL_WIDTH - font.width(empty)) / 2, LIST_TOP + 30,
                    COLOR_MUTED, false);
            return;
        }

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scrollOffset + row;
            if (index >= entries.size()) {
                break;
            }
            int y = LIST_TOP + row * ROW_HEIGHT;
            boolean deleteHovered = mouseX >= DELETE_LEFT && mouseX < DELETE_LEFT + 16
                    && mouseY >= y && mouseY < y + 20;
            drawRow(guiGraphics, entries.get(index), y, index == hoveredRow, deleteHovered);
        }
        drawScrollbar(guiGraphics, entries.size());
    }

    private void drawRow(GuiGraphics guiGraphics, XppStatePayload.Entry entry, int y, boolean hovered,
                         boolean deleteHovered) {
        int background = entry.team()
                ? (hovered ? COLOR_ROW_BG_HOVER_TEAM : COLOR_ROW_BG_TEAM)
                : (hovered ? COLOR_ROW_BG_HOVER_PLAYER : COLOR_ROW_BG_PLAYER);
        guiGraphics.fill(ROW_LEFT, y, ROW_RIGHT, y + 20, background);
        guiGraphics.fill(ROW_LEFT, y, ROW_LEFT + 3, y + 20, entry.team() ? COLOR_TEAM : COLOR_PLAYER);

        if (entry.team()) {
            guiGraphics.blitSprite(TEAM_ICON, 15, y + 6, 8, 8);
        } else {
            guiGraphics.fill(15, y + 6, 23, y + 14, COLOR_PLAYER);
        }

        String prefix = entry.team() ? "[T] " : "[P] ";
        guiGraphics.drawString(font, prefix, 27, y + 6, COLOR_ROW_TEXT, false);
        int prefixWidth = font.width(prefix);
        // The check mark has to fit before the label is measured, otherwise a long name would push it out of
        // the row and the player would lose the only cue that says "this one is you".
        int checkWidth = entry.mine() ? font.width(" \u2714") : 0;
        int labelRoom = LABEL_RIGHT - (27 + prefixWidth + checkWidth);
        guiGraphics.drawString(font, fit(entry.label(), labelRoom), 27 + prefixWidth, y + 6, COLOR_ROW_TEXT, false);
        if (entry.mine()) {
            guiGraphics.drawString(font, "\u2714", 27 + prefixWidth + font.width(fit(entry.label(), labelRoom)) + 4,
                    y + 6, COLOR_CHECK, false);
        }

        // The delete control is a hit box, not the whole row: hovering a row must never arm a removal. The click
        // is still routed through mouseClicked rather than a widget, so a drag that ends elsewhere deletes
        // nothing.
        guiGraphics.drawString(font, "\u00d7", DELETE_LEFT + (16 - font.width("\u00d7")) / 2, y + 6,
                deleteHovered ? COLOR_DELETE_HOVER : COLOR_DELETE, false);
    }

    private void drawScrollbar(GuiGraphics guiGraphics, int count) {
        if (count <= VISIBLE_ROWS) {
            return;
        }
        int trackHeight = VISIBLE_ROWS * ROW_HEIGHT;
        guiGraphics.fill(SCROLLBAR_X, LIST_TOP, SCROLLBAR_X + SCROLLBAR_WIDTH, LIST_TOP + trackHeight,
                COLOR_SEPARATOR);
        int thumbHeight = Math.max(10, trackHeight * VISIBLE_ROWS / count);
        int maxOffset = count - VISIBLE_ROWS;
        int thumbY = LIST_TOP + (trackHeight - thumbHeight) * scrollOffset / maxOffset;
        guiGraphics.fill(SCROLLBAR_X, thumbY, SCROLLBAR_X + SCROLLBAR_WIDTH, thumbY + thumbHeight, COLOR_MUTED);
    }

    /** Truncates with an ellipsis so the row keeps its delete control reachable whatever the name length is. */
    private String fit(String label, int room) {
        if (room <= 0) {
            return "";
        }
        if (font.width(label) <= room) {
            return label;
        }
        String ellipsis = "\u2026";
        int width = room - font.width(ellipsis);
        String cut = font.plainSubstrByWidth(label, Math.max(0, width));
        return cut + ellipsis;
    }

    private void updateHover(int mouseX, int mouseY) {
        int index = rowAt(mouseX, mouseY);
        if (index != hoveredRow) {
            hoveredRow = index;
            hoveredSince = Util.getMillis();
        }
    }

    private int rowAt(int mouseX, int mouseY) {
        if (state == null || mouseX < ROW_LEFT || mouseX >= ROW_RIGHT) {
            return -1;
        }
        int row = (mouseY - LIST_TOP) / ROW_HEIGHT;
        if (row < 0 || row >= VISIBLE_ROWS || mouseY - LIST_TOP - row * ROW_HEIGHT >= 20) {
            // The 1px gap between rows belongs to no row, so a click there does nothing rather than the wrong
            // neighbour's delete control.
            return -1;
        }
        int index = scrollOffset + row;
        return index < state.entries().size() ? index : -1;
    }

    private boolean inDeleteBox(int mouseX, int mouseY, int index) {
        if (rowAt(mouseX, mouseY) != index) {
            return false;
        }
        return mouseX >= DELETE_LEFT && mouseX < DELETE_LEFT + 16;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && state != null) {
            int localX = (int) mouseX - leftPos;
            int localY = (int) mouseY - topPos;
            int index = rowAt(localX, localY);
            if (index >= 0 && inDeleteBox(localX, localY, index)) {
                removeEntry(state.entries().get(index));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void removeEntry(XppStatePayload.Entry entry) {
        XppAccessPayload.Action action = entry.team()
                ? XppAccessPayload.Action.REMOVE_TEAM
                : XppAccessPayload.Action.REMOVE_PLAYER;
        // The label travels with the removal so the server can also drop a legacy entry that has no UUID.
        send(action, entry.id(), entry.label());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int count = state == null ? 0 : state.entries().size();
        if (count > VISIBLE_ROWS && scrollY != 0) {
            scrollOffset = Math.max(0, Math.min(count - VISIBLE_ROWS, scrollOffset - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void clampScroll() {
        int maxOffset = Math.max(0, (state == null ? 0 : state.entries().size()) - VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset));
        if (hoveredRow >= (state == null ? 0 : state.entries().size())) {
            hoveredRow = -1;
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (state != null && hoveredRow >= 0 && hoveredRow < state.entries().size()
                && Util.getMillis() - hoveredSince >= 500) {
            XppStatePayload.Entry entry = state.entries().get(hoveredRow);
            List<Component> lines = new ArrayList<>();
            // A legacy row has no id to show, so its bare name is all the tooltip can report - and it is the
            // only place the stored name is visible, since every other surface shows the label.
            lines.add(Component.translatable(entry.id() == null ? "access.tooltip.name" : "access.tooltip.uuid",
                    entry.id() == null ? entry.label() : entry.id().toString())
                    .withStyle(style -> style.withColor(COLOR_TOOLTIP_UUID)));
            guiGraphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
