package com.hexvane.wraithbusters.ui;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.game.GameSession;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

public final class RoundTimerHud extends CustomUIHud {
    public RoundTimerHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, WraithBustersConstants.ROUND_TIMER_HUD_KEY, 0);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("WraithBusters/RoundTimerHud.ui");
    }

    public void refresh(@Nonnull GameSession session, @Nonnull WraithBustersPluginConfig config) {
        long remainingMs = Math.max(0L, session.getRoundEndEpochMs() - System.currentTimeMillis());
        int totalSeconds = (int) (remainingMs / 1000L);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String time = String.format("%02d:%02d", minutes, seconds);
        UICommandBuilder b = new UICommandBuilder();
        b.set("#RoundTimer.TextSpans", Message.translation("server.wraithbusters.round.timer").param("time", time));
        this.update(false, b);
    }
}
