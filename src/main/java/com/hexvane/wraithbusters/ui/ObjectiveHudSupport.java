package com.hexvane.wraithbusters.ui;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

public final class ObjectiveHudSupport {
    private ObjectiveHudSupport() {}

    @Nonnull
    public static ObjectiveHud obtainHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        CustomUIHud existing = player.getHudManager().getCustomHud(WraithBustersConstants.OBJECTIVE_HUD_KEY);
        if (existing instanceof ObjectiveHud hud) {
            return hud;
        }
        ObjectiveHud created = new ObjectiveHud(playerRef);
        player.getHudManager().addCustomHud(playerRef, created);
        return created;
    }

    public static void refresh(
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef,
        @Nonnull GameSession session,
        @Nonnull PlayerSessionState state
    ) {
        obtainHud(player, playerRef).refresh(session, state);
    }

    public static void removeHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        player.getHudManager().removeCustomHud(playerRef, WraithBustersConstants.OBJECTIVE_HUD_KEY);
    }
}
