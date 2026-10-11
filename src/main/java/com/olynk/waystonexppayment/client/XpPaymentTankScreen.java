package com.olynk.waystonexppayment.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.olynk.waystonexppayment.menu.XpPaymentTankMenu;
import com.olynk.waystonexppayment.net.XppAccessPayload;
import com.olynk.waystonexppayment.net.XppMembersPayload;
import com.olynk.waystonexppayment.net.XppMembersStatus;
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
 * Screen for the payment tank: the pool on top, the access list - or the picker, or a team's members - below.
 *
 * <p>The list and the picker are the same widget, driven by focus: an unfocused input shows the access list, a
 * focused one shows everything that can be added. Filtering is local (case-insensitive substring, prefix hits
 * first) and the name typed is never an identity: a picker row carries the server-issued UUID that gets added.
 *
 * <p>Nothing on a row is destructive on its own. A left click selects, a right click opens a small menu whose
 * items are the only way to add or remove an entry, and the row's trailing slot - once a delete button - now
 * opens that same menu. The shape keeps "select" and "act" as separate gestures, so no click can lose data.
 *
 * <p>The list is drawn directly instead of being a widget tree. Rows carry a colour bar, an icon, a menu hit
 * box and a hover dwell tooltip, none of which {@code AbstractWidget} models; a widget per row would need a
 * custom widget anyway, and the list is short and fixed-height. Only the input box and the two controls that
 * have no row (the header buttons, the member panel's back control) are real widgets.
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
    private static final int COLOR_CHECK = 0xFF7CFF9A;
    private static final int COLOR_TOOLTIP_UUID = 0xFFAAAAAA;
    private static final int COLOR_HIGHLIGHT = 0xFF9A9A9A;
    private static final int COLOR_OK = 0xFF7CFF9A;
    private static final int COLOR_REJECTED = 0xFFE05555;
    private static final int COLOR_TIMEOUT = 0xFFE8A33D;
    private static final int COLOR_OFFLINE_TEXT = 0xFF8A8A8A;
    private static final int COLOR_MENU_BG = 0xF0101010;
    private static final int COLOR_MENU_SHADOW = 0x66000000;
    private static final int COLOR_MENU_HOVER = 0x55FFFFFF;
    private static final int COLOR_MENU_DANGER_TEXT = 0xFFFF8888;
    private static final int COLOR_MENU_DANGER_HOVER = 0x44E05555;
    private static final int COLOR_MENU_DISABLED_TEXT = 0xFF8A8A8A;
    private static final int COLOR_MENU_DISABLED_BG = 0x14FFFFFF;
    private static final int COLOR_MENU_DISABLED_BORDER = 0xFF3A3A3A;
    /** Mirrors {@code AccessButton.ACCENT_DISABLED}: a disabled icon keeps its hue at reduced opacity. */
    private static final int MENU_ACCENT_DISABLED = 0x40FFFFFF;

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

    /**
     * Container frame: one border around the input, the header row, the row area and the status strip.
     *
     * <p>The four pieces are separate drawings with their own edges, and without this they read as four
     * floating strips rather than as the one block the player is meant to work in (spec §1 P0-6). It sits
     * outside the input frame on purpose: the input keeps its own border, which is what carries its focus
     * and error states.
     */
    private static final int FRAME_LEFT = 5;
    private static final int FRAME_TOP = 76;
    private static final int FRAME_RIGHT = 251;
    private static final int FRAME_BOTTOM = 238;
    private static final int FRAME_BG = 0x14000000;
    private static final int FRAME_BORDER = 0xFF3A3A3A;
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
    /** Full row width, shared by the selection frame and the keyboard highlight so they can be told apart. */
    private static final int ROW_FRAME_WIDTH = ROW_RIGHT - ROW_LEFT;
    private static final int LABEL_RIGHT = 205;
    private static final int DELETE_X = 229;
    private static final int ICON_LEFT = 15;
    private static final int TEXT_LEFT = 27;
    private static final int SCROLLBAR_X = 245;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SEPARATOR_Y = 222;

    /** Status strip below the separator: standing information left, the last answer's copy right. */
    private static final int STATUS_TEXT_Y = 226;
    private static final int STATUS_LEFT_X = 8;
    private static final int STATUS_SPLIT_X = 128;
    private static final int STATUS_RIGHT_EDGE = 244;

    /** Centres of the three in-flight dots at a row's right end, between the label and the menu slot. */
    private static final int[] PENDING_DOT_X = {209, 214, 219};
    private static final long PENDING_TIMEOUT_MS = 3000L;
    private static final long MEMBERS_TIMEOUT_MS = 3000L;
    private static final long STATUS_OK_MS = 1800L;
    private static final long STATUS_ERROR_MS = 3000L;
    private static final long TOOLTIP_DELAY_MS = 500L;

    /** Sliding progress bar of the loading state: its track spans the row area, the slider runs along it. */
    private static final int PROGRESS_TOP = 178;
    private static final int PROGRESS_BOTTOM = 180;
    private static final int PROGRESS_SLIDER = 48;
    private static final long PROGRESS_PERIOD_MS = 1200L;

    private static final int BACK_X = 232;
    private static final int BACK_Y = 102;
    private static final int BACK_SIZE = 12;
    /** How far the member header may reach before it would run under the back control (232 - 6). */
    private static final int MEMBERS_HEADER_RIGHT = 226;

    /**
     * The row's trailing slot, drawn where the delete button used to be.
     *
     * <p>U+22EF (the single midline ellipsis the design asked for) is NOT in the vanilla 1.21.1 font atlas -
     * the glyph sheet's ranges were checked and it is absent, and the unifont fallback provider ships empty -
     * so the same three dots are composed from U+00B7, which is covered and sits at the same height.
     */
    private static final String ROW_MENU_GLYPH = "\u00b7\u00b7\u00b7";
    private static final String MEMBER_DOT_ONLINE = "\u25cf";
    private static final String MEMBER_DOT_OFFLINE = "\u25cb";
    private static final String MEMBER_PREFIX = "[P] ";

    /** Vanilla sprite reused as the team icon; the player icon is a filled square drawn with {@code fill}. */
    private static final ResourceLocation TEAM_ICON =
            ResourceLocation.withDefaultNamespace("container/enchanting_table/level_1");

    /** Which of the two row sources fills the row area. */
    private enum View {
        LIST, MEMBERS
    }

    /** Which list an open menu was anchored to, so it can be re-read after the underlying state changes. */
    private enum Source {
        LIST, PICKER
    }

    /** What the status strip's right segment currently says. */
    private enum StatusKind {
        NONE, SENDING, OK, REJECTED, TIMEOUT
    }

    private final List<AccessButton> headerButtons = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<Row> candidates = new ArrayList<>();
    private final List<Row> found = new ArrayList<>();
    private final List<XppMembersPayload.Member> members = new ArrayList<>();
    private XppStatePayload state;
    private AccessButton modeButton;
    private AccessButton addMeButton;
    private AccessButton addTeamsButton;
    private AccessButton backButton;
    private EditBox searchBox;
    private int scrollOffset;
    private int hoverRow = -1;
    private int highlightRow = -1;
    private long hoveredSince;
    private long errorSince;
    private String lastQuery = "";

    private View view = View.LIST;

    /** Single selection (see the class javadoc): at most one row is selected at a time. */
    @Nullable
    private Selection selected;

    private boolean menuOpen;
    private Source menuSource = Source.LIST;
    private int menuRow = -1;
    /** -1 until an arrow key picks an item: opening a menu never preselects the destructive one. */
    private int menuHighlight = -1;
    @Nullable
    private ContextMenuLayout.Placement menuPlacement;

    @Nullable
    private PendingRequest pending;
    private StatusKind statusKind = StatusKind.NONE;
    private long statusSince;

    private int membersScroll;
    private int membersHighlight = -1;
    private int memberHover = -1;
    @Nullable
    private UUID selectedMember;
    @Nullable
    private UUID membersTeamId;
    private String membersTeamLabel = "";
    private byte membersStatus = XppMembersStatus.OK;
    private boolean membersLoading;
    private boolean membersTimedOut;
    private long membersSentAt;
    /** Id of the member request the panel is showing; a reply carrying anything else is an older one. */
    private long membersRequestId;
    /** Seeded from the clock so a reopened screen cannot collide with a request id of an earlier one. */
    private long requestCounter = Util.getMillis();

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
                button -> sendEdit(XppAccessPayload.Action.SET_MODE, null,
                        state != null && state.whitelist() ? "blacklist" : "whitelist",
                        state == null || !state.whitelist())));
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

        // A real widget rather than a hand-drawn hit box: without one the member panel would have no control
        // the TAB key could reach, which is the only keyboard route back to the list.
        this.backButton = addRenderableWidget(new AccessButton(Component.translatable("access.members.back"),
                COLOR_MUTED, button -> showList(), "<"));
        this.backButton.setPosition(leftPos + BACK_X, topPos + BACK_Y);
        this.backButton.setSize(BACK_SIZE, BACK_SIZE);

        headerButtons.clear();
        headerButtons.add(modeButton);
        headerButtons.add(addMeButton);
        headerButtons.add(addTeamsButton);
        refreshWidgets();
    }

    /** Called by the payload handler whenever the server sends a new snapshot. */
    public void onState(XppStatePayload payload) {
        this.state = payload;
        // The rows are about to be rebuilt, so the row an open menu was anchored to may not exist any more;
        // acting on a stale index is how the wrong entry gets removed.
        closeMenu();
        evaluatePending(payload);
        rebuildRows();
        if (minecraft != null && minecraft.player != null) {
            refreshWidgets();
        }
    }

    /** Called by the payload handler when the server answers a member request. */
    public void onMembers(XppMembersPayload payload) {
        // A reply to an earlier request for the same team is indistinguishable from this one's without the id,
        // and acting on it would end the loading state at the wrong moment.
        if (view != View.MEMBERS || payload.requestId() != membersRequestId) {
            return;
        }
        membersLoading = false;
        membersTimedOut = false;
        membersStatus = payload.status();
        if (!payload.teamLabel().isEmpty()) {
            // The server's name is authoritative: the team may have been renamed since the client's snapshot.
            membersTeamLabel = payload.teamLabel();
        }
        members.clear();
        members.addAll(payload.members());
        membersScroll = 0;
        membersHighlight = -1;
        selectedMember = null;
        clampMembersScroll();
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
        return view == View.LIST && searchBox != null && searchBox.isFocused();
    }

    /** The rows currently on screen, in draw order. */
    private List<Row> shownRows() {
        return picking() ? found : rows;
    }

    /** The list an open menu is reading from, fixed at the moment it was opened. */
    private List<Row> menuRows() {
        return menuSource == Source.PICKER ? found : rows;
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
        // Read-only drops the member panel and cancels whatever was in flight: the answer it was waiting for
        // can only be a refusal now, and a refusal would leave the status strip explaining itself forever.
        if (state != null && !state.canEdit()) {
            closeMenu();
            exitMembers();
            clearPending();
        }
        // An unclaimed tank is editable, so the buttons must be live before the first edit claims it.
        boolean editable = canEdit();
        for (AccessButton button : headerButtons) {
            button.visible = state != null && editable;
        }
        // Read-only hides the controls instead of greying them out: there is no edit to make, and a disabled
        // row of buttons would suggest otherwise.
        // The member panel hides it too: the list is not on screen, and a filter would have nothing to filter.
        searchBox.visible = state != null && editable && view == View.LIST;
        if (!searchBox.visible && searchBox.isFocused()) {
            clearSearch();
        }

        boolean backVisible = view == View.MEMBERS && state != null;
        if (backButton != null) {
            // Active as well as visible: vanilla's focus cycle walks active widgets, so leaving it active while
            // hidden would quietly add a TAB stop that is not on screen.
            backButton.visible = backVisible;
            backButton.active = backVisible;
        }

        // One edit at a time: everything that starts one is off until the answer or the timeout arrives, which
        // is also what keeps the pending registry a single slot.
        boolean writesAllowed = editable && pending == null;
        modeButton.active = writesAllowed;
        // Adding "me" is only meaningful while this player is not listed yet; a greyed-out button also answers
        // "am I on the list?" without the player having to read the whole list.
        addMeButton.active = writesAllowed && !isSelfListed();
        // init() runs from the open-screen packet, which arrives before the state packet: on the first init the
        // snapshot is still null, and reading it here threw inside the packet handler - which NeoForge reports
        // as a protocol error and which disconnects the player, single player included.
        addTeamsButton.active = writesAllowed && state != null && !state.ownTeams().isEmpty();
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
        return self != null && listedAsPlayer(self);
    }

    @Nullable
    private static UUID selfId() {
        return net.minecraft.client.Minecraft.getInstance().player != null
                ? net.minecraft.client.Minecraft.getInstance().player.getUUID()
                : null;
    }

    private boolean listedAsPlayer(UUID playerId) {
        if (state == null) {
            return false;
        }
        for (XppStatePayload.Entry entry : state.entries()) {
            if (!entry.team() && playerId.equals(entry.id())) {
                return true;
            }
        }
        return false;
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

    /** True when the snapshot already contains this candidate, so adding it would only be a no-op. */
    private boolean isListed(XppStatePayload.Entry entry) {
        if (entry.id() == null) {
            return false;
        }
        return entry.team() ? listedAsTeam(entry.id()) : listedAsPlayer(entry.id());
    }

    private void addSelf() {
        UUID self = selfId();
        if (self != null && pending == null) {
            sendEdit(XppAccessPayload.Action.ADD_PLAYER, self, "", true);
        }
    }

    /**
     * Adds every team this player is currently in that is not on the list yet.
     *
     * <p>The teams come from the snapshot, not from a local team mod lookup, so this works on a client that has
     * no FTB Teams of its own. All of them travel as one request: the answer arrives as one snapshot per packet,
     * and {@link PendingRequest} knows not to read the first of those as the whole answer.
     */
    private void addOwnTeams() {
        if (state == null || pending != null) {
            return;
        }
        List<XppStatePayload.Entry> missing = new ArrayList<>();
        for (XppStatePayload.Entry team : state.ownTeams()) {
            if (!listedAsTeam(team.id())) {
                missing.add(team);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        List<UUID> targets = new ArrayList<>(missing.size());
        for (XppStatePayload.Entry team : missing) {
            targets.add(team.id());
        }
        this.pending = new PendingRequest(XppAccessPayload.Action.ADD_TEAM, targets, "",
                state.whitelist(), Util.getMillis());
        setStatus(StatusKind.SENDING);
        for (XppStatePayload.Entry team : missing) {
            send(XppAccessPayload.Action.ADD_TEAM, team.id(), team.label());
        }
        refreshWidgets();
    }

    private void send(XppAccessPayload.Action action, @Nullable UUID target, String label) {
        send(action, target, label, 0L);
    }

    private void send(XppAccessPayload.Action action, @Nullable UUID target, String label, long requestId) {
        if (state != null) {
            PacketDistributor.sendToServer(new XppAccessPayload(state.pos(), action, target, label, requestId));
        }
    }

    /**
     * Sends one access-list edit and registers it as the edit in flight.
     *
     * <p>Refused while another edit is still unanswered: the registry holds one request, and a second one
     * would have no way to tell whose snapshot was whose.
     */
    private void sendEdit(XppAccessPayload.Action action, @Nullable UUID target, String label,
                          boolean wantWhitelist) {
        if (pending != null) {
            return;
        }
        pending = new PendingRequest(action, Collections.singletonList(target), label, wantWhitelist,
                Util.getMillis());
        setStatus(StatusKind.SENDING);
        send(action, target, label);
        refreshWidgets();
    }

    private void clearPending() {
        pending = null;
        if (statusKind == StatusKind.SENDING) {
            statusKind = StatusKind.NONE;
        }
    }

    private void setStatus(StatusKind kind) {
        this.statusKind = kind;
        this.statusSince = Util.getMillis();
    }

    /**
     * Decides what the snapshot just received says about the edit in flight.
     *
     * <p>Nothing here trusts a reply code: the server answers every request with a snapshot, including a
     * refusal, so the edit's fate is read out of the snapshot itself.
     */
    private void evaluatePending(XppStatePayload payload) {
        PendingRequest request = pending;
        if (request == null) {
            return;
        }
        List<PendingRequest.Entry> entries = new ArrayList<>(payload.entries().size());
        for (XppStatePayload.Entry entry : payload.entries()) {
            entries.add(new PendingRequest.Entry(entry.id(), entry.label(), entry.team()));
        }
        switch (request.verdict(entries, payload.whitelist())) {
            case APPLIED -> {
                pending = null;
                setStatus(StatusKind.OK);
            }
            case REFUSED -> {
                pending = null;
                setStatus(StatusKind.REJECTED);
            }
            // Not the whole answer yet: a bulk request is still being applied, so the edit stays in flight
            // and the timeout is what ends it.
            case INCOMPLETE -> {
            }
        }
    }

    /** Ends the edit in flight when the server never said anything about it. */
    private void expirePending() {
        if (pending != null && Util.getMillis() - pending.sentAt() >= PENDING_TIMEOUT_MS) {
            pending = null;
            setStatus(StatusKind.TIMEOUT);
            refreshWidgets();
        }
    }

    private void expireStatus() {
        long age = Util.getMillis() - statusSince;
        if (statusKind == StatusKind.OK && age >= STATUS_OK_MS) {
            statusKind = StatusKind.NONE;
        } else if ((statusKind == StatusKind.REJECTED || statusKind == StatusKind.TIMEOUT)
                && age >= STATUS_ERROR_MS) {
            statusKind = StatusKind.NONE;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Background, widgets and this screen's own content are drawn in one place so the list cannot end up
        // under a widget the player is expected to click.
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int localX = mouseX - leftPos;
        int localY = mouseY - topPos;
        expirePending();
        expireMembers();
        expireStatus();
        updateHover(localX, localY);

        // Hand-drawn content uses panel-relative coordinates, so the pose has to be moved onto the panel first -
        // render() hands us screen space (vanilla's own renderBg does the same translate).
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(leftPos, topPos, 0);
        drawPool(guiGraphics);
        drawHeader(guiGraphics);
        if (view == View.MEMBERS) {
            drawMembers(guiGraphics, localX, localY);
        } else {
            drawList(guiGraphics, localX, localY);
        }
        drawStatusBar(guiGraphics);
        if (menuOpen) {
            drawMenu(guiGraphics, localX, localY);
        }
        guiGraphics.pose().popPose();

        // Own tooltips come after the widget tooltips: AbstractWidget queues its tooltip to be flushed by
        // Screen.renderTooltip, which would otherwise paint over the row tooltip drawn here. Both are skipped
        // while the menu is open - the menu is the topmost layer and a tooltip is drawn last by construction.
        if (!menuOpen) {
            super.renderTooltip(guiGraphics, mouseX, mouseY);
            renderRowTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(leftPos, topPos, 0);
        guiGraphics.fill(0, 0, PANEL_WIDTH, PANEL_HEIGHT, COLOR_PANEL);
        // Before anything else inside the panel: the row fills are translucent, so this wash has to be under
        // them, and render() draws every one of those pieces after renderBg has returned.
        guiGraphics.fill(FRAME_LEFT, FRAME_TOP, FRAME_RIGHT, FRAME_BOTTOM, FRAME_BG);
        guiGraphics.fill(FRAME_LEFT, FRAME_TOP, FRAME_RIGHT, FRAME_TOP + 1, FRAME_BORDER);
        guiGraphics.fill(FRAME_LEFT, FRAME_BOTTOM - 1, FRAME_RIGHT, FRAME_BOTTOM, FRAME_BORDER);
        guiGraphics.fill(FRAME_LEFT, FRAME_TOP, FRAME_LEFT + 1, FRAME_BOTTOM, FRAME_BORDER);
        guiGraphics.fill(FRAME_RIGHT - 1, FRAME_TOP, FRAME_RIGHT, FRAME_BOTTOM, FRAME_BORDER);
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

    /**
     * Suppresses every tooltip while the menu is open.
     *
     * <p>Overridden rather than skipped at the call site because {@code AbstractContainerScreen.render} flushes
     * tooltips itself on the way out; a tooltip is the last thing drawn, so no drawing order can put the menu
     * above it.
     */
    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (menuOpen) {
            return;
        }
        super.renderTooltip(guiGraphics, mouseX, mouseY);
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

    private void drawHeader(GuiGraphics guiGraphics) {
        if (view == View.MEMBERS) {
            // Truncated to stop short of the back control, which shares this line's right end.
            String header = Component.translatable("access.members.header", membersTeamLabel).getString();
            guiGraphics.drawString(font, fit(header, MEMBERS_HEADER_RIGHT - HEADER_X), HEADER_X, HEADER_Y,
                    COLOR_MUTED, false);
            return;
        }
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

    private void drawList(GuiGraphics guiGraphics, int mouseLocalX, int mouseLocalY) {
        int rowsVisible = rowCapacity();
        if (state == null) {
            drawCentered(guiGraphics, Component.translatable("access.waiting").getString(), rowsVisible);
            return;
        }
        // Read-only draws the same rows: the list stays readable and selectable, and the reason it cannot be
        // edited is stated once in the status strip instead of taking the row area away from the list.
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

        int pendingIndex = pendingRowIndex(list);
        for (int row = 0; row < rowsVisible; row++) {
            int index = scrollOffset + row;
            if (index >= list.size()) {
                break;
            }
            Row entry = list.get(index);
            int y = LIST_TOP + row * ROW_HEIGHT;
            boolean hovered = mouseLocalY >= y && mouseLocalY < y + 20
                    && mouseLocalX >= ROW_LEFT && mouseLocalX < ROW_RIGHT;
            boolean slotHovered = mouseLocalX >= DELETE_X && mouseLocalX < ROW_RIGHT
                    && mouseLocalY >= y && mouseLocalY < y + 20;
            drawRow(guiGraphics, entry, y, index, hovered, slotHovered, index == pendingIndex);
        }
        drawScrollbar(guiGraphics, list.size(), scrollOffset);
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

    private void drawCentered(GuiGraphics guiGraphics, String text, int rowsVisible) {
        int centerY = LIST_TOP + (rowsVisible * ROW_HEIGHT) / 2 - 4;
        guiGraphics.drawString(font, text, (PANEL_WIDTH - font.width(text)) / 2, centerY, COLOR_MUTED, false);
    }

    private void drawRow(GuiGraphics guiGraphics, Row entry, int y, int index, boolean hovered,
                         boolean slotHovered, boolean isPending) {
        int background = entry.team
                ? (hovered ? COLOR_ROW_BG_HOVER_TEAM : COLOR_ROW_BG_TEAM)
                : (hovered ? COLOR_ROW_BG_HOVER_PLAYER : COLOR_ROW_BG_PLAYER);
        guiGraphics.fill(ROW_LEFT, y, ROW_RIGHT, y + 20, background);
        if (isSelected(entry)) {
            // The 8% wash goes down over the hover background and under everything else on the row: it is the
            // channel that has to survive both a hover and the icons.
            guiGraphics.fill(ROW_LEFT, y, ROW_LEFT + ROW_FRAME_WIDTH, y + 20,
                    (entry.team ? COLOR_TEAM : COLOR_PLAYER) & 0x22FFFFFF);
        }
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

        if (isPending) {
            // No menu slot while this row is being changed: the three dots replace it, and a second request for
            // the same row would be a request whose answer could not be told apart from the first one's.
            drawPendingDots(guiGraphics, y);
        } else if (hasEnabledMenuItem(entry)) {
            // Where the delete button used to be, in the same pixels: the slot now opens the row's menu instead
            // of destroying the entry, so no single click can lose data.
            guiGraphics.drawString(font, ROW_MENU_GLYPH,
                    DELETE_X + (ROW_RIGHT - DELETE_X - font.width(ROW_MENU_GLYPH)) / 2, y + 6,
                    slotHovered ? COLOR_TEXT : COLOR_MUTED, false);
        }

        // Two independent cues: the keyboard highlight survives the mouse leaving, and the mouse hover survives
        // the keyboard highlight being elsewhere. Where they coincide, only the keyboard frame is drawn and the
        // selection keeps its wash, so the row never wears two frames in the same pixels.
        drawFocusFrames(guiGraphics, y, entry.team ? COLOR_TEAM : COLOR_PLAYER, isSelected(entry),
                index == highlightRow);
    }

    /**
     * Draws the selection frame and the keyboard highlight frame for one row.
     *
     * <p>Both are the full row width, which is what lets them be told apart by colour and line width alone.
     */
    private void drawFocusFrames(GuiGraphics guiGraphics, int y, int accent, boolean selected,
                                 boolean keyboardHere) {
        if (selected && !keyboardHere) {
            guiGraphics.renderOutline(ROW_LEFT, y, ROW_FRAME_WIDTH, 20, accent);
            guiGraphics.renderOutline(ROW_LEFT + 1, y + 1, ROW_FRAME_WIDTH - 2, 18, accent);
        }
        if (keyboardHere) {
            guiGraphics.renderOutline(ROW_LEFT, y, ROW_FRAME_WIDTH, 20, COLOR_HIGHLIGHT);
        }
    }

    /**
     * The three dots that say "this row is being changed".
     *
     * <p>Phased 200 ms apart and driven straight from the clock, so nothing has to be ticked or stored: the
     * animation is a pure function of the current time.
     */
    private void drawPendingDots(GuiGraphics guiGraphics, int y) {
        long now = Util.getMillis();
        for (int i = 0; i < PENDING_DOT_X.length; i++) {
            int alpha = pulseAlpha(now, i * 200L);
            int color = (alpha << 24) | 0x0055FF99;
            int x = PENDING_DOT_X[i];
            guiGraphics.fill(x - 1, y + 9, x + 1, y + 11, color);
        }
    }

    /**
     * Alpha of one dot of a pulse, 0..255.
     *
     * <p>A 600 ms period with the phase offset folded in, so all three dots can share one formula.
     */
    private static int pulseAlpha(long now, long offset) {
        double phase = 2.0 * Math.PI * (((now - offset) % 600L + 600L) % 600L) / 600.0;
        double wave = 0.35 + 0.65 * (0.5 + 0.5 * Math.sin(phase));
        return (int) Math.round(255.0 * wave);
    }

    private int pendingRowIndex(List<Row> list) {
        PendingRequest request = pending;
        if (request == null) {
            return -1;
        }
        for (int i = 0; i < list.size(); i++) {
            Row row = list.get(i);
            if (request.concerns(row.entryData.id(), row.label, row.team)) {
                return i;
            }
        }
        return -1;
    }

    private void drawScrollbar(GuiGraphics guiGraphics, int count, int offset) {
        int rowsVisible = rowCapacity();
        if (count <= rowsVisible) {
            return;
        }
        int trackHeight = rowsVisible * ROW_HEIGHT;
        guiGraphics.fill(SCROLLBAR_X, LIST_TOP, SCROLLBAR_X + SCROLLBAR_WIDTH, LIST_TOP + trackHeight,
                COLOR_SEPARATOR);
        int thumbHeight = Math.max(10, trackHeight * rowsVisible / count);
        int maxOffset = count - rowsVisible;
        int thumbY = LIST_TOP + (trackHeight - thumbHeight) * offset / maxOffset;
        guiGraphics.fill(SCROLLBAR_X, thumbY, SCROLLBAR_X + SCROLLBAR_WIDTH, thumbY + thumbHeight, COLOR_MUTED);
    }

    /**
     * The status strip's standing information and the last answer's copy.
     *
     * <p>The read-only sentence lives here rather than in the row area: it is a property of the whole panel,
     * and the row area is busy showing the list the player is allowed to read.
     */
    private void drawStatusBar(GuiGraphics guiGraphics) {
        String left;
        if (view == View.MEMBERS) {
            int online = 0;
            for (XppMembersPayload.Member member : members) {
                if (member.online()) {
                    online++;
                }
            }
            left = Component.translatable("access.members.summary", members.size(), online).getString();
        } else {
            String mode = state == null ? Component.translatable("access.mode.unknown").getString()
                    : Component.translatable(state.whitelist() ? "access.mode.whitelist" : "access.mode.blacklist")
                            .getString();
            left = Component.translatable("access.status.info", mode,
                    state == null ? 0 : state.entries().size()).getString();
        }
        guiGraphics.drawString(font, fit(left, STATUS_SPLIT_X - STATUS_LEFT_X - 4), STATUS_LEFT_X, STATUS_TEXT_Y,
                COLOR_MUTED, false);

        if (state != null && !state.canEdit()) {
            String owner = state.ownerName().isEmpty()
                    ? Component.translatable("access.owner.none").getString()
                    : state.ownerName();
            drawStatusRight(guiGraphics, Component.translatable("access.status.readonly", owner).getString(),
                    COLOR_MUTED, false, false);
            return;
        }
        switch (statusKind) {
            case SENDING -> drawStatusRight(guiGraphics,
                    Component.translatable("access.status.sending").getString(), COLOR_MUTED, false, true);
            case OK -> drawStatusRight(guiGraphics,
                    Component.translatable("access.status.ok").getString(), COLOR_OK, false, false);
            case REJECTED -> drawStatusRight(guiGraphics,
                    Component.translatable("access.status.rejected").getString(), COLOR_REJECTED, true, false);
            case TIMEOUT -> drawStatusRight(guiGraphics,
                    Component.translatable("access.status.timeout").getString(), COLOR_TIMEOUT, true, false);
            case NONE -> {
            }
        }
    }

    /**
     * Draws the right segment, right-aligned to the panel edge.
     *
     * <p>{@code bar} adds the second channel - a 2px stroke in the text colour - because "refused" and
     * "timed out" are the two answers a colour-blind player most needs to tell apart. {@code pulse} prepends
     * the three in-flight dots, shrunk to single pixels so they read as an animation, not as a word.
     */
    private void drawStatusRight(GuiGraphics guiGraphics, String text, int color, boolean bar, boolean pulse) {
        String shown = fit(text, STATUS_RIGHT_EDGE - STATUS_SPLIT_X);
        int textWidth = font.width(shown);
        int dots = pulse ? 10 : 0;
        int stroke = bar ? 5 : 0;
        int x = STATUS_RIGHT_EDGE - textWidth - dots - stroke;
        if (pulse) {
            long now = Util.getMillis();
            for (int i = 0; i < 3; i++) {
                int alpha = pulseAlpha(now, i * 200L);
                guiGraphics.fill(x + i * 3, STATUS_TEXT_Y + 3, x + i * 3 + 1, STATUS_TEXT_Y + 4,
                        (alpha << 24) | 0x00AAAAAA);
            }
            x += dots;
        }
        if (bar) {
            guiGraphics.fill(x, STATUS_TEXT_Y - 1, x + 2, STATUS_TEXT_Y + 8, color);
            x += stroke;
        }
        guiGraphics.drawString(font, shown, x, STATUS_TEXT_Y, color, false);
    }

    private void drawMembers(GuiGraphics guiGraphics, int mouseLocalX, int mouseLocalY) {
        XppMembersStatus.Phase phase = membersPhase();
        if (phase != XppMembersStatus.Phase.OK) {
            String key = XppMembersStatus.messageKey(phase);
            if (key != null) {
                drawCentered(guiGraphics, Component.translatable(key).getString(), rowCapacity());
            }
            String second = XppMembersStatus.secondLineKey(phase);
            if (second != null) {
                String line = Component.translatable(second).getString();
                guiGraphics.drawString(font, line, (PANEL_WIDTH - font.width(line)) / 2,
                        LIST_TOP + (rowCapacity() * ROW_HEIGHT) / 2 + 8, COLOR_TIMEOUT, false);
            }
            if (phase == XppMembersStatus.Phase.LOADING) {
                drawProgressBar(guiGraphics);
            }
            return;
        }

        int rowsVisible = rowCapacity();
        for (int row = 0; row < rowsVisible; row++) {
            int index = membersScroll + row;
            if (index >= members.size()) {
                break;
            }
            int y = LIST_TOP + row * ROW_HEIGHT;
            boolean hovered = mouseLocalY >= y && mouseLocalY < y + 20
                    && mouseLocalX >= ROW_LEFT && mouseLocalX < ROW_RIGHT;
            drawMemberRow(guiGraphics, members.get(index), y, index, hovered);
        }
        drawScrollbar(guiGraphics, members.size(), membersScroll);
    }

    private void drawMemberRow(GuiGraphics guiGraphics, XppMembersPayload.Member member, int y, int index,
                               boolean hovered) {
        boolean selected = member.id().equals(selectedMember);
        // Background first, then the selection wash on top of it: the wash is 8% alpha, and painting it under an
        // opaque row background leaves no trace of it. Mirrors drawRow's order.
        guiGraphics.fill(ROW_LEFT, y, ROW_RIGHT, y + 20,
                hovered ? COLOR_ROW_BG_HOVER_PLAYER : COLOR_ROW_BG_PLAYER);
        if (selected) {
            guiGraphics.fill(ROW_LEFT, y, ROW_LEFT + ROW_FRAME_WIDTH, y + 20, COLOR_PLAYER & 0x22FFFFFF);
        }
        guiGraphics.fill(ROW_LEFT, y, ROW_LEFT + 3, y + 20, COLOR_PLAYER);
        guiGraphics.fill(ICON_LEFT, y + 6, ICON_LEFT + 8, y + 14, COLOR_PLAYER);

        int textColor = member.online() ? COLOR_ROW_TEXT : COLOR_OFFLINE_TEXT;
        String dot = member.online() ? MEMBER_DOT_ONLINE : MEMBER_DOT_OFFLINE;
        int dotWidth = font.width(dot);
        guiGraphics.drawString(font, MEMBER_PREFIX, TEXT_LEFT, y + 6, textColor, false);
        int prefixWidth = font.width(MEMBER_PREFIX);
        // The status dot is right-aligned to the label's right edge, so the name has to stop before it.
        int room = LABEL_RIGHT - (TEXT_LEFT + prefixWidth) - dotWidth - 4;
        guiGraphics.drawString(font, fit(member.name(), Math.max(0, room)), TEXT_LEFT + prefixWidth, y + 6,
                textColor, false);
        guiGraphics.drawString(font, dot, LABEL_RIGHT - dotWidth, y + 6,
                member.online() ? COLOR_CHECK : COLOR_OFFLINE_TEXT, false);

        drawFocusFrames(guiGraphics, y, COLOR_PLAYER, selected, index == membersHighlight);
    }

    /**
     * The loading bar: a slider running back and forth along a fixed track.
     *
     * <p>Driven from the clock with no stored position, so it cannot drift out of step with anything else and
     * needs nothing reset when the panel closes.
     */
    private void drawProgressBar(GuiGraphics guiGraphics) {
        guiGraphics.fill(ROW_LEFT, PROGRESS_TOP, ROW_RIGHT, PROGRESS_BOTTOM, COLOR_GROOVE);
        long phase = Util.getMillis() % PROGRESS_PERIOD_MS;
        double t = phase <= PROGRESS_PERIOD_MS / 2
                ? (double) phase / (PROGRESS_PERIOD_MS / 2.0)
                : (double) (PROGRESS_PERIOD_MS - phase) / (PROGRESS_PERIOD_MS / 2.0);
        int travel = (ROW_RIGHT - ROW_LEFT) - PROGRESS_SLIDER;
        int x = ROW_LEFT + (int) Math.round(t * travel);
        guiGraphics.fill(x, PROGRESS_TOP, x + PROGRESS_SLIDER, PROGRESS_BOTTOM, COLOR_FLUID_TEXT);
    }

    private XppMembersStatus.Phase membersPhase() {
        if (membersTimedOut) {
            return XppMembersStatus.Phase.TIMEOUT;
        }
        if (membersLoading) {
            return XppMembersStatus.Phase.LOADING;
        }
        return XppMembersStatus.phaseFor(membersStatus, members.size());
    }

    private void drawMenu(GuiGraphics guiGraphics, int mouseLocalX, int mouseLocalY) {
        ContextMenuLayout.Placement placement = menuPlacement;
        Row row = menuRow();
        if (placement == null || row == null) {
            return;
        }
        guiGraphics.fill(placement.x() + 2, placement.y() + 2, placement.x() + placement.width() + 2,
                placement.y() + placement.height() + 2, COLOR_MENU_SHADOW);
        guiGraphics.fill(placement.x(), placement.y(), placement.x() + placement.width(),
                placement.y() + placement.height(), COLOR_MENU_BG);

        int hovered = placement.itemAt(mouseLocalX, mouseLocalY);
        for (int item = 0; item < ContextMenuLayout.ITEM_COUNT; item++) {
            int top = placement.itemTop(item);
            int bottom = top + ContextMenuLayout.ITEM_HEIGHT;
            int left = placement.x() + 1;
            int right = placement.x() + placement.width() - 1;
            boolean enabled = menuItemEnabled(item, row);
            // The keyboard highlight reuses the hover wash: an item ENTER would activate has to look like one
            // the mouse is on, otherwise the key does something invisible.
            boolean hot = enabled && (item == hovered || item == menuHighlight);
            if (!enabled) {
                guiGraphics.fill(left, top, right, bottom, COLOR_MENU_DISABLED_BG);
                guiGraphics.fill(left, top, right, top + 1, COLOR_MENU_DISABLED_BORDER);
                guiGraphics.fill(left, bottom - 1, right, bottom, COLOR_MENU_DISABLED_BORDER);
                guiGraphics.fill(left, top, left + 1, bottom, COLOR_MENU_DISABLED_BORDER);
                guiGraphics.fill(right - 1, top, right, bottom, COLOR_MENU_DISABLED_BORDER);
            } else if (hot) {
                guiGraphics.fill(left, top, right, bottom,
                        item == ITEM_REMOVE ? COLOR_MENU_DANGER_HOVER : COLOR_MENU_HOVER);
                if (item == ITEM_REMOVE) {
                    // The only item that destroys something gets the 2px stroke, so it is recognisable before
                    // it is read.
                    guiGraphics.fill(left, top, left + 2, bottom, COLOR_DELETE);
                }
            }
            guiGraphics.drawString(font, menuIcon(item), placement.x() + ContextMenuLayout.ICON_X, top + 5,
                    menuIconColor(item, enabled), false);
            guiGraphics.drawString(font, menuLabel(item), placement.x() + ContextMenuLayout.TEXT_X, top + 5,
                    menuTextColor(item, enabled), false);
        }

        guiGraphics.fill(placement.x(), placement.y(), placement.x() + placement.width(), placement.y() + 1,
                COLOR_BORDER);
        guiGraphics.fill(placement.x(), placement.y() + placement.height() - 1,
                placement.x() + placement.width(), placement.y() + placement.height(), COLOR_BORDER);
        guiGraphics.fill(placement.x(), placement.y(), placement.x() + 1,
                placement.y() + placement.height(), COLOR_BORDER);
        guiGraphics.fill(placement.x() + placement.width() - 1, placement.y(),
                placement.x() + placement.width(), placement.y() + placement.height(), COLOR_BORDER);
    }

    /** Truncates with an ellipsis so the row keeps its menu slot reachable whatever the name length is. */
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
        long now = Util.getMillis();
        if (view == View.MEMBERS) {
            hoverRow = -1;
            int index = memberRowAt(mouseLocalX, mouseLocalY);
            if (index != memberHover) {
                memberHover = index;
                hoveredSince = now;
            }
            return;
        }
        memberHover = -1;
        int index = listRowAt(mouseLocalX, mouseLocalY);
        if (index != hoverRow) {
            hoverRow = index;
            hoveredSince = now;
        }
    }

    /**
     * Index into the visible list under the pointer, or -1.
     *
     * <p>The pointer only reaches the list body: the input frame, the header and the buttons are outside it.
     * Read-only is not excluded - a row there is still selectable, it just has nothing to act on.
     */
    private int listRowAt(int mouseLocalX, int mouseLocalY) {
        if (state == null || view != View.LIST || inInputArea(mouseLocalX, mouseLocalY)
                || mouseLocalX < ROW_LEFT || mouseLocalX >= ROW_RIGHT) {
            return -1;
        }
        int row = rowIndexAt(mouseLocalY);
        if (row < 0) {
            return -1;
        }
        int index = scrollOffset + row;
        return index < shownRows().size() ? index : -1;
    }

    private int memberRowAt(int mouseLocalX, int mouseLocalY) {
        if (view != View.MEMBERS || membersPhase() != XppMembersStatus.Phase.OK
                || mouseLocalX < ROW_LEFT || mouseLocalX >= ROW_RIGHT) {
            return -1;
        }
        int row = rowIndexAt(mouseLocalY);
        if (row < 0) {
            return -1;
        }
        int index = membersScroll + row;
        return index < members.size() ? index : -1;
    }

    /**
     * Row slot of the row area at a vertical position, or -1.
     *
     * <p>The explicit lower bound matters: Java division truncates towards zero, so a point one pixel above the
     * row area would otherwise divide to slot 0 and make the header click through to the first row.
     */
    private int rowIndexAt(int mouseLocalY) {
        if (mouseLocalY < LIST_TOP) {
            return -1;
        }
        int row = (mouseLocalY - LIST_TOP) / ROW_HEIGHT;
        if (row < 0 || row >= rowCapacity() || mouseLocalY - LIST_TOP - row * ROW_HEIGHT >= 20) {
            // The 1px gap between rows belongs to no row, so a click there does nothing rather than hitting the
            // wrong neighbour's menu slot.
            return -1;
        }
        return row;
    }

    /** True inside the drawn input frame, padding included - the frame is the click target, not the widget. */
    private boolean inInputArea(int mouseLocalX, int mouseLocalY) {
        return searchBox != null && searchBox.visible
                && mouseLocalX >= INPUT_LEFT && mouseLocalX < INPUT_RIGHT
                && mouseLocalY >= INPUT_TOP && mouseLocalY < INPUT_BOTTOM;
    }

    private boolean inRowArea(int mouseLocalX, int mouseLocalY) {
        return mouseLocalX >= ROW_LEFT && mouseLocalX < ROW_RIGHT
                && mouseLocalY >= LIST_TOP && mouseLocalY < LIST_TOP + rowCapacity() * ROW_HEIGHT;
    }

    /** True when the point lands on a real widget, which then gets the click untouched. */
    private boolean overWidget(double mouseX, double mouseY) {
        for (AccessButton button : headerButtons) {
            if (hits(button, mouseX, mouseY)) {
                return true;
            }
        }
        return hits(backButton, mouseX, mouseY);
    }

    private static boolean hits(@Nullable AccessButton button, double mouseX, double mouseY) {
        return button != null && button.visible
                && mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int localX = (int) mouseX - leftPos;
        int localY = (int) mouseY - topPos;

        if (menuOpen) {
            return clickOnMenu(mouseX, mouseY, button, localX, localY);
        }
        if (state == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 1) {
            openRowMenu(localX, localY);
            return true;
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        // Widgets keep the click untouched, and they come before the row logic: a button sits in the empty part
        // of the panel as far as the row area is concerned.
        if (overWidget(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (inInputArea(localX, localY)) {
            // Clicking anywhere on the frame, padding included, focuses the box: the drawn frame is the
            // control, and a 9px-tall borderless EditBox is a hard target on its own.
            // Focus goes through the screen, not the widget: ContainerEventHandler.charTyped only forwards
            // to getFocused(), so a widget-level flag alone would drop every typed character.
            setFocused(searchBox);
            // The caret goes to the end so a second click into an already filtered box appends rather than
            // inserting in the middle of the old query.
            searchBox.setCursorPosition(searchBox.getValue().length());
            return true;
        }
        if (view == View.MEMBERS) {
            return clickMemberRow(localX, localY);
        }
        int index = listRowAt(localX, localY);
        if (index >= 0) {
            Row row = shownRows().get(index);
            // Selection only. Adding and removing are menu items now, so a stray click can no longer change
            // anything - except in the trailing slot, which opens that same menu for this row.
            selected = Selection.of(row);
            if (hasEnabledMenuItem(row) && localX >= DELETE_X) {
                openMenu(index, localX, localY);
            }
            return true;
        }
        // Clicking the empty part of the list leaves the picker and shows the access list again.
        if (picking()) {
            clearSearch();
            return true;
        }
        selected = null;
        setFocused(null);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * A click while a menu is open.
     *
     * <p>Inside the menu it either activates an item or does nothing (a disabled item stays on screen rather
     * than closing, so the player can see why it is unavailable). Outside it the click is swallowed: letting it
     * through would also hit whatever row or control is underneath, which reads as two actions from one click.
     */
    private boolean clickOnMenu(double mouseX, double mouseY, int button, int localX, int localY) {
        ContextMenuLayout.Placement placement = menuPlacement;
        boolean inside = placement != null && placement.contains(localX, localY);
        if (button == 0) {
            if (inside) {
                int item = placement.itemAt(localX, localY);
                if (item >= 0) {
                    activateMenuItem(item);
                }
                return true;
            }
            closeMenu();
            return true;
        }
        if (button == 1) {
            if (inside) {
                closeMenu();
                return true;
            }
            // Right-clicking another row moves the menu there, selection and all; right-clicking the same row
            // closes it. Either way the click does not reach the row underneath.
            int index = listRowAt(localX, localY);
            if (index < 0) {
                closeMenu();
                return true;
            }
            Row row = shownRows().get(index);
            selected = Selection.of(row);
            if (index == menuRow && menuSource == currentSource()) {
                closeMenu();
            } else {
                openMenu(index, localX, localY);
            }
            return true;
        }
        closeMenu();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Right-click handling when no menu is open: select the row and anchor a menu on it. */
    private void openRowMenu(int localX, int localY) {
        if (view == View.MEMBERS) {
            // Member rows are read-only: there is no per-member action the protocol can express (a team is
            // added and removed as a whole), so opening a menu full of disabled items would only mislead.
            return;
        }
        int index = listRowAt(localX, localY);
        if (index < 0) {
            return;
        }
        selected = Selection.of(shownRows().get(index));
        openMenu(index, localX, localY);
    }

    private boolean clickMemberRow(int localX, int localY) {
        if (membersPhase() == XppMembersStatus.Phase.TIMEOUT && inRowArea(localX, localY)) {
            // The whole row area is the retry control: a 16px button would need its own hit box and a TAB stop,
            // and the panel's only other control is the way back.
            requestMembers();
            return true;
        }
        int index = memberRowAt(localX, localY);
        if (index >= 0) {
            selectedMember = members.get(index).id();
        }
        return true;
    }

    private void openMenu(int rowIndex, int anchorX, int anchorY) {
        this.menuOpen = true;
        this.menuSource = currentSource();
        this.menuRow = rowIndex;
        this.menuHighlight = -1;
        this.menuPlacement = ContextMenuLayout.at(anchorX, anchorY, menuWidth());
    }

    private void closeMenu() {
        menuOpen = false;
        menuRow = -1;
        menuHighlight = -1;
        menuPlacement = null;
    }

    private Source currentSource() {
        return picking() ? Source.PICKER : Source.LIST;
    }

    @Nullable
    private Row menuRow() {
        List<Row> list = menuRows();
        return menuRow >= 0 && menuRow < list.size() ? list.get(menuRow) : null;
    }

    /** Widest item label, so a translated menu never clips its own text. */
    private int menuWidth() {
        int widest = 0;
        for (int item = 0; item < ContextMenuLayout.ITEM_COUNT; item++) {
            widest = Math.max(widest, font.width(menuLabel(item)));
        }
        return ContextMenuLayout.width(widest);
    }

    private String menuLabel(int item) {
        return Component.translatable(menuKey(item)).getString();
    }

    private static String menuKey(int item) {
        return switch (item) {
            case ITEM_ADD -> "access.menu.add";
            case ITEM_REMOVE -> "access.menu.remove";
            default -> "access.menu.view";
        };
    }

    private static String menuIcon(int item) {
        return switch (item) {
            case ITEM_ADD -> "+";
            case ITEM_REMOVE -> "\u00d7";
            default -> ">";
        };
    }

    private static int menuAccent(int item) {
        return switch (item) {
            case ITEM_ADD -> COLOR_CHECK;
            case ITEM_REMOVE -> COLOR_DELETE;
            default -> COLOR_PLAYER;
        };
    }

    private static int menuIconColor(int item, boolean enabled) {
        int accent = menuAccent(item);
        return enabled ? accent : accent & MENU_ACCENT_DISABLED;
    }

    private static int menuTextColor(int item, boolean enabled) {
        if (!enabled) {
            return COLOR_MENU_DISABLED_TEXT;
        }
        return item == ITEM_REMOVE ? COLOR_MENU_DANGER_TEXT : COLOR_ROW_TEXT;
    }

    /**
     * Whether an item would do anything on this row.
     *
     * <p>Everything that writes is off while one edit is in flight, and everything is off in read-only mode:
     * the owner gate refuses those requests server-side, and a control that can only fail is worse than none.
     */
    private boolean menuItemEnabled(int item, Row row) {
        if (!canEdit()) {
            return false;
        }
        return switch (item) {
            // Only a candidate that is not listed yet: re-adding what the server already has would be a
            // request whose answer is indistinguishable from success, so it is never sent.
            case ITEM_ADD -> !row.entry && row.entryData.id() != null && !isListed(row.entryData)
                    && pending == null;
            case ITEM_REMOVE -> row.entry && pending == null;
            // A legacy team row has a name but no id, and the member request is addressed by id - so there is
            // nothing this item could ask for.
            case ITEM_VIEW -> row.team && row.entryData.id() != null;
            default -> false;
        };
    }

    private boolean hasEnabledMenuItem(Row row) {
        for (int item = 0; item < ContextMenuLayout.ITEM_COUNT; item++) {
            if (menuItemEnabled(item, row)) {
                return true;
            }
        }
        return false;
    }

    private void activateMenuItem(int item) {
        Row row = menuRow();
        if (row == null || !menuItemEnabled(item, row)) {
            return;
        }
        closeMenu();
        switch (item) {
            case ITEM_ADD -> addCandidate(row.entryData);
            case ITEM_REMOVE -> removeEntry(row.entryData);
            case ITEM_VIEW -> openMembers(row.entryData);
            default -> {
            }
        }
    }

    /**
     * Moves the menu highlight, skipping what cannot be activated.
     *
     * <p>Nothing is highlighted when the menu opens: the first arrow press is what arms an item, so a menu
     * opened by a stray right click cannot be set off by a stray ENTER.
     */
    private void moveMenuHighlight(int delta) {
        Row row = menuRow();
        if (row == null) {
            return;
        }
        int next = menuHighlight;
        for (int step = 0; step < ContextMenuLayout.ITEM_COUNT; step++) {
            next = menuHighlight < 0 && step == 0
                    ? (delta > 0 ? 0 : ContextMenuLayout.ITEM_COUNT - 1)
                    : Math.floorMod(next + delta, ContextMenuLayout.ITEM_COUNT);
            if (menuItemEnabled(next, row)) {
                menuHighlight = next;
                return;
            }
        }
    }

    private void addCandidate(XppStatePayload.Entry candidate) {
        if (candidate.id() == null) {
            return;
        }
        sendEdit(candidate.team() ? XppAccessPayload.Action.ADD_TEAM : XppAccessPayload.Action.ADD_PLAYER,
                candidate.id(), candidate.label(), state != null && state.whitelist());
    }

    private void removeEntry(XppStatePayload.Entry entry) {
        XppAccessPayload.Action action = entry.team()
                ? XppAccessPayload.Action.REMOVE_TEAM
                : XppAccessPayload.Action.REMOVE_PLAYER;
        // The label travels with the removal so the server can also drop a legacy entry that has no UUID.
        sendEdit(action, entry.id(), entry.label(), state != null && state.whitelist());
    }

    /** Leaves the list for the member panel of one team and asks the server who is in it. */
    private void openMembers(XppStatePayload.Entry team) {
        UUID teamId = team.id();
        if (teamId == null) {
            return;
        }
        closeMenu();
        view = View.MEMBERS;
        membersTeamId = teamId;
        membersTeamLabel = team.label();
        members.clear();
        membersStatus = XppMembersStatus.OK;
        membersLoading = true;
        membersTimedOut = false;
        membersScroll = 0;
        membersHighlight = -1;
        selectedMember = null;
        requestMembers();
        refreshWidgets();
    }

    /** Sends the member request for the team the panel is showing, with a fresh id. */
    private void requestMembers() {
        if (membersTeamId == null) {
            return;
        }
        membersLoading = true;
        membersTimedOut = false;
        membersSentAt = Util.getMillis();
        // A fresh id per request: the answer to an earlier one must not be mistaken for this one's, and both
        // a reopen and a retry can leave an earlier answer in flight.
        membersRequestId = ++requestCounter;
        send(XppAccessPayload.Action.REQUEST_MEMBERS, membersTeamId, membersTeamLabel, membersRequestId);
    }

    private void expireMembers() {
        if (view == View.MEMBERS && membersLoading
                && Util.getMillis() - membersSentAt >= MEMBERS_TIMEOUT_MS) {
            membersLoading = false;
            membersTimedOut = true;
        }
    }

    /** Returns to the list, keeping its selection and scroll position. */
    private void exitMembers() {
        view = View.LIST;
        members.clear();
        membersLoading = false;
        membersTimedOut = false;
        membersScroll = 0;
        membersHighlight = -1;
        memberHover = -1;
        selectedMember = null;
        membersTeamId = null;
    }

    private void showList() {
        exitMembers();
        refreshWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menuOpen) {
            // Scrolling under an open menu would slide the row it is anchored to out from under it.
            closeMenu();
            return true;
        }
        int localX = (int) mouseX - leftPos;
        int localY = (int) mouseY - topPos;
        if (view == View.MEMBERS) {
            int memberCount = members.size();
            if (memberCount > rowCapacity() && scrollY != 0 && inRowArea(localX, localY)) {
                membersScroll = clampOffset(membersScroll - (int) Math.signum(scrollY), memberCount,
                        rowCapacity());
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int count = shownRows().size();
        if (count > rowCapacity() && scrollY != 0 && localX >= ROW_LEFT && localX < SCROLLBAR_X
                && localY >= LIST_TOP && localY < LIST_TOP + rowCapacity() * ROW_HEIGHT) {
            scrollOffset = Math.max(0,
                    Math.min(count - rowCapacity(), scrollOffset - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static int clampOffset(int offset, int count, int capacity) {
        return Math.max(0, Math.min(Math.max(0, count - capacity), offset));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The EditBox returns false for ESC/ENTER/TAB and the arrows, so they reach this method and can be
        // claimed here before vanilla turns them into a screen close or a focus change. TAB is deliberately not
        // claimed unless a menu is open: otherwise it is the vanilla focus cycle.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // Three stages: the menu first, then the member panel, then the picker, and only then the screen.
            // An open menu is closed even when the input box is not focused: it is the topmost thing on screen.
            if (menuOpen) {
                closeMenu();
                return true;
            }
            if (view == View.MEMBERS) {
                showList();
                return true;
            }
            if (searchBox != null && (searchBox.isFocused() || !searchBox.getValue().isEmpty())) {
                clearSearch();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (searchBox == null) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_TAB && menuOpen) {
            // Swallowed rather than passed on: moving focus with a menu open would leave the menu anchored to a
            // control the player is no longer on.
            closeMenu();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (menuOpen) {
                if (menuHighlight >= 0) {
                    activateMenuItem(menuHighlight);
                }
                return true;
            }
            if (view == View.MEMBERS) {
                // The back control is a widget, so ENTER has to reach it when it holds the focus.
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            List<Row> list = shownRows();
            if (picking() && highlightRow >= 0 && highlightRow < list.size() && !list.get(highlightRow).entry) {
                Row row = list.get(highlightRow);
                selected = Selection.of(row);
                addCandidate(row.entryData);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            int delta = keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1;
            if (menuOpen) {
                moveMenuHighlight(delta);
                return true;
            }
            if (view == View.MEMBERS) {
                moveMemberHighlight(delta);
                return true;
            }
            if (picking()) {
                moveHighlight(delta);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (view == View.MEMBERS) {
            // Nothing in the member panel takes text; the widgets still take their keys.
            return super.keyPressed(keyCode, scanCode, modifiers);
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

    /** The member highlight is visual only, but it still has to keep its row on screen. */
    private void moveMemberHighlight(int delta) {
        if (members.isEmpty()) {
            membersHighlight = -1;
            return;
        }
        int next = membersHighlight < 0 ? (delta > 0 ? 0 : members.size() - 1) : membersHighlight + delta;
        membersHighlight = Math.max(0, Math.min(members.size() - 1, next));
        if (membersHighlight < membersScroll) {
            membersScroll = membersHighlight;
        } else if (membersHighlight >= membersScroll + rowCapacity()) {
            membersScroll = membersHighlight - rowCapacity() + 1;
        }
        clampMembersScroll();
    }

    /** Leaves the picker: clears the query, drops focus and shows the access list again. */
    private void clearSearch() {
        searchBox.setValue("");
        setFocused(null);
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

    private void clampMembersScroll() {
        membersScroll = clampOffset(membersScroll, members.size(), rowCapacity());
        if (membersHighlight >= members.size()) {
            membersHighlight = -1;
        }
        if (memberHover >= members.size()) {
            memberHover = -1;
        }
    }

    /** True when this drawn row carries the single selection. */
    private boolean isSelected(Row row) {
        Selection current = selected;
        if (current == null || current.team() != row.team) {
            return false;
        }
        if (current.id() != null && row.entryData.id() != null) {
            return current.id().equals(row.entryData.id());
        }
        // A legacy row has no id at all, so the label is the only identity it has left.
        return current.id() == null && row.entryData.id() == null && current.label().equals(row.label);
    }

    private void renderRowTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // The menu is drawn above everything and a tooltip is drawn last, so suppression is the only way to
        // keep a row tooltip from appearing on top of the menu it belongs to.
        if (menuOpen || state == null) {
            return;
        }
        if (view == View.MEMBERS) {
            renderMemberTooltip(guiGraphics, mouseX, mouseY);
            return;
        }
        List<Row> list = shownRows();
        if (hoverRow < 0 || hoverRow >= list.size() || Util.getMillis() - hoveredSince < TOOLTIP_DELAY_MS) {
            return;
        }
        // A tooltip while the pointer is on a button or in the input frame would sit on top of the control the
        // player is about to use; a tooltip on the row the keyboard already highlights would only repeat it.
        if (mouseX >= leftPos && mouseY >= topPos) {
            int localX = mouseX - leftPos;
            int localY = mouseY - topPos;
            if (inInputArea(localX, localY) || overWidget(mouseX, mouseY)) {
                return;
            }
        }
        if (picking() && hoverRow == highlightRow) {
            return;
        }
        Row row = list.get(hoverRow);
        // The row already shows the name, so the tooltip adds the identity behind it: the raw UUID, which is what
        // a server admin needs to paste into a command. A legacy row has no id at all, so its name is all there is.
        String value = row.entryData.id() == null ? row.label : row.entryData.id().toString();
        renderIdentityTooltip(guiGraphics, value, mouseX, mouseY);
    }

    private void renderMemberTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (memberHover < 0 || memberHover >= members.size()
                || Util.getMillis() - hoveredSince < TOOLTIP_DELAY_MS) {
            return;
        }
        renderIdentityTooltip(guiGraphics, members.get(memberHover).id().toString(), mouseX, mouseY);
    }

    private void renderIdentityTooltip(GuiGraphics guiGraphics, String value, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(value).withStyle(style -> style.withColor(COLOR_TOOLTIP_UUID)));
        guiGraphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private static final int ITEM_ADD = 0;
    private static final int ITEM_REMOVE = 1;
    private static final int ITEM_VIEW = 2;

    /** Identity of the selected row: players and teams are told apart by the flag, legacy rows by the label. */
    private record Selection(boolean team, @Nullable UUID id, String label) {

        private static Selection of(Row row) {
            return new Selection(row.team, row.entryData.id(), row.label);
        }
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
