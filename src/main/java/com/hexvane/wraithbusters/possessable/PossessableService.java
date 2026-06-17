package com.hexvane.wraithbusters.possessable;

import com.hexvane.wraithbusters.arena.PossessableMarker;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hexvane.wraithbusters.ui.GhostManaHudSupport;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

public final class PossessableService {
    private PossessableService() {}

    @Nullable
    public static PossessableMarker findAt(@Nonnull GameSession session, @Nonnull Vector3i blockPos) {
        for (PossessableMarker marker : session.getArenaLayout().getPossessables()) {
            Vector3i pos = marker.getBlockPos();
            if (pos.x == blockPos.x && pos.y == blockPos.y && pos.z == blockPos.z) {
                return marker;
            }
        }
        return null;
    }

    public static boolean tryActivate(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> ghostRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PossessableMarker marker,
        @Nonnull WraithBustersPluginConfig config
    ) {
        if (session.getPhase() != GamePhase.ACTIVE) {
            return false;
        }
        PlayerRef pr = store.getComponent(ghostRef, PlayerRef.getComponentType());
        if (pr == null) {
            return false;
        }
        PlayerSessionState state = session.getOrCreatePlayer(pr.getUuid());
        if (state.getRole() != PlayerRole.GHOST) {
            return false;
        }
        if ("plate".equals(marker.getTypeId())) {
            return activatePlate(session, world, ghostRef, store, commandBuffer, state, marker, config, pr);
        }
        return false;
    }

    private static boolean activatePlate(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> ghostRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PlayerSessionState state,
        @Nonnull PossessableMarker marker,
        @Nonnull WraithBustersPluginConfig config,
        @Nonnull PlayerRef ghostPlayer
    ) {
        if (state.getGhostMana() < config.getPlateManaCost()) {
            ghostPlayer.sendMessage(Message.translation("server.wraithbusters.mana.notEnough"));
            return false;
        }
        Ref<EntityStore> target = findNearestHuman(session, world, marker);
        if (target == null) {
            ghostPlayer.sendMessage(Message.translation("server.wraithbusters.possess.noTarget"));
            return false;
        }
        state.setGhostMana(state.getGhostMana() - config.getPlateManaCost());
        Player player = store.getComponent(ghostRef, Player.getComponentType());
        if (player != null) {
            GhostManaHudSupport.refresh(player, ghostPlayer, state, config);
        }
        Vector3i pos = marker.getBlockPos();
        Vector3d origin = new Vector3d(pos.x + 0.5, pos.y + 2.5, pos.z + 0.5);
        Vector3d targetPos = store.getComponent(target, com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType())
            .getPosition();
        Vector3d dir = new Vector3d(targetPos).sub(origin);
        if (dir.lengthSquared() > 0.0001) {
            dir.normalize();
        } else {
            dir.set(0, 0, 1);
        }
        ProjectileConfig projectileConfig = ProjectileConfig.getAssetMap().getAsset("WraithBusters_Plate_Projectile");
        if (projectileConfig != null) {
            com.hypixel.hytale.server.core.modules.projectile.ProjectileModule.get()
                .spawnProjectile(ghostRef, commandBuffer, projectileConfig, origin, dir);
        }
        ghostPlayer.sendMessage(Message.translation("server.wraithbusters.possess.plate"));
        return true;
    }

    @Nullable
    private static Ref<EntityStore> findNearestHuman(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull PossessableMarker marker
    ) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        Vector3i pos = marker.getBlockPos();
        Vector3d origin = new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
        Ref<EntityStore> best = null;
        double bestDist = Double.MAX_VALUE;
        for (var entry : session.getPlayers().entrySet()) {
            if (entry.getValue().getRole() != PlayerRole.HUMAN || !entry.getValue().isAlive()) {
                continue;
            }
            PlayerRef pr = com.hypixel.hytale.server.core.universe.Universe.get().getPlayer(entry.getKey());
            if (pr == null) {
                continue;
            }
            Ref<EntityStore> ref = pr.getReference();
            if (ref == null) {
                continue;
            }
            var tc = store.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            if (tc == null) {
                continue;
            }
            double dist = tc.getPosition().distanceSquared(origin.x, origin.y, origin.z);
            if (dist < bestDist) {
                bestDist = dist;
                best = ref;
            }
        }
        return best;
    }
}
