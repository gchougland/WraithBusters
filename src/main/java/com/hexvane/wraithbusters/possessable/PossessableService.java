package com.hexvane.wraithbusters.possessable;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.arena.PossessableMarker;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hexvane.wraithbusters.ui.GhostManaHudSupport;
import com.hexvane.wraithbusters.util.WraithBustersSoundUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

public final class PossessableService {
    private static final int MARKER_XZ_TOLERANCE = 1;

    private PossessableService() {}

    @Nullable
    public static PossessableMarker findAt(@Nonnull GameSession session, @Nonnull Vector3i blockPos) {
        return findAt(session, null, blockPos);
    }

    @Nullable
    public static PossessableMarker findAt(
        @Nonnull GameSession session,
        @Nullable World world,
        @Nonnull Vector3i blockPos
    ) {
        for (PossessableMarker marker : session.getArenaLayout().getPossessables()) {
            Vector3i pos = marker.getBlockPos();
            if (pos.x == blockPos.x && pos.y == blockPos.y && pos.z == blockPos.z) {
                return marker;
            }
        }
        for (PossessableMarker marker : session.getArenaLayout().getPossessables()) {
            Vector3i pos = marker.getBlockPos();
            if (pos.y == blockPos.y
                && Math.abs(pos.x - blockPos.x) <= MARKER_XZ_TOLERANCE
                && Math.abs(pos.z - blockPos.z) <= MARKER_XZ_TOLERANCE) {
                return marker;
            }
        }
        if (world != null && isPossessableBlock(world, blockPos)) {
            return findNearestMarker(session, blockPos);
        }
        return null;
    }

    public enum ActivateResult {
        SUCCESS,
        NOT_ACTIVE,
        NOT_GHOST,
        NOT_ENOUGH_MANA,
        NO_TARGET,
        UNKNOWN_TYPE
    }

    @Nonnull
    public static ActivateResult tryActivate(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> ghostRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PossessableMarker marker,
        @Nonnull WraithBustersPluginConfig config
    ) {
        if (session.getPhase() != GamePhase.ACTIVE) {
            return ActivateResult.NOT_ACTIVE;
        }
        PlayerRef pr = store.getComponent(ghostRef, PlayerRef.getComponentType());
        if (pr == null) {
            return ActivateResult.NOT_GHOST;
        }
        PlayerSessionState state = session.getPlayers().get(pr.getUuid());
        if (state == null || state.getRole() != PlayerRole.GHOST) {
            return ActivateResult.NOT_GHOST;
        }
        String typeId = marker.getTypeId();
        if ("plate".equals(typeId)) {
            return activatePlate(session, world, ghostRef, store, commandBuffer, state, marker, config, pr);
        }
        if ("candle".equals(typeId)) {
            return activateCandle(session, world, ghostRef, store, state, marker, config, pr);
        }
        return ActivateResult.UNKNOWN_TYPE;
    }

    @Nonnull
    private static ActivateResult activatePlate(
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
            return ActivateResult.NOT_ENOUGH_MANA;
        }
        Ref<EntityStore> target = findNearestHuman(session, world, marker);
        if (target == null) {
            ghostPlayer.sendMessage(Message.translation("server.wraithbusters.possess.noTarget"));
            return ActivateResult.NO_TARGET;
        }
        state.setGhostMana(state.getGhostMana() - config.getPlateManaCost());
        Player player = store.getComponent(ghostRef, Player.getComponentType());
        if (player != null) {
            GhostManaHudSupport.refresh(player, ghostPlayer, state, config);
        }
        Vector3i pos = marker.getBlockPos();
        Vector3d origin = new Vector3d(pos.x + 0.5, pos.y + 0.65, pos.z + 0.5);
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
        return ActivateResult.SUCCESS;
    }

    @Nonnull
    private static ActivateResult activateCandle(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> ghostRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerSessionState state,
        @Nonnull PossessableMarker marker,
        @Nonnull WraithBustersPluginConfig config,
        @Nonnull PlayerRef ghostPlayer
    ) {
        if (state.getGhostMana() < config.getCandleManaCost()) {
            ghostPlayer.sendMessage(Message.translation("server.wraithbusters.mana.notEnough"));
            return ActivateResult.NOT_ENOUGH_MANA;
        }
        state.setGhostMana(state.getGhostMana() - config.getCandleManaCost());
        Player player = store.getComponent(ghostRef, Player.getComponentType());
        if (player != null) {
            GhostManaHudSupport.refresh(player, ghostPlayer, state, config);
        }
        Vector3i pos = marker.getBlockPos();
        Vector3d origin = new Vector3d(pos.x + 0.5, pos.y + 0.15, pos.z + 0.5);
        Vector3d particleOrigin = new Vector3d(origin.x, origin.y - 1.0, origin.z);
        ParticleUtil.spawnParticleEffect(
            WraithBustersConstants.CANDLE_FIRE_RING_PARTICLE,
            particleOrigin,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            WraithBustersConstants.CANDLE_FIRE_RING_DURATION_SEC,
            store
        );
        WraithBustersSoundUtil.play3dAtPosition(
            world,
            origin.x,
            origin.y,
            origin.z,
            WraithBustersConstants.CANDLE_ACTIVATE_SOUND_EVENT
        );
        applyBurnToHumansInRadius(session, world, store, origin, config.getCandleFireRadius());
        ghostPlayer.sendMessage(Message.translation("server.wraithbusters.possess.candle"));
        return ActivateResult.SUCCESS;
    }

    private static void applyBurnToHumansInRadius(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d origin,
        float radius
    ) {
        EntityEffect burnEffect = EntityEffect.getAssetMap().getAsset(WraithBustersConstants.BURN_ENTITY_EFFECT_ID);
        if (burnEffect == null) {
            return;
        }
        double radiusSq = radius * radius;
        for (Ref<EntityStore> humanRef : findHumansInRadius(session, world, origin, radiusSq)) {
            EffectControllerComponent effectController = store.getComponent(humanRef, EffectControllerComponent.getComponentType());
            if (effectController != null) {
                effectController.addEffect(humanRef, burnEffect, store);
            }
        }
    }

    @Nonnull
    private static List<Ref<EntityStore>> findHumansInRadius(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Vector3d origin,
        double radiusSq
    ) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        List<Ref<EntityStore>> humans = new ArrayList<>();
        for (var entry : session.getPlayers().entrySet()) {
            if (entry.getValue().getRole() != PlayerRole.HUMAN || !entry.getValue().isAlive()) {
                continue;
            }
            PlayerRef pr = com.hypixel.hytale.server.core.universe.Universe.get().getPlayer(entry.getKey());
            if (pr == null) {
                continue;
            }
            Ref<EntityStore> ref = pr.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            var tc = store.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            if (tc == null) {
                continue;
            }
            if (tc.getPosition().distanceSquared(origin.x, origin.y, origin.z) <= radiusSq) {
                humans.add(ref);
            }
        }
        return humans;
    }

    private static boolean isPossessableBlock(@Nonnull World world, @Nonnull Vector3i blockPos) {
        BlockType blockType = world.getBlockType(blockPos.x, blockPos.y, blockPos.z);
        if (blockType == null) {
            return false;
        }
        String id = blockType.getId();
        return WraithBustersConstants.POSSESSABLE_PLATE_BLOCK_ID.equals(id)
            || WraithBustersConstants.POSSESSABLE_CANDLE_BLOCK_ID.equals(id);
    }

    @Nullable
    private static PossessableMarker findNearestMarker(@Nonnull GameSession session, @Nonnull Vector3i blockPos) {
        PossessableMarker best = null;
        int bestDist = Integer.MAX_VALUE;
        for (PossessableMarker marker : session.getArenaLayout().getPossessables()) {
            Vector3i pos = marker.getBlockPos();
            int dist = Math.abs(pos.x - blockPos.x) + Math.abs(pos.y - blockPos.y) + Math.abs(pos.z - blockPos.z);
            if (dist < bestDist) {
                bestDist = dist;
                best = marker;
            }
        }
        return bestDist <= 3 ? best : null;
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
