package com.hexvane.wraithbusters.block;

import com.hexvane.wraithbusters.WraithBustersConstants;
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

public final class ExorcismTablePlaceSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    public ExorcismTablePlaceSystem() {
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
        if (!WraithBustersConstants.EXORCISM_TABLE_BLOCK_ID.equals(itemStack.getItem().getBlockId())) {
            return;
        }
        World world = store.getExternalData().getWorld();
        ExorcismTableFillerRepairService.repairAt(world, event.getTargetBlock());
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
