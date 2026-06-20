package com.hexvane.wraithbusters.ui;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

public final class ObjectiveHud extends CustomUIHud {
    public ObjectiveHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, WraithBustersConstants.OBJECTIVE_HUD_KEY, 0);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("WraithBusters/ObjectiveHud.ui");
    }

    public void refresh(@Nonnull GameSession session, @Nonnull PlayerSessionState state) {
        UICommandBuilder b = new UICommandBuilder();
        b.set("#ObjectiveTitle.TextSpans", ObjectiveHudContent.title(session));
        b.set("#ObjectiveDescription.TextSpans", ObjectiveHudContent.description(session, state));
        this.update(false, b);
    }
}
