package com.olynk.waystonexppayment.client;

import java.util.ArrayList;
import java.util.List;

import com.olynk.waystonexppayment.menu.XpPaymentTankMenu;
import com.olynk.waystonexppayment.net.XppAccessPayload;
import com.olynk.waystonexppayment.net.XppStatePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Screen for the payment tank: shows the pool and edits the access list.
 *
 * <p>There is no text input on purpose. Entries are added by clicking a button that names an already-known
 * identity ("me", or one of the player's FTB teams), which keeps typos out of the access list and keeps the
 * screen usable without a keyboard layout assumption.
 */
public class XpPaymentTankScreen extends AbstractContainerScreen<XpPaymentTankMenu> {

    private static final int TEAM_BUTTON_SLOTS = 4;

    private final List<Button> teamButtons = new ArrayList<>();
    private Button modeButton;
    private Button addMeButton;
    private Button removeMeButton;
    private XppStatePayload state;

    public XpPaymentTankScreen(XpPaymentTankMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 220;
        this.imageHeight = 170;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        this.modeButton = addRenderableWidget(new Button.Builder(Component.literal("Mode"),
                button -> send(XppAccessPayload.Action.SET_MODE,
                        state != null && state.whitelist() ? "blacklist" : "whitelist"))
                .pos(leftPos + 8, topPos + 30).size(104, 20).build());
        this.addMeButton = addRenderableWidget(new Button.Builder(Component.literal("Add me"),
                button -> send(XppAccessPayload.Action.ADD_PLAYER, ownName()))
                .pos(leftPos + 116, topPos + 30).size(48, 20).build());
        this.removeMeButton = addRenderableWidget(new Button.Builder(Component.literal("Remove me"),
                button -> send(XppAccessPayload.Action.REMOVE_PLAYER, ownName()))
                .pos(leftPos + 166, topPos + 30).size(46, 20).build());

        teamButtons.clear();
        for (int index = 0; index < TEAM_BUTTON_SLOTS; index++) {
            int slot = index;
            Button button = addRenderableWidget(new Button.Builder(Component.literal("Team"),
                    b -> onTeamButton(slot))
                    .pos(leftPos + 8, topPos + 54 + index * 22).size(204, 20).build());
            button.visible = false;
            button.active = false;
            teamButtons.add(button);
        }
        refreshWidgets();
    }

    /** Called by the payload handler whenever the server sends a new snapshot. */
    public void onState(XppStatePayload payload) {
        this.state = payload;
        if (minecraft != null && minecraft.player != null) {
            refreshWidgets();
        }
    }

    private void refreshWidgets() {
        if (modeButton == null) {
            return;
        }
        modeButton.setMessage(Component.literal(state == null
                ? "Mode: ..."
                : "Mode: " + (state.whitelist() ? "whitelist" : "blacklist")));

        List<String> teams = state == null ? List.of() : state.ownTeams();
        for (int index = 0; index < teamButtons.size(); index++) {
            Button button = teamButtons.get(index);
            if (index < teams.size()) {
                String team = teams.get(index);
                boolean listed = state != null && state.teams().stream().anyMatch(name -> name.equalsIgnoreCase(team));
                button.setMessage(Component.literal((listed ? "Remove team: " : "Add team: ") + team));
                button.visible = true;
                button.active = true;
            } else {
                button.visible = false;
                button.active = false;
            }
        }
    }

    private void onTeamButton(int slot) {
        if (state == null || slot >= state.ownTeams().size()) {
            return;
        }
        String team = state.ownTeams().get(slot);
        boolean listed = state.teams().stream().anyMatch(name -> name.equalsIgnoreCase(team));
        send(listed ? XppAccessPayload.Action.REMOVE_TEAM : XppAccessPayload.Action.ADD_TEAM, team);
    }

    private void send(XppAccessPayload.Action action, String value) {
        if (state != null) {
            PacketDistributor.sendToServer(new XppAccessPayload(state.pos(), action, value));
        }
    }

    private static String ownName() {
        return Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getGameProfile().getName()
                : "";
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101010);
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 1, 0xFF9A9A9A);
        guiGraphics.fill(leftPos, topPos + imageHeight - 1, leftPos + imageWidth, topPos + imageHeight, 0xFF9A9A9A);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFFFFF, false);

        if (state == null) {
            guiGraphics.drawString(font, "Waiting for the server...", 8, 8 + font.lineHeight + 6, 0xA0A0A0, false);
            return;
        }

        String fluid = state.fluid().isEmpty()
                ? "empty"
                : state.fluid().getHoverName().getString() + " " + state.fluid().getAmount() + " mB";
        guiGraphics.drawString(font, fluid + " / " + state.capacityOrDefault() + " mB"
                        + "  (" + state.poolXp() + " XP, " + state.mbPerXp() + " mB/XP)",
                8, 8 + font.lineHeight + 2, 0x55FF99, false);

        // Coordinates here are relative to the panel: AbstractContainerScreen already translated the pose.
        int y = 54 + TEAM_BUTTON_SLOTS * 22 + 4;
        guiGraphics.drawString(font, "Players: " + join(state.players()), 8, y, 0xFFFFFF, false);
        guiGraphics.drawString(font, "Teams: " + join(state.teams()), 8, y + font.lineHeight + 2, 0xFFFFFF, false);
    }

    private static String join(List<String> values) {
        return values.isEmpty() ? "-" : String.join(", ", values);
    }
}
