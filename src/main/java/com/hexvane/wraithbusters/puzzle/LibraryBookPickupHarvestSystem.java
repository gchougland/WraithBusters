package com.hexvane.wraithbusters.puzzle;

import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Tracks library puzzle pickup state when book rubble is harvested via vanilla gather. */
public final class LibraryBookPickupHarvestSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    public LibraryBookPickupHarvestSystem() {
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
        if (event.isCancelled()) {
            return;
        }
        BlockType blockType = event.getBlockType();
        if (BookColor.fromPickupBlockId(blockType.getId()) == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        GameSession session = GameRegistry.get().getSessionForWorld(world.getWorldConfig().getUuid());
        if (session == null || session.getPhase() != GamePhase.ACTIVE) {
            return;
        }
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player != null) {
            var playerState = session.getOrCreatePlayer(player.getUuid());
            if (playerState.getRole() != PlayerRole.HUMAN || !playerState.isAlive()) {
                event.setCancelled(true);
                player.sendMessage(Message.translation("server.wraithbusters.puzzle.libraryBooks.humansOnly"));
                return;
            }
        }
        Vector3i blockPos = event.getTargetBlock();
        LibraryBookService.onPickupHarvested(world, blockPos);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
