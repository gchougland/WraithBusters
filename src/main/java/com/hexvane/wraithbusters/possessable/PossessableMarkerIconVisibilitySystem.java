package com.hexvane.wraithbusters.possessable;

import com.hexvane.wraithbusters.debug.GhostTestService;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GameSession;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.SystemGroup;
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

/** Hides possessable marker icon entities from human players. Ghosts and creative-mode players still see them. */
public final class PossessableMarkerIconVisibilitySystem extends EntityTickingSystem<EntityStore> {
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
        if (session == null) {
            return;
        }
        boolean testMarkers = GhostTestService.hasTestMarkers(session);
        if (session.getPhase() != GamePhase.ACTIVE && !testMarkers) {
            return;
        }

        Set<Ref<EntityStore>> iconRefs = PossessableMarkerIconService.activeIconRefs(session.getSessionId());
        if (iconRefs.isEmpty()) {
            return;
        }

        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        Player player = chunk.getComponent(index, Player.getComponentType());
        EntityTrackerSystems.EntityViewer viewer = chunk.getComponent(index, EntityTrackerSystems.EntityViewer.getComponentType());
        if (playerRef == null || player == null || viewer == null) {
            return;
        }
        if (PossessableMarkerIconService.canPlayerSeeIcons(session, playerRef.getUuid(), player)) {
            return;
        }

        Iterator<Ref<EntityStore>> iterator = viewer.visible.iterator();
        while (iterator.hasNext()) {
            Ref<EntityStore> visibleRef = iterator.next();
            if (iconRefs.contains(visibleRef)) {
                viewer.hiddenCount++;
                iterator.remove();
            }
        }
    }
}
