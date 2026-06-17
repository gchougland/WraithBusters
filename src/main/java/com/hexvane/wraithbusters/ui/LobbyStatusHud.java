package com.hexvane.wraithbusters.ui;

import com.hexvane.wraithbusters.WraithBustersConstants;
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

    public void refresh(@Nonnull GameSession session) {
        UICommandBuilder b = new UICommandBuilder();
        int ready = session.readyCount();
        int total = session.getPlayers().size();
        b.set("#LobbyStatus.TextSpans", Message.translation("server.wraithbusters.lobby.status").param("ready", ready).param("total", total));
        this.update(false, b);
    }
}
