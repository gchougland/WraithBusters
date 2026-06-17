package com.hexvane.wraithbusters.ghost;

import com.hexvane.wraithbusters.arena.GhostPhaseDoorMarker;
import com.hexvane.wraithbusters.arena.PhaseDoorSize;
import com.hexvane.wraithbusters.debug.GhostTestService;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.util.PlayerTeleportUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

public final class GhostPhaseDoorSystem extends EntityTickingSystem<EntityStore> {
    private static final double COOLDOWN_MS = 2500L;

    private final Map<UUID, Long> lastPhase = new HashMap<>();

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType(), TransformComponent.getComponentType());
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
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        boolean testGhost = GhostTestService.isActive(pr.getUuid());
        if (session.getPhase() != GamePhase.ACTIVE && !testGhost) {
            return;
        }
        if (!testGhost && session.getOrCreatePlayer(pr.getUuid()).getRole() != PlayerRole.GHOST) {
            return;
        }
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        Vector3d pos = tc.getPosition();
        long now = System.currentTimeMillis();
        Long last = lastPhase.get(pr.getUuid());
        if (last != null && now - last < COOLDOWN_MS) {
            return;
        }
        for (GhostPhaseDoorMarker door : session.getArenaLayout().getGhostPhaseDoors()) {
            PhaseDoorSize size = door.getDoorSize();
            double radiusSq = size.triggerRadius() * size.triggerRadius();
            if (withinRadiusSq(pos, door.getEntry().getPosition(), radiusSq)) {
                PlayerTeleportUtil.teleport(ref, commandBuffer, world, door.getExit());
                lastPhase.put(pr.getUuid(), now);
                return;
            }
            if (withinRadiusSq(pos, door.getExit().getPosition(), radiusSq)) {
                PlayerTeleportUtil.teleport(ref, commandBuffer, world, door.getEntry());
                lastPhase.put(pr.getUuid(), now);
                return;
            }
        }
    }

    private static boolean withinRadiusSq(
        @Nonnull Vector3d pos,
        @Nonnull Vector3d target,
        double radiusSq
    ) {
        double dx = pos.x - target.x;
        double dy = pos.y - target.y;
        double dz = pos.z - target.z;
        return dx * dx + dy * dy + dz * dz <= radiusSq;
    }
}
