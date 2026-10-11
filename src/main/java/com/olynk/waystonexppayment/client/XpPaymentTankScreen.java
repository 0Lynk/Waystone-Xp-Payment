package com.olynk.waystonexppayment.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.olynk.waystonexppayment.menu.XpPaymentTankMenu;
import com.olynk.waystonexppayment.net.XppAccessPayload;
import com.olynk.waystonexppayment.net.XppStatePayload;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Screen for the payment tank: the pool on top, the access list - or the picker - below.
 *
 * <p>The list and the picker are the same widget, driven by focus: an unfocused input shows the access list, a
 * focused one shows everything that can be added. Filtering is local (case-insensitive substring, prefix hits
 * first) and the name typed is never an identity: a picker row carries the server-issued UUID that gets added.
 *
 * <p>The list is drawn directly instead of being a widget tree. Rows carry a colour bar, an icon, a delete hit
 * box and a hover dwell tooltip, none of which {@code AbstractWidget} models; a widget per row would need a
 * custom widget anyway, and the list is short and fixed-height.
 *
 * <p>Every hand-drawn coordinate here is panel-relative, so {@link #render} and {@link #renderBg} both bracket
 * their drawing with a {@code translate(leftPos, topPos, 0)} - the two methods are called with a pose stack that
 * is in screen space, not panel space.
 */
public class XpPaymentTankScreen extends AbstractContainerScreen<XpPaymentTankMenu> {

    private static final int PANEL_WIDTH = XpPaymentTankMenu.PANEL_WIDTH;
    private static final int PANEL_HEIGHT = XpPaymentTankMenu.PANEL_HEIGHT;
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
    private static final int COLOR_HIGHLIGHT = 0xFF9A9A9A;

    /** Input box: the frame is drawn here, the borderless {@link EditBox} sits inside it. */
    private static final int INPUT_LEFT = 8;
    private static final int INPUT_TOP = 80;
    private static final int INPUT_RIGHT = 248;
    private static final int INPUT_BOTTOM = 101;
    private static final int INPUT_BG = 0x40000000;
    private static final int INPUT_BORDER = 0xFF555555;
    private static final int INPUT_BORDER_FOCUSED = 0xFF9A9A9A;
    private static final int INPUT_BORDER_ERROR = 0xFFE05555;
    private static final int INPUT_TEXT = 0xFFE8E8E8;
    private static final int INPUT_HINT = 0xFF8A8A8A;
    /** How long the "no match" border flash lasts, in milliseconds. */
    private static final long ERROR_FLASH_MS = 150L;

    private static final int HEADER_X = 12;
    private static final int HEADER_Y = 104;
    private static final int PANEL_RIGHT = 248;
    private static final int LIST_TOP = 114;
    private static final int ROW_HEIGHT = 21;
    /** Five rows at the designed height; a shorter-than-designed window drops to four (see {@link #visibleRows()}). */
    private static final int VISIBLE_ROWS = 5;
    private static final int VISIBLE_ROWS_SHORT = 4;
    private static final int ROW_LEFT = 8;
    private static final int ROW_RIGHT = 245;
    private static final int LABEL_RIGHT = 205;
    private static final int DELETE_X = 229;
    private static final int ICON_LEFT = 15;
    private static final int TEXT_LEFT = 27;
    private static final int SCROLLBAR_X = 245;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SEPARATOR_Y = 222;

    /** Vanilla sprite reused as the team icon; the player icon is a filled square drawn with {@code fill}. */
    private static final ResourceLocation TEAM_ICON =
            ResourceLocation.withDefaultNamespace("container/enchanting_table/level_1");

    private final List<AccessButton> headerButtons = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<Row> candidates = new ArrayList<>();
    private final List<Row> found = new ArrayList<>();
    private XppStatePayload state;
    private AccessButton modeButton;
    private AccessButton addMeButton;
    private AccessButton addTeamsButton;
    private EditBox searchBox;
    private int scrollOffset;
    private int hoverRow = -1;
    private int highlightRow = -1;
    private long hoveredSince;
    private long errorSince;
    private String lastQuery = "";

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
        this.modeButton = addRenderableWidget(new AccessButton(modeLabel(), COLOR_MUTED,
                button -> send(XppAccessPayload.Action.SET_MODE, null,
                        state != null && state.whitelist() ? "blacklist" : "whitelist")));
        this.modeButton.setPosition(leftPos + 8, topPos + 54);
        this.modeButton.setSize(84, 20);
        this.addMeButton = addRenderableWidget(new AccessButton(Component.translatable("access.add_me"),
                COLOR_PLAYER, button -> addSelf()));
        this.addMeButton.setPosition(leftPos + 96, topPos + 54);
        this.addMeButton.setSize(72, 20);
        this.addTeamsButton = addRenderableWidget(new AccessButton(Component.translatable("access.add_team"),
                COLOR_TEAM, button -> addOwnTeams()));
        this.addTeamsButton.setPosition(leftPos + 176, topPos + 54);
        this.addTeamsButton.setSize(72, 20);

        this.searchBox = new EditBox(font, leftPos + 14, topPos + 87, 228, 9,
                Component.translatable("access.picker.hint"));
        // The frame is drawn by this screen, so the widget must not draw its own sprite inside it.
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(32);
        this.searchBox.setTextColor(INPUT_TEXT);
        this.searchBox.setTextColorUneditable(INPUT_TEXT);
        this.searchBox.setHint(Component.translatable("access.picker.hint"));
        addRenderableWidget(searchBox);

        headerButtons.clear();
        headerButtons.add(modeButton);
        headerButtons.add(addMeButton);
        headerButtons.add(addTeamsButton);
        refreshWidgets();
    }

    /** Called by the payload handler whenever the server sends a new snapshot. */
    public void onState(XppStatePayload payload) {
        this.state = payload;
        rebuildRows();
        if (minecraft != null && minecraft.player != null) {
            refreshWidgets();
        }
    }

    /**
     * Rebuilds both lists and re-applies the filter.
     *
     * <p>The candidates arrive with the snapshot already sorted by the server, so they only have to be split
     * into the two colour groups here - players first, then teams, which is the order they are drawn in.
     */
    private void rebuildRows() {
        rows.clear();
        candidates.clear();
        if (state != null) {
            for (XppStatePayload.Entry entry : state.entries()) {
                rows.add(new Row(entry, true));
            }
            for (XppStatePayload.Entry entry : state.candidatesPlayers()) {
                candidates.add(new Row(entry, false));
            }
            for (XppStatePayload.Entry entry : state.candidatesTeams()) {
                candidates.add(new Row(entry, false));
            }
        }
        int previousHighlight = highlightRow;
        int previousScroll = scrollOffset;
        lastQuery = null;
        applyFilter();
        // A new snapshot is usually the answer to the edit the player just made, so the scroll position and the
        // keyboard highlight are kept unless the shortened list no longer has room for them.
        highlightRow = previousHighlight;
        scrollOffset = previousScroll;
        clampScroll();
    }

    /**
     * Local, case-insensitive substring filter with prefix matches first.
     *
     * <p>The text never leaves the client: the picker row the player clicks carries the UUID the server issued,
     * so a typo can only ever hide a row, never create an identity.
     */
    private void applyFilter() {
        String query = searchBox == null ? "" : searchBox.getValue().trim();
        if (query.equals(lastQuery)) {
            return;
        }
        // A new query is a new list; keeping the old highlight would point at an unrelated row.
        highlightRow = -1;
        scrollOffset = 0;
        lastQuery = query;
        found.clear();
        if (query.isEmpty()) {
            found.addAll(candidates);
        } else {
            String needle = query.toLowerCase(java.util.Locale.ROOT);
            List<Row> prefix = new ArrayList<>();
            List<Row> contains = new ArrayList<>();
            for (Row row : candidates) {
                String label = row.label.toLowerCase(java.util.Locale.ROOT);
                if (label.startsWith(needle)) {
                    prefix.add(row);
                } else if (label.contains(needle)) {
                    contains.add(row);
                }
            }
            prefix.sort(Comparator.comparing(row -> row.label, String.CASE_INSENSITIVE_ORDER));
            contains.sort(Comparator.comparing(row -> row.label, String.CASE_INSENSITIVE_ORDER));
            found.addAll(prefix);
            found.addAll(contains);
        }
        if (highlightRow >= found.size()) {
            highlightRow = -1;
        }
        clampScroll();
    }

    /** True while the picker is the active mode: a focused input shows candidates, an idle one shows the list. */
    private boolean picking() {
        return searchBox != null && searchBox.isFocused();
    }

    /** The rows currently on screen, in draw order. */
    private List<Row> shownRows() {
        return picking() ? found : rows;
    }

    /** How many rows fit: five at the designed panel height, four in a shorter window (see the class javadoc). */
    private int rowCapacity() {
        return height < PANEL_HEIGHT ? VISIBLE_ROWS_SHORT : VISIBLE_ROWS;
    }

    private boolean canEdit() {
        return state != null && state.canEdit();
    }

    private void refreshWidgets() {
        if (modeButton == null) {
            return;
        }
        modeButton.setMessage(modeLabel());
        // An unclaimed tank is editable, so the buttons must be live before the first edit claims it.
        boolean editable = canEdit();
        for (AccessButton button : headerButtons) {
            button.visible = state != null && editable;
        }
        // Read-only hides the controls instead of greying them out: there is no edit to make, and a disabled
        // row of buttons would suggest otherwise.
        searchBox.visible = state != null && editable;
        if (!searchBox.visible && searchBox.isFocused()) {
            clearSearch();
        }
        // Adding "me" is only meaningful while this player is not listed yet; a greyed-out button also answers
        // "am I on the list?" without the player having to read the whole list.
        addMeButton.active = !isSelfListed();
        addTeamsButton.active = !state.ownTeams().isEmpty();
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
    private boolean isSelfListed() {
        UUID self = selfId();
        if (self == null || state == null) {
            return false;
        }
        for (XppStatePayload.Entry entry : state.entries()) {
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
        if (self != null) {
            send(XppAccessPayload.Action.ADD_PLAYER, self, "");
        }
    }

    /**
     * Adds every team this player is currently in that is not on the list yet.
     *
     * <p>The teams come from the snapshot, not from a local team mod lookup, so this works on a client that has
     * no FTB Teams of its own.
     */
    private void addOwnTeams() {
        if (state == null) {
            return;
        }
        for (XppStatePayload.Entry team : state.ownTeams()) {
            if (!listedAsTeam(team.id())) {
                send(XppAccessPayload.Action.ADD_TEAM, team.id(), team.label());
            }
        }
    }

    /**
     * An id-less entry counts as listed: an old name-only row cannot be matched against, and a team without an
     * id cannot be addressed by the payload at all.
     */
    private boolean listedAsTeam(@Nullable UUID id) {
        if (id == null || state == null) {
            return true;
        }
        for (XppStatePayload.Entry entry : state.entries()) {
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
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int localX = mouseX - leftPos;
        int localY = mouseY - topPos;
        updateHover(localX, localY);

        // Hand-drawn content uses panel-relative coordinates, so the pose has to be moved onto the panel first -
        // render() hands us screen space (vanilla's own renderBg does the same translate).
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(leftPos, topPos, 0);
        drawPool(guiGraphics);
        drawList(guiGraphics, localX, localY);
        guiGraphics.pose().popPose();

        // Own tooltips come after the widget tooltips: AbstractWidget queues its tooltip to be flushed by
        // Screen.renderTooltip, which would otherwise paint over the row tooltip drawn here.
        super.renderTooltip(guiGraphics, mouseX, mouseY);
        renderRowTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(leftPos, topPos, 0);
        guiGraphics.fill(0, 0, PANEL_WIDTH, PANEL_HEIGHT, COLOR_PANEL);
        guiGraphics.fill(0, 0, PANEL_WIDTH, 1, COLOR_BORDER);
        guiGraphics.fill(0, PANEL_HEIGHT - 1, PANEL_WIDTH, PANEL_HEIGHT, COLOR_BORDER);
        guiGraphics.fill(ROW_LEFT, SEPARATOR_Y, PANEL_RIGHT, SEPARATOR_Y + 1, COLOR_SEPARATOR);
        guiGraphics.pose().popPose();
    }

    /**
     * No vanilla labels: the title is drawn by {@link #drawPool} and an item-inventory label would describe a
     * screen that has no slots.
     */
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
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

        if (searchBox != null && searchBox.visible) {
            drawInputFrame(guiGraphics);
        }
    }

    /** The input frame is drawn whether or not the box is visible, so read-only mode shows no empty hole. */
    private void drawInputFrame(GuiGraphics guiGraphics) {
        if (searchBox == null) {
            return;
        }
        boolean error = errorSince > 0 && Util.getMillis() - errorSince < ERROR_FLASH_MS;
        int border = error ? INPUT_BORDER_ERROR
                : searchBox.isFocused() ? INPUT_BORDER_FOCUSED : INPUT_BORDER;
        guiGraphics.fill(INPUT_LEFT, INPUT_TOP, INPUT_RIGHT, INPUT_BOTTOM, INPUT_BG);
        guiGraphics.fill(INPUT_LEFT, INPUT_TOP, INPUT_RIGHT, INPUT_TOP + 1, border);
        guiGraphics.fill(INPUT_LEFT, INPUT_BOTTOM - 1, INPUT_RIGHT, INPUT_BOTTOM, border);
        guiGraphics.fill(INPUT_LEFT, INPUT_TOP, INPUT_LEFT + 1, INPUT_BOTTOM, border);
        guiGraphics.fill(INPUT_RIGHT - 1, INPUT_TOP, INPUT_RIGHT, INPUT_BOTTOM, border);
    }

    private void drawList(GuiGraphics guiGraphics, int mouseLocalX, int mouseLocalY) {
        int rowsVisible = rowCapacity();
        boolean editable = canEdit();

        drawHeader(guiGraphics);

        if (state == null) {
            drawCentered(guiGraphics, Component.translatable("access.waiting").getString(), rowsVisible);
            return;
        }
        if (!editable) {
            String owner = state.ownerName().isEmpty()
                    ? Component.translatable("access.owner.none").getString()
                    : state.ownerName();
            drawCentered(guiGraphics, Component.translatable("access.readonly", owner).getString(), rowsVisible);
            return;
        }

        List<Row> list = shownRows();
        if (list.isEmpty()) {
            String key = pickerKey();
            if ("access.picker.no_match".equals(key)) {
                errorSince = Util.getMillis();
            }
            drawCentered(guiGraphics, Component.translatable(key).getString(), rowsVisible);
            return;
        }
        if (errorSince > 0 && Util.getMillis() - errorSince >= ERROR_FLASH_MS) {
            errorSince = 0;
        }
        drawCategoryHint(guiGraphics, list);

        for (int row = 0; row < rowsVisible; row++) {
            int index = scrollOffset + row;
            if (index >= list.size()) {
                break;
            }
            Row entry = list.get(index);
            int y = LIST_TOP + row * ROW_HEIGHT;
            boolean hovered = mouseLocalY >= y && mouseLocalY < y + 20
                    && mouseLocalX >= ROW_LEFT && mouseLocalX < ROW_RIGHT;
            boolean deleteHovered = canDelete(entry) && mouseLocalX >= DELETE_X && mouseLocalX < ROW_RIGHT
                    && mouseLocalY >= y && mouseLocalY < y + 20;
            drawRow(guiGraphics, entry, y, hovered, deleteHovered, index);
        }
        drawScrollbar(guiGraphics, list.size());
    }

    /**
     * Message key for an empty row area.
     *
     * <p>An empty result from a typed query is a different message than "this server has no teams at all", and
     * the two are otherwise indistinguishable on screen.
     */
    private String pickerKey() {
        if (!picking()) {
            return "access.list.empty";
        }
        if (searchBox.getValue().trim().isEmpty()) {
            return "access.picker.no_candidates";
        }
        return "access.picker.no_match";
    }

    /**
     * Notes the one empty colour group while the other still has rows.
     *
     * <p>The column split is the point of the picker, so "no online player matches" has to be said even while
     * teams are listed - otherwise a filter that hides every player looks like a filter that found teams only.
     */
    private void drawCategoryHint(GuiGraphics guiGraphics, List<Row> list) {
        if (!picking()) {
            return;
        }
        boolean players = false;
        boolean teams = false;
        for (Row row : list) {
            if (row.team) {
                teams = true;
            } else {
                players = true;
            }
        }
        if (players == teams) {
            return;
        }
        String key = players ? "access.picker.no_teams" : "access.picker.no_players";
        guiGraphics.drawString(font, Component.translatable(key).getString(), HEADER_X, HEADER_Y + 9,
                COLOR_MUTED, false);
    }

    private void drawHeader(GuiGraphics guiGraphics) {
        boolean picker = picking();
        String header;
        if (picker) {
            int players = 0;
            int teams = 0;
            for (Row row : found) {
                if (row.team) {
                    teams++;
                } else {
                    players++;
                }
            }
            header = Component.translatable("access.picker.header", players, teams).getString();
        } else {
            header = Component.translatable("access.list_header", state == null ? 0 : state.entries().size())
                    .getString();
        }
        guiGraphics.drawString(font, header, HEADER_X, HEADER_Y, COLOR_MUTED, false);

        if (!picker && state != null) {
            // The owner is shown next to the list header, not instead of it: it answers "whose tank is this"
            // without taking the row area away from the list.
            Component owner = state.ownerName().isEmpty()
                    ? Component.translatable("access.owner.none")
                    : Component.translatable("access.owner", state.ownerName());
            String text = owner.getString();
            guiGraphics.drawString(font, text, PANEL_RIGHT - font.width(text), HEADER_Y, COLOR_MUTED, false);
        }
    }

    private void drawCentered(GuiGraphics guiGraphics, String text, int rowsVisible) {
        int centerY = LIST_TOP + (rowsVisible * ROW_HEIGHT) / 2 - 4;
        guiGraphics.drawString(font, text, (PANEL_WIDTH - font.width(text)) / 2, centerY, COLOR_MUTED, false);
    }

    private void drawRow(GuiGraphics guiGraphics, Row entry, int y, boolean hovered, boolean deleteHovered,
                         int index) {
        int background = entry.team
                ? (hovered ? COLOR_ROW_BG_HOVER_TEAM : COLOR_ROW_BG_TEAM)
                : (hovered ? COLOR_ROW_BG_HOVER_PLAYER : COLOR_ROW_BG_PLAYER);
        guiGraphics.fill(ROW_LEFT, y, ROW_RIGHT, y + 20, background);
        guiGraphics.fill(ROW_LEFT, y, ROW_LEFT + 3, y + 20, entry.team ? COLOR_TEAM : COLOR_PLAYER);

        if (entry.team) {
            guiGraphics.blitSprite(TEAM_ICON, ICON_LEFT, y + 6, 8, 8);
        } else {
            guiGraphics.fill(ICON_LEFT, y + 6, ICON_LEFT + 8, y + 14, COLOR_PLAYER);
        }

        String prefix = entry.team ? "[T] " : "[P] ";
        guiGraphics.drawString(font, prefix, TEXT_LEFT, y + 6, COLOR_ROW_TEXT, false);
        int prefixWidth = font.width(prefix);
        // The check mark has to fit before the label is measured, otherwise a long name would push it out of
        // the row and the player would lose the only cue that says "this one is you".
        int checkWidth = entry.mine ? font.width(" \u2714") : 0;
        int labelRoom = LABEL_RIGHT - (TEXT_LEFT + prefixWidth + checkWidth);
        String label = fit(entry.label, Math.max(0, labelRoom));
        guiGraphics.drawString(font, label, TEXT_LEFT + prefixWidth, y + 6, COLOR_ROW_TEXT, false);
        if (entry.mine) {
            guiGraphics.drawString(font, "\u2714", TEXT_LEFT + prefixWidth + font.width(label) + 4, y + 6,
                    COLOR_CHECK, false);
        }

        // The delete control is a hit box, not the whole row: hovering a row must never arm a removal. The click
        // is still routed through mouseClicked rather than a widget, so a drag that ends elsewhere deletes
        // nothing. Picker rows have nothing to delete, so they never draw it.
        if (canDelete(entry)) {
            guiGraphics.drawString(font, "\u00d7", DELETE_X + (16 - font.width("\u00d7")) / 2, y + 6,
                    deleteHovered ? COLOR_DELETE_HOVER : COLOR_DELETE, false);
        }

        // Two independent cues: the keyboard highlight survives the mouse leaving, and the mouse hover survives
        // the keyboard highlight being elsewhere.
        if (index == highlightRow) {
            guiGraphics.renderOutline(ROW_LEFT, y, ROW_RIGHT - ROW_LEFT - 8, 20, COLOR_HIGHLIGHT);
        }
    }

    private void drawScrollbar(GuiGraphics guiGraphics, int count) {
        int rowsVisible = rowCapacity();
        if (count <= rowsVisible) {
            return;
        }
        int trackHeight = rowsVisible * ROW_HEIGHT;
        guiGraphics.fill(SCROLLBAR_X, LIST_TOP, SCROLLBAR_X + SCROLLBAR_WIDTH, LIST_TOP + trackHeight,
                COLOR_SEPARATOR);
        int thumbHeight = Math.max(10, trackHeight * rowsVisible / count);
        int maxOffset = count - rowsVisible;
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

    private void updateHover(int mouseLocalX, int mouseLocalY) {
        int index = rowAt(mouseLocalX, mouseLocalY);
        if (index != hoverRow) {
            hoverRow = index;
            hoveredSince = Util.getMillis();
        }
    }

    /**
     * Index into the visible list under the pointer, or -1.
     *
     * <p>The pointer only reaches the list body: the input frame, the header and the buttons are outside it.
     */
    private int rowAt(int mouseLocalX, int mouseLocalY) {
        if (state == null || !canEdit() || inInputArea(mouseLocalX, mouseLocalY)
                || mouseLocalX < ROW_LEFT || mouseLocalX >= ROW_RIGHT) {
            return -1;
        }
        int row = (mouseLocalY - LIST_TOP) / ROW_HEIGHT;
        if (row < 0 || row >= rowCapacity() || mouseLocalY - LIST_TOP - row * ROW_HEIGHT >= 20) {
            // The 1px gap between rows belongs to no row, so a click there does nothing rather than the wrong
            // neighbour's delete control.
            return -1;
        }
        int index = scrollOffset + row;
        return index < shownRows().size() ? index : -1;
    }

    /** True inside the drawn input frame, padding included - the frame is the click target, not the widget. */
    private boolean inInputArea(int mouseLocalX, int mouseLocalY) {
        return searchBox != null && searchBox.visible
                && mouseLocalX >= INPUT_LEFT && mouseLocalX < INPUT_RIGHT
                && mouseLocalY >= INPUT_TOP && mouseLocalY < INPUT_BOTTOM;
    }

    private boolean canDelete(Row row) {
        return row.entry && canEdit();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && state != null) {
            int localX = (int) mouseX - leftPos;
            int localY = (int) mouseY - topPos;
            if (inInputArea(localX, localY)) {
                // Clicking anywhere on the frame, padding included, focuses the box: the drawn frame is the
                // control, and a 9px-tall borderless EditBox is a hard target on its own.
                searchBox.setFocused(true);
                return true;
            }
            int index = rowAt(localX, localY);
            if (index >= 0) {
                List<Row> list = shownRows();
                Row row = list.get(index);
                if (canDelete(row) && localX >= DELETE_X) {
                    // The delete hot zone wins over the row body: it is the smaller target and the only one
                    // that destroys something.
                    removeEntry(row.entryData);
                    return true;
                }
                if (!row.entry) {
                    addCandidate(row.entryData);
                    return true;
                }
                return true;
            }
            // Clicking the empty part of the list leaves the picker and shows the access list again.
            if (picking()) {
                clearSearch();
                return true;
            }
            searchBox.setFocused(false);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void addCandidate(XppStatePayload.Entry candidate) {
        if (candidate.id() == null) {
            return;
        }
        send(candidate.team() ? XppAccessPayload.Action.ADD_TEAM : XppAccessPayload.Action.ADD_PLAYER,
                candidate.id(), candidate.label());
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
        int localX = (int) mouseX - leftPos;
        int localY = (int) mouseY - topPos;
        int count = shownRows().size();
        if (count > rowCapacity() && scrollY != 0 && localX >= ROW_LEFT && localX < SCROLLBAR_X
                && localY >= LIST_TOP && localY < LIST_TOP + rowCapacity() * ROW_HEIGHT) {
            scrollOffset = Math.max(0,
                    Math.min(count - rowCapacity(), scrollOffset - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The EditBox returns false for ESC/ENTER/TAB and the arrows, so they reach this method and can be
        // claimed here before vanilla turns them into a screen close or a focus change. TAB is deliberately not
        // claimed: it is the vanilla focus cycle.
        if (searchBox == null) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // Two-stage: the first ESC leaves the picker, the next one closes the screen.
            if (searchBox.isFocused() || !searchBox.getValue().isEmpty()) {
                clearSearch();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            List<Row> list = shownRows();
            if (picking() && highlightRow >= 0 && highlightRow < list.size() && !list.get(highlightRow).entry) {
                addCandidate(list.get(highlightRow).entryData);
            }
            return true;
        }
        if (picking() && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
            moveHighlight(keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1);
            return true;
        }
        if (searchBox.isFocused() && searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            applyFilter();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void moveHighlight(int delta) {
        List<Row> list = shownRows();
        if (list.isEmpty()) {
            highlightRow = -1;
            return;
        }
        int next = highlightRow < 0 ? (delta > 0 ? 0 : list.size() - 1) : highlightRow + delta;
        highlightRow = Math.max(0, Math.min(list.size() - 1, next));
        // Scroll by the single row that the highlight moved, so a keyboard walk through the list keeps the
        // highlighted row on screen without jumping a page at a time.
        if (highlightRow < scrollOffset) {
            scrollOffset = highlightRow;
        } else if (highlightRow >= scrollOffset + rowCapacity()) {
            scrollOffset = highlightRow - rowCapacity() + 1;
        }
        clampScroll();
    }

    /** Leaves the picker: clears the query, drops focus and shows the access list again. */
    private void clearSearch() {
        searchBox.setValue("");
        searchBox.setFocused(false);
        lastQuery = "";
        highlightRow = -1;
        scrollOffset = 0;
        applyFilter();
    }

    private void clampScroll() {
        int maxOffset = Math.max(0, shownRows().size() - rowCapacity());
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset));
        if (hoverRow >= shownRows().size()) {
            hoverRow = -1;
        }
        if (highlightRow >= shownRows().size()) {
            highlightRow = -1;
        }
    }

    private void renderRowTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        List<Row> list = shownRows();
        if (state == null || hoverRow < 0 || hoverRow >= list.size()
                || Util.getMillis() - hoveredSince < 500) {
            return;
        }
        // A tooltip while the pointer is on a button or in the input frame would sit on top of the control the
        // player is about to use; a tooltip on the row the keyboard already highlights would only repeat it.
        if (mouseX >= leftPos && mouseY >= topPos) {
            int localX = mouseX - leftPos;
            int localY = mouseY - topPos;
            if (inInputArea(localX, localY)) {
                return;
            }
            for (AccessButton button : headerButtons) {
                if (button.visible && mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                        && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight()) {
                    return;
                }
            }
        }
        if (picking() && hoverRow == highlightRow) {
            return;
        }
        Row row = list.get(hoverRow);
        // The row already shows the name, so the tooltip adds the identity behind it: the raw UUID, which is what
        // a server admin needs to paste into a command. A legacy row has no id at all, so its name is all there is.
        String value = row.entryData.id() == null ? row.label : row.entryData.id().toString();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(value).withStyle(style -> style.withColor(COLOR_TOOLTIP_UUID)));
        guiGraphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    /** One drawn row: either an entry of the access list or a picker candidate. */
    private static final class Row {

        private final XppStatePayload.Entry entryData;
        private final String label;
        private final boolean team;
        private final boolean mine;
        /** True for an access-list entry, false for a picker candidate. */
        private final boolean entry;

        private Row(XppStatePayload.Entry entryData, boolean entry) {
            this.entryData = entryData;
            this.label = entryData.label();
            this.team = entryData.team();
            this.mine = entryData.mine();
            this.entry = entry;
        }
    }
}
