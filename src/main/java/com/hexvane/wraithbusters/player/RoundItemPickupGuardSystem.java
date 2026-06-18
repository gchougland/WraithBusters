package com.hexvane.wraithbusters.player;

import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GameSession;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Blocks vanilla item pickup during active rounds; humans collect items via {@link com.hexvane.wraithbusters.puzzle.HumanKeyPickupSystem}. */
public final class RoundItemPickupGuardSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    private final Query<EntityStore> query = Query.and(
        ItemComponent.getComponentType(),
        Query.not(PreventPickup.getComponentType())
    );

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        GameSession session = GameRegistry.get().getSessionForWorld(store.getExternalData().getWorld().getWorldConfig().getUuid());
        if (session == null || session.getPhase() != GamePhase.ACTIVE) {
            return;
        }
        commandBuffer.ensureComponent(chunk.getReferenceTo(index), PreventPickup.getComponentType());
    }
}
