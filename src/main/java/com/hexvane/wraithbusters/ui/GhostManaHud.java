package com.hexvane.wraithbusters.ui;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

public final class GhostManaHud extends CustomUIHud {
    public GhostManaHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, WraithBustersConstants.GHOST_MANA_HUD_KEY, 0);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("WraithBusters/GhostManaHud.ui");
    }

    public void refresh(@Nonnull PlayerSessionState state, @Nonnull WraithBustersPluginConfig config) {
        UICommandBuilder b = new UICommandBuilder();
        b.set(
            "#SpiritBarText.TextSpans",
            Message.translation("server.wraithbusters.ghost.mana.title")
        );
        SpiritBarUi.apply(b, state.getGhostMana(), config.getGhostMaxMana());
        this.update(false, b);
    }
}
