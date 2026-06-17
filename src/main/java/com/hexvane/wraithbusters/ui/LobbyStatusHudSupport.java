package com.hexvane.wraithbusters.ui;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameSession;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class LobbyStatusHudSupport {
    private LobbyStatusHudSupport() {}

    @Nonnull
    public static LobbyStatusHud obtainHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        var existing = player.getHudManager().getCustomHud(WraithBustersConstants.LOBBY_HUD_KEY);
        if (existing instanceof LobbyStatusHud hud) {
            return hud;
        }
        LobbyStatusHud created = new LobbyStatusHud(playerRef);
        player.getHudManager().addCustomHud(playerRef, created);
        return created;
    }

    public static void refresh(@Nonnull Player player, @Nonnull PlayerRef playerRef, @Nonnull GameSession session) {
        obtainHud(player, playerRef).refresh(session);
    }

    public static void refreshAll(@Nonnull GameSession session, @Nonnull World world) {
        if (session.getPhase() != GamePhase.LOBBY) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null) {
            return;
        }
        for (UUID playerUuid : session.playerUuidList()) {
            PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
            if (playerRef == null) {
                continue;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                refresh(player, playerRef, session);
            }
        }
    }

    public static void removeHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        player.getHudManager().removeCustomHud(playerRef, WraithBustersConstants.LOBBY_HUD_KEY);
    }
}
