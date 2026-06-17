package com.hexvane.wraithbusters.util;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Defers work to the world thread without touching a shut-down entity store. */
public final class DeferredWorldTasks {
    private DeferredWorldTasks() {}

    public static boolean isStoreOpen(@Nonnull World world) {
        if (!world.isAlive()) {
            return false;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        return store != null && !store.isShutdown();
    }

    public static void run(@Nonnull World world, @Nonnull Runnable task) {
        if (!world.isAlive()) {
            return;
        }
        try {
            world.execute(() -> {
                if (!isStoreOpen(world)) {
                    return;
                }
                task.run();
            });
        } catch (RuntimeException ignored) {
            // World is no longer accepting tasks (shutting down).
        }
    }
}
