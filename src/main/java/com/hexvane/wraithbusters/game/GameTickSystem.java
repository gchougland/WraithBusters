package com.hexvane.wraithbusters.game;

import com.hexvane.wraithbusters.debug.GhostTestService;
import com.hexvane.wraithbusters.WraithBustersPlugin;
import com.hexvane.wraithbusters.pickup.ManaPickupService;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import javax.annotation.Nonnull;

public final class GameTickSystem extends TickingSystem<EntityStore> {
    @Nonnull
    private final WraithBustersPlugin plugin;
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();

    public GameTickSystem(@Nonnull WraithBustersPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        GameSession session = GameRegistry.get().getSessionForWorld(world.getWorldConfig().getUuid());
        if (session == null) {
            return;
        }
        // Store mutations must not run during system ticks; defer to the world task queue.
        DeferredWorldTasks.run(world, () -> {
            if (!DeferredWorldTasks.isStoreOpen(world)) {
                return;
            }
            plugin.getGameService().tickSession(session, world);
            if (session.getPhase() == GamePhase.ACTIVE || GhostTestService.hasTestMarkers(session)) {
                ManaPickupService.tick(session, world, plugin.getPluginConfig());
            }
        });
    }
}
