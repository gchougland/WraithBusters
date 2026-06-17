package com.hexvane.wraithbusters.ui;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

public final class GhostManaHudSupport {
    private GhostManaHudSupport() {}

    @Nonnull
    public static GhostManaHud obtainHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        CustomUIHud existing = player.getHudManager().getCustomHud(WraithBustersConstants.GHOST_MANA_HUD_KEY);
        if (existing instanceof GhostManaHud hud) {
            return hud;
        }
        GhostManaHud created = new GhostManaHud(playerRef);
        player.getHudManager().addCustomHud(playerRef, created);
        return created;
    }

    public static void refresh(
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef,
        @Nonnull PlayerSessionState state,
        @Nonnull WraithBustersPluginConfig config
    ) {
        obtainHud(player, playerRef).refresh(state, config);
    }

    public static void removeHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        player.getHudManager().removeCustomHud(playerRef, WraithBustersConstants.GHOST_MANA_HUD_KEY);
    }
}
