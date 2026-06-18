package com.hexvane.wraithbusters.ui;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.game.GameSession;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

public final class LobbyStatusHud extends CustomUIHud {
    public LobbyStatusHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, WraithBustersConstants.LOBBY_HUD_KEY, 0);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("WraithBusters/LobbyStatusHud.ui");
    }

    public void refresh(@Nonnull GameSession session, @Nonnull WraithBustersPluginConfig config) {
        UICommandBuilder b = new UICommandBuilder();
        int ready = session.readyCount();
        int joined = session.getPlayers().size();
        String total = joined >= config.getMinPlayers() ? String.valueOf(joined) : "?";
        b.set(
            "#LobbyStatus.TextSpans",
            Message.translation("server.wraithbusters.lobby.status")
                .param("ready", ready)
                .param("total", total)
        );
        this.update(false, b);
    }
}
