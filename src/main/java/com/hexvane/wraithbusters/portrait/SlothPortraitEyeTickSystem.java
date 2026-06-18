package com.hexvane.wraithbusters.portrait;

import com.hexvane.wraithbusters.WraithBustersPlugin;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import javax.annotation.Nonnull;

public final class SlothPortraitEyeTickSystem extends TickingSystem<EntityStore> {
    @Nonnull
    private final WraithBustersPlugin plugin;
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();

    public SlothPortraitEyeTickSystem(@Nonnull WraithBustersPlugin plugin) {
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
        if (!SlothPortraitService.hasPortraits(world)) {
            return;
        }
        DeferredWorldTasks.run(world, () -> {
            if (!DeferredWorldTasks.isStoreOpen(world)) {
                return;
            }
            SlothPortraitService.tickEyes(world, plugin.getPluginConfig());
        });
    }
}
