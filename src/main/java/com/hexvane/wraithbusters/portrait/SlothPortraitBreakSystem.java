package com.hexvane.wraithbusters.portrait;

import com.hexvane.wraithbusters.portrait.PortraitVariant;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class SlothPortraitBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    public SlothPortraitBreakSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull BreakBlockEvent event
    ) {
        BlockType blockType = event.getBlockType();
        PortraitVariant variant = PortraitVariant.fromBlockId(blockType.getId());
        if (variant == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        SlothPortraitService.onBlockBroken(world, event.getTargetBlock());
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
