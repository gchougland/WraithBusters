package com.hexvane.wraithbusters.instance;

import com.hexvane.wraithbusters.util.WorldThreadTasks;
import com.hexvane.wraithbusters.portrait.SlothPortraitService;
import com.hypixel.hytale.builtin.portals.systems.PortalInvalidDestinationSystem;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hexvane.wraithbusters.WraithBustersPlugin;
import javax.annotation.Nonnull;

/**
 * WraithBusters spawns and removes instance worlds. That fires {@link RemoveWorldEvent}, which
 * makes vanilla {@code PortalsPlugin} queue deferred portal cleanup on other worlds even when no
 * {@code PortalDevice} blocks exist. During shutdown those tasks can run after the chunk store is
 * closed and log {@code Store is shutdown!}.
 */
public final class WorldRemovalListener {
    private WorldRemovalListener() {}

    public static void register(@Nonnull WraithBustersPlugin plugin) {
        var registry = plugin.getEventRegistry();
        registry.registerGlobal(EventPriority.FIRST, RemoveWorldEvent.class, WorldRemovalListener::runPortalCleanupNow);
        registry.registerGlobal(EventPriority.LAST, RemoveWorldEvent.class, WorldRemovalListener::drainDeferredCleanup);
        registry.registerGlobal(EventPriority.LAST, RemoveWorldEvent.class, WorldRemovalListener::shutdownPortraits);
    }

    private static void shutdownPortraits(@Nonnull RemoveWorldEvent event) {
        SlothPortraitService.shutdownWorld(event.getWorld());
    }

    private static void runPortalCleanupNow(@Nonnull RemoveWorldEvent event) {
        World removed = event.getWorld();
        for (World world : Universe.get().getWorlds().values()) {
            if (world == removed) {
                continue;
            }
            WorldThreadTasks.runOnWorldThread(world, () -> turnOffPortalsIfOpen(world, removed));
        }
    }

    private static void drainDeferredCleanup(@Nonnull RemoveWorldEvent event) {
        World removed = event.getWorld();
        if (removed.isAlive()) {
            WorldThreadTasks.drainQueue(removed);
        }
        for (World world : Universe.get().getWorlds().values()) {
            if (world == removed) {
                continue;
            }
            WorldThreadTasks.drainQueue(world);
        }
    }

    private static void turnOffPortalsIfOpen(@Nonnull World originWorld, @Nonnull World destinationWorld) {
        Store<ChunkStore> store = originWorld.getChunkStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        PortalInvalidDestinationSystem.turnOffPortalsInWorld(originWorld, destinationWorld);
    }
}
