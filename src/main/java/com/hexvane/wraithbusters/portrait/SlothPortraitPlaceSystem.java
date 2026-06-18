package com.hexvane.wraithbusters.portrait;

import com.hexvane.wraithbusters.portrait.PortraitVariant;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class SlothPortraitPlaceSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    public SlothPortraitPlaceSystem() {
        super(PlaceBlockEvent.class);
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PlaceBlockEvent event
    ) {
        ItemStack itemStack = event.getItemInHand();
        if (itemStack == null || itemStack.getItem() == null || !itemStack.getItem().hasBlockType()) {
            return;
        }
        PortraitVariant variant = PortraitVariant.fromBlockId(itemStack.getItem().getBlockId());
        if (variant == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        SlothPortraitService.onBlockPlaced(world, event.getTargetBlock(), event.getRotation(), variant);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
