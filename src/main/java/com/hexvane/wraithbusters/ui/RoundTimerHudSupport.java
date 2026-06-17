package com.hexvane.wraithbusters.ui;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.game.GameSession;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

public final class RoundTimerHudSupport {
    private RoundTimerHudSupport() {}

    @Nonnull
    public static RoundTimerHud obtainHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        CustomUIHud existing = player.getHudManager().getCustomHud(WraithBustersConstants.ROUND_TIMER_HUD_KEY);
        if (existing instanceof RoundTimerHud hud) {
            return hud;
        }
        RoundTimerHud created = new RoundTimerHud(playerRef);
        player.getHudManager().addCustomHud(playerRef, created);
        return created;
    }

    public static void refresh(
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef,
        @Nonnull GameSession session,
        @Nonnull WraithBustersPluginConfig config
    ) {
        obtainHud(player, playerRef).refresh(session, config);
    }

    public static void removeHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        player.getHudManager().removeCustomHud(playerRef, WraithBustersConstants.ROUND_TIMER_HUD_KEY);
    }
}
