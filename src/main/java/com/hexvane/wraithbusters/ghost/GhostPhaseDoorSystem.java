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
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
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
    /** Minimum distance from door center plane to count as on one side or the other. */
    private static final double DOOR_SIDE_THRESHOLD = 0.15;

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
        BoundingBox boundingBox = store.getComponent(ref, BoundingBox.getComponentType());
        if (boundingBox == null) {
            return;
        }
        Box playerBox = boundingBox.getBoundingBox();
        Vector3d pos = tc.getPosition();
        long now = System.currentTimeMillis();
        Long last = lastPhase.get(pr.getUuid());
        if (last != null && now - last < COOLDOWN_MS) {
            return;
        }
        for (GhostPhaseDoorMarker door : session.getArenaLayout().getGhostPhaseDoors()) {
            if (tryPhaseThrough(ref, commandBuffer, world, door, pos, now, pr.getUuid(), playerBox)) {
                return;
            }
        }
    }

    private boolean tryPhaseThrough(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull GhostPhaseDoorMarker door,
        @Nonnull Vector3d pos,
        long now,
        @Nonnull UUID playerUuid,
        @Nonnull Box playerBox
    ) {
        PhaseDoorSize size = door.getDoorSize();
        double radiusSq = size.triggerRadius() * size.triggerRadius();
        Vector3d entryPos = door.getEntry().getPosition();
        Vector3d exitPos = door.getExit().getPosition();
        Vector3d midpoint = midpoint(entryPos, exitPos);
        // Points from entry side toward exit side (through the door).
        Vector3d towardExit = outwardFrom(midpoint, exitPos);
        double doorSide = dot(pos, midpoint, towardExit);

        if (doorSide < -DOOR_SIDE_THRESHOLD
            && withinRadiusSq(pos, entryPos, radiusSq)) {
            PlayerTeleportUtil.teleport(
                ref,
                commandBuffer,
                world,
                PhasePortalTeleportUtil.buildEgress(
                    world,
                    playerBox,
                    door.getExit(),
                    midpoint,
                    entryPos,
                    size.triggerRadius()
                )
            );
            lastPhase.put(playerUuid, now);
            return true;
        }
        if (doorSide > DOOR_SIDE_THRESHOLD
            && withinRadiusSq(pos, exitPos, radiusSq)) {
            PlayerTeleportUtil.teleport(
                ref,
                commandBuffer,
                world,
                PhasePortalTeleportUtil.buildEgress(
                    world,
                    playerBox,
                    door.getEntry(),
                    midpoint,
                    exitPos,
                    size.triggerRadius()
                )
            );
            lastPhase.put(playerUuid, now);
            return true;
        }
        return false;
    }

    @Nonnull
    private static Vector3d midpoint(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        return new Vector3d(
            (a.x + b.x) * 0.5,
            (a.y + b.y) * 0.5,
            (a.z + b.z) * 0.5
        );
    }

    @Nonnull
    private static Vector3d outwardFrom(@Nonnull Vector3d midpoint, @Nonnull Vector3d portalPos) {
        Vector3d outward = new Vector3d(portalPos).sub(midpoint);
        double len = outward.length();
        if (len < 1e-6) {
            return new Vector3d(0.0, 0.0, 1.0);
        }
        return outward.div(len);
    }

    private static double dot(
        @Nonnull Vector3d from,
        @Nonnull Vector3d anchor,
        @Nonnull Vector3d axis
    ) {
        return (from.x - anchor.x) * axis.x
            + (from.y - anchor.y) * axis.y
            + (from.z - anchor.z) * axis.z;
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
