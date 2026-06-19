package com.hexvane.wraithbusters.team;

import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GameSession;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Enforces team visibility every tick. Humans cannot see ghosts or spectators during an active round,
 * even when entity markers bypass {@code HiddenPlayersManager}.
 */
public final class GhostPlayerVisibilitySystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = Collections.singleton(
        new SystemDependency<>(Order.AFTER, EntityTrackerSystems.HideFromPlayer.class)
    );

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.FIND_VISIBLE_ENTITIES_GROUP;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            EntityTrackerSystems.EntityViewer.getComponentType(),
            PlayerRef.getComponentType(),
            Player.getComponentType()
        );
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        World world = store.getExternalData().getWorld();
        GameSession session = GameRegistry.get().getSessionForWorld(world.getWorldConfig().getUuid());
        if (session == null || session.getPhase() != GamePhase.ACTIVE) {
            return;
        }

        PlayerRef viewerPlayerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        Player viewer = chunk.getComponent(index, Player.getComponentType());
        EntityTrackerSystems.EntityViewer viewerComponent = chunk.getComponent(
            index,
            EntityTrackerSystems.EntityViewer.getComponentType()
        );
        if (viewerPlayerRef == null || viewer == null || viewerComponent == null) {
            return;
        }
        UUID viewerUuid = viewerPlayerRef.getUuid();
        if (TeamSetupService.canBypassTeamVisibility(viewerUuid, viewer)) {
            return;
        }
        if (session.getPlayers().get(viewerUuid) == null) {
            return;
        }

        Iterator<Ref<EntityStore>> iterator = viewerComponent.visible.iterator();
        while (iterator.hasNext()) {
            Ref<EntityStore> visibleRef = iterator.next();
            PlayerRef targetPlayerRef = store.getComponent(visibleRef, PlayerRef.getComponentType());
            if (targetPlayerRef == null) {
                continue;
            }
            if (TeamSetupService.shouldViewerSeeSessionPlayer(session, viewerUuid, targetPlayerRef.getUuid())) {
                continue;
            }
            viewerComponent.hiddenCount++;
            iterator.remove();
        }
    }
}
