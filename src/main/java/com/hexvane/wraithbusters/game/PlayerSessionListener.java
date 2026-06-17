package com.hexvane.wraithbusters.game;

import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.RemovedPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hexvane.wraithbusters.WraithBustersPlugin;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class PlayerSessionListener {
    private PlayerSessionListener() {}

    public static void register(@Nonnull WraithBustersPlugin plugin) {
        plugin.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> onDisconnect(plugin, event));
        plugin.getEventRegistry().registerGlobal(RemovedPlayerFromWorldEvent.class, event -> onRemovedFromWorld(plugin, event));
    }

    private static void onDisconnect(@Nonnull WraithBustersPlugin plugin, @Nonnull PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        UUID playerUuid = playerRef.getUuid();
        GameSession session = GameRegistry.get().getSessionForPlayer(playerUuid);
        if (session == null) {
            return;
        }
        var ref = playerRef.getReference();
        if (ref != null && ref.isValid()) {
            var store = ref.getStore();
            World world = store.getExternalData().getWorld();
            DeferredWorldTasks.run(world, () -> {
                if (ref.isValid()) {
                    plugin.getGameService().departPlayer(session, playerUuid, ref, store, false, true);
                } else {
                    plugin.getGameService().departPlayer(session, playerUuid, null, null, false, true);
                }
            });
        } else {
            plugin.getGameService().departPlayer(session, playerUuid, null, null, false, true);
        }
    }

    private static void onRemovedFromWorld(@Nonnull WraithBustersPlugin plugin, @Nonnull RemovedPlayerFromWorldEvent event) {
        World world = event.getWorld();
        GameSession session = GameRegistry.get().getSessionForWorld(world.getWorldConfig().getUuid());
        if (session == null || session.getPhase() != GamePhase.ENDING) {
            return;
        }
        PlayerRef playerRef = event.getHolder().getComponent(PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        UUID playerUuid = playerRef.getUuid();
        if (!session.getPlayers().containsKey(playerUuid)) {
            return;
        }
        plugin.getGameService().departPlayer(session, playerUuid, null, null, false, true);
    }
}
