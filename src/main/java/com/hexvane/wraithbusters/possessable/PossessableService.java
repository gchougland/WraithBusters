package com.hexvane.wraithbusters.possessable;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.arena.PossessableMarker;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hexvane.wraithbusters.ui.GhostManaHudSupport;
import com.hexvane.wraithbusters.util.BlockSectionQueries;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hexvane.wraithbusters.util.FurnitureAnchorUtil;
import com.hexvane.wraithbusters.util.StatueAnchorUtil;
import com.hexvane.wraithbusters.util.WatcherStatueAnchorUtil;
import com.hexvane.wraithbusters.util.WraithBustersSoundUtil;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
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
    /** Statue_Full is three blocks tall; arena markers may be a few blocks off on Y. */
    private static final int STATUE_MARKER_Y_TOLERANCE = 3;
    /** Watcher statues span two blocks tall; arena markers may be on either block. */
    private static final int WATCHER_MARKER_Y_TOLERANCE = 1;

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
            int yTolerance = switch (marker.getTypeId()) {
                case "statue" -> STATUE_MARKER_Y_TOLERANCE;
                case "watcher" -> WATCHER_MARKER_Y_TOLERANCE;
                default -> 0;
            };
            if (Math.abs(pos.y - blockPos.y) <= yTolerance
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
        BUSY,
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
        @Nonnull WraithBustersPluginConfig config,
        @Nonnull Vector3i targetBlock
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
        if ("statue".equals(typeId)) {
            return activateStatue(session, world, store, state, marker, config, pr, targetBlock);
        }
        if ("bush".equals(typeId)) {
            return activateBush(session, world, ghostRef, store, state, marker, config, pr);
        }
        if ("hive".equals(typeId)) {
            return activateHive(session, world, ghostRef, store, state, marker, config, pr);
        }
        if ("cocoon".equals(typeId)) {
            return activateCocoon(session, world, ghostRef, store, state, marker, config, pr);
        }
        if ("skull".equals(typeId)) {
            return activateSkull(session, world, ghostRef, store, state, marker, config, pr);
        }
        if ("watcher".equals(typeId)) {
            return activateWatcher(session, world, ghostRef, store, state, marker, config, pr, targetBlock);
        }
        if ("barrel".equals(typeId)) {
            return activateBarrel(session, world, ghostRef, store, state, marker, config, pr);
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
        WraithBustersSoundUtil.play3dAtPosition(
            world,
            origin.x,
            origin.y,
            origin.z,
            WraithBustersConstants.PLATE_LAUNCH_SOUND_EVENT
        );
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

    @Nonnull
    private static ActivateResult activateCocoon(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> ghostRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerSessionState state,
        @Nonnull PossessableMarker marker,
        @Nonnull WraithBustersPluginConfig config,
        @Nonnull PlayerRef ghostPlayer
    ) {
        if (state.getGhostMana() < config.getCocoonManaCost()) {
            ghostPlayer.sendMessage(Message.translation("server.wraithbusters.mana.notEnough"));
            return ActivateResult.NOT_ENOUGH_MANA;
        }
        state.setGhostMana(state.getGhostMana() - config.getCocoonManaCost());
        Player player = store.getComponent(ghostRef, Player.getComponentType());
        if (player != null) {
            GhostManaHudSupport.refresh(player, ghostPlayer, state, config);
        }
        Vector3i pos = marker.getBlockPos();
        Vector3d origin = new Vector3d(pos.x + 0.5, pos.y + 0.15, pos.z + 0.5);
        Vector3d particleOrigin = new Vector3d(origin.x, origin.y - 1.0, origin.z);
        ParticleUtil.spawnParticleEffect(
            WraithBustersConstants.COCOON_BURST_PARTICLE,
            particleOrigin,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            WraithBustersConstants.COCOON_BURST_DURATION_SEC,
            store
        );
        WraithBustersSoundUtil.play3dAtPosition(
            world,
            origin.x,
            origin.y,
            origin.z,
            WraithBustersConstants.COCOON_ACTIVATE_SOUND_EVENT
        );
        applyCocoonBurstToHumansInRadius(session, world, store, origin, config);
        ghostPlayer.sendMessage(Message.translation("server.wraithbusters.possess.cocoon"));
        return ActivateResult.SUCCESS;
    }

    @Nonnull
    private static ActivateResult activateStatue(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerSessionState state,
        @Nonnull PossessableMarker marker,
        @Nonnull WraithBustersPluginConfig config,
        @Nonnull PlayerRef ghostPlayer,
        @Nonnull Vector3i targetBlock
    ) {
        if (state.getGhostMana() < config.getStatueManaCost()) {
            ghostPlayer.sendMessage(Message.translation("server.wraithbusters.mana.notEnough"));
            return ActivateResult.NOT_ENOUGH_MANA;
        }
        Vector3i anchor = StatueAnchorUtil.resolveStatueAnchor(world, targetBlock);
        if (SwordStatueSwingService.isSwinging(world, anchor)) {
            ghostPlayer.sendMessage(Message.translation("server.wraithbusters.possess.statueBusy"));
            return ActivateResult.BUSY;
        }
        state.setGhostMana(state.getGhostMana() - config.getStatueManaCost());
        Player player = store.getComponent(ghostPlayer.getReference(), Player.getComponentType());
        if (player != null) {
            GhostManaHudSupport.refresh(player, ghostPlayer, state, config);
        }
        SwordStatueSwingService.triggerSwing(world, anchor, config);
        Vector3d origin = new Vector3d(anchor.x + 0.5, anchor.y + 1.2, anchor.z + 0.5);
        WraithBustersSoundUtil.play3dAtPosition(
            world,
            origin.x,
            origin.y,
            origin.z,
            WraithBustersConstants.STATUE_SWING_SOUND_EVENT
        );
        ghostPlayer.sendMessage(Message.translation("server.wraithbusters.possess.statue"));
        return ActivateResult.SUCCESS;
    }

    @Nonnull
    private static ActivateResult activateWatcher(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> ghostRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerSessionState state,
        @Nonnull PossessableMarker marker,
        @Nonnull WraithBustersPluginConfig config,
        @Nonnull PlayerRef ghostPlayer,
        @Nonnull Vector3i targetBlock
    ) {
        if (state.getGhostMana() < config.getWatcherManaCost()) {
            ghostPlayer.sendMessage(Message.translation("server.wraithbusters.mana.notEnough"));
            return ActivateResult.NOT_ENOUGH_MANA;
        }
        Vector3i anchor = WatcherStatueAnchorUtil.resolveWatcherAnchor(world, targetBlock);
        if (WatcherStatueBurstService.isBusy(world, targetBlock)) {
            ghostPlayer.sendMessage(Message.translation("server.wraithbusters.possess.watcherBusy"));
            return ActivateResult.BUSY;
        }
        state.setGhostMana(state.getGhostMana() - config.getWatcherManaCost());
        Player player = store.getComponent(ghostRef, Player.getComponentType());
        if (player != null) {
            GhostManaHudSupport.refresh(player, ghostPlayer, state, config);
        }
        WatcherStatueBurstService.triggerBurst(world, targetBlock, marker, ghostRef, config);
        ghostPlayer.sendMessage(Message.translation("server.wraithbusters.possess.watcher"));
        return ActivateResult.SUCCESS;
    }

    @Nonnull
    private static ActivateResult activateBarrel(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> ghostRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerSessionState state,
        @Nonnull PossessableMarker marker,
        @Nonnull WraithBustersPluginConfig config,
        @Nonnull PlayerRef ghostPlayer
    ) {
        if (state.getGhostMana() < config.getBarrelManaCost()) {
            ghostPlayer.sendMessage(Message.translation("server.wraithbusters.mana.notEnough"));
            return ActivateResult.NOT_ENOUGH_MANA;
        }
        state.setGhostMana(state.getGhostMana() - config.getBarrelManaCost());
        Player player = store.getComponent(ghostRef, Player.getComponentType());
        if (player != null) {
            GhostManaHudSupport.refresh(player, ghostPlayer, state, config);
        }
        Vector3i pos = marker.getBlockPos();
        Vector3d origin = new Vector3d(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5);
        Ref<EntityStore> preferredHuman = findNearestHuman(session, world, marker);
        WraithBustersSoundUtil.play3dAtPosition(
            world,
            origin.x,
            origin.y,
            origin.z,
            WraithBustersConstants.BARREL_ACTIVATE_SOUND_EVENT
        );
        DeferredWorldTasks.run(
            world,
            () -> PossessableFoodTornadoService.spawn(session, world, origin, ghostRef, preferredHuman, config)
        );
        ghostPlayer.sendMessage(Message.translation("server.wraithbusters.possess.barrel"));
        return ActivateResult.SUCCESS;
    }

    @Nonnull
    private static ActivateResult activateBush(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> ghostRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerSessionState state,
        @Nonnull PossessableMarker marker,
        @Nonnull WraithBustersPluginConfig config,
        @Nonnull PlayerRef ghostPlayer
    ) {
        if (state.getGhostMana() < config.getBushManaCost()) {
            ghostPlayer.sendMessage(Message.translation("server.wraithbusters.mana.notEnough"));
            return ActivateResult.NOT_ENOUGH_MANA;
        }
        state.setGhostMana(state.getGhostMana() - config.getBushManaCost());
        Player player = store.getComponent(ghostRef, Player.getComponentType());
        if (player != null) {
            GhostManaHudSupport.refresh(player, ghostPlayer, state, config);
        }
        Vector3i pos = marker.getBlockPos();
        Vector3d origin = new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
        Ref<EntityStore> preferredHuman = findNearestHuman(session, world, marker);
        WraithBustersSoundUtil.play3dAtPosition(
            world,
            origin.x,
            origin.y,
            origin.z,
            WraithBustersConstants.BUSH_ACTIVATE_SOUND_EVENT
        );
        DeferredWorldTasks.run(world, () -> PossessableSnapdragonService.spawn(session, world, origin, preferredHuman));
        ghostPlayer.sendMessage(Message.translation("server.wraithbusters.possess.bush"));
        return ActivateResult.SUCCESS;
    }

    @Nonnull
    private static ActivateResult activateHive(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> ghostRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerSessionState state,
        @Nonnull PossessableMarker marker,
        @Nonnull WraithBustersPluginConfig config,
        @Nonnull PlayerRef ghostPlayer
    ) {
        if (state.getGhostMana() < config.getHiveManaCost()) {
            ghostPlayer.sendMessage(Message.translation("server.wraithbusters.mana.notEnough"));
            return ActivateResult.NOT_ENOUGH_MANA;
        }
        Ref<EntityStore> preferredHuman = findNearestHuman(session, world, marker);
        if (preferredHuman == null) {
            ghostPlayer.sendMessage(Message.translation("server.wraithbusters.possess.noTarget"));
            return ActivateResult.NO_TARGET;
        }
        state.setGhostMana(state.getGhostMana() - config.getHiveManaCost());
        Player player = store.getComponent(ghostRef, Player.getComponentType());
        if (player != null) {
            GhostManaHudSupport.refresh(player, ghostPlayer, state, config);
        }
        Vector3i pos = marker.getBlockPos();
        Vector3d origin = new Vector3d(pos.x + 0.5, pos.y + 0.65, pos.z + 0.5);
        WraithBustersSoundUtil.play3dAtPosition(
            world,
            origin.x,
            origin.y,
            origin.z,
            WraithBustersConstants.HIVE_ACTIVATE_SOUND_EVENT
        );
        DeferredWorldTasks.run(
            world,
            () -> PossessableHiveSwarmService.spawn(session, world, origin, preferredHuman, config)
        );
        ghostPlayer.sendMessage(Message.translation("server.wraithbusters.possess.hive"));
        return ActivateResult.SUCCESS;
    }

    @Nonnull
    private static ActivateResult activateSkull(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> ghostRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerSessionState state,
        @Nonnull PossessableMarker marker,
        @Nonnull WraithBustersPluginConfig config,
        @Nonnull PlayerRef ghostPlayer
    ) {
        if (state.getGhostMana() < config.getSkullManaCost()) {
            ghostPlayer.sendMessage(Message.translation("server.wraithbusters.mana.notEnough"));
            return ActivateResult.NOT_ENOUGH_MANA;
        }
        Ref<EntityStore> preferredHuman = findNearestHuman(session, world, marker);
        if (preferredHuman == null) {
            ghostPlayer.sendMessage(Message.translation("server.wraithbusters.possess.noTarget"));
            return ActivateResult.NO_TARGET;
        }
        state.setGhostMana(state.getGhostMana() - config.getSkullManaCost());
        Player player = store.getComponent(ghostRef, Player.getComponentType());
        if (player != null) {
            GhostManaHudSupport.refresh(player, ghostPlayer, state, config);
        }
        Vector3i pos = marker.getBlockPos();
        Vector3d origin = new Vector3d(pos.x + 0.5, pos.y - 0.35, pos.z + 0.5);
        DeferredWorldTasks.run(
            world,
            () -> PossessableFlamingSkullService.spawn(session, world, origin, preferredHuman, config)
        );
        ghostPlayer.sendMessage(Message.translation("server.wraithbusters.possess.skull"));
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

    private static void applyCocoonBurstToHumansInRadius(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d origin,
        @Nonnull WraithBustersPluginConfig config
    ) {
        DamageCause cause = resolveEnvironmentCause();
        EntityEffect slowEffect = EntityEffect.getAssetMap().getAsset(WraithBustersConstants.COCOON_SLOW_EFFECT_ID);
        double radiusSq = config.getCocoonBurstRadius() * config.getCocoonBurstRadius();
        for (Ref<EntityStore> humanRef : findHumansInRadius(session, world, origin, radiusSq)) {
            if (cause != null) {
                DamageSystems.executeDamage(
                    humanRef,
                    store,
                    new Damage(Damage.NULL_SOURCE, cause, config.getCocoonDamage())
                );
            }
            if (slowEffect != null) {
                EffectControllerComponent effectController = store.getComponent(
                    humanRef,
                    EffectControllerComponent.getComponentType()
                );
                if (effectController != null) {
                    effectController.addEffect(
                        humanRef,
                        slowEffect,
                        config.getCocoonSlowDurationSeconds(),
                        OverlapBehavior.OVERWRITE,
                        store
                    );
                }
            }
        }
    }

    @Nullable
    private static DamageCause resolveEnvironmentCause() {
        IndexedLookupTableAssetMap<String, DamageCause> map = DamageCause.getAssetMap();
        DamageCause env = map.getAsset("Environment");
        if (env != null) {
            return env;
        }
        return map.getAsset("OutOfWorld");
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

    /**
     * Resolves the world block to attach ghost marker icons to. Returns null while the chunk is unloaded or
     * the possessable block cannot be found near the arena marker (caller should retry later).
     */
    @Nullable
    public static Vector3i resolveMarkerAnchor(@Nonnull World world, @Nonnull PossessableMarker marker) {
        Vector3i markerPos = marker.getBlockPos();
        if (world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(markerPos.x, markerPos.z)) == null) {
            return null;
        }
        String typeId = marker.getTypeId();
        if ("statue".equals(typeId)) {
            Vector3i anchor = StatueAnchorUtil.resolveStatueAnchor(world, markerPos);
            if (!isLoadedPossessableAnchor(world, anchor, WraithBustersConstants.POSSESSABLE_STATUE_BLOCK_ID)) {
                return null;
            }
            return anchor;
        }
        if ("watcher".equals(typeId)) {
            Vector3i anchor = WatcherStatueAnchorUtil.resolveWatcherAnchor(world, markerPos);
            if (!isLoadedPossessableAnchor(world, anchor, WraithBustersConstants.POSSESSABLE_WATCHER_STATUE_BLOCK_ID)) {
                return null;
            }
            return anchor;
        }
        String expectedBlockId = expectedBlockIdForType(typeId);
        Vector3i found = findLoadedPossessableNear(world, markerPos, expectedBlockId, MARKER_XZ_TOLERANCE, 3);
        if (found != null) {
            return found;
        }
        return findLoadedPossessableNear(world, markerPos, expectedBlockId, 4, 4);
    }

    @Nonnull
    private static String expectedBlockIdForType(@Nonnull String typeId) {
        return switch (typeId) {
            case "candle" -> WraithBustersConstants.POSSESSABLE_CANDLE_BLOCK_ID;
            case "bush" -> WraithBustersConstants.POSSESSABLE_BUSH_BLOCK_ID;
            case "hive" -> WraithBustersConstants.POSSESSABLE_HIVE_BLOCK_ID;
            case "cocoon" -> WraithBustersConstants.POSSESSABLE_COCOON_BLOCK_ID;
            case "watcher" -> WraithBustersConstants.POSSESSABLE_WATCHER_STATUE_BLOCK_ID;
            case "barrel" -> WraithBustersConstants.POSSESSABLE_BARREL_BLOCK_ID;
            case "skull" -> WraithBustersConstants.POSSESSABLE_SKULL_WALL_BLOCK_ID;
            default -> WraithBustersConstants.POSSESSABLE_PLATE_BLOCK_ID;
        };
    }

    @Nullable
    private static Vector3i findLoadedPossessableNear(
        @Nonnull World world,
        @Nonnull Vector3i markerPos,
        @Nonnull String expectedBlockId,
        int radiusXZ,
        int radiusY
    ) {
        Vector3i best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int dy = -radiusY; dy <= radiusY; dy++) {
            for (int dx = -radiusXZ; dx <= radiusXZ; dx++) {
                for (int dz = -radiusXZ; dz <= radiusXZ; dz++) {
                    Vector3i probe = new Vector3i(markerPos.x + dx, markerPos.y + dy, markerPos.z + dz);
                    Vector3i anchor = FurnitureAnchorUtil.resolveAnchor(world, probe);
                    if (!isLoadedPossessableAnchor(world, anchor, expectedBlockId)) {
                        continue;
                    }
                    int score = Math.abs(anchor.x - markerPos.x)
                        + Math.abs(anchor.y - markerPos.y)
                        + Math.abs(anchor.z - markerPos.z);
                    if (score < bestScore) {
                        bestScore = score;
                        best = anchor;
                    }
                }
            }
        }
        return best;
    }

    private static boolean isLoadedPossessableAnchor(
        @Nonnull World world,
        @Nonnull Vector3i anchor,
        @Nonnull String expectedBlockId
    ) {
        if (BlockSectionQueries.getFiller(world, anchor.x, anchor.y, anchor.z) != 0) {
            return false;
        }
        BlockType blockType = BlockSectionQueries.getBlockTypeIfLoaded(world, anchor.x, anchor.y, anchor.z);
        return blockType != null && expectedBlockId.equals(blockType.getId());
    }

    private static boolean isPossessableBlock(@Nonnull World world, @Nonnull Vector3i blockPos) {
        BlockType blockType = world.getBlockType(blockPos.x, blockPos.y, blockPos.z);
        if (blockType == null) {
            return false;
        }
        String id = blockType.getId();
        return WraithBustersConstants.POSSESSABLE_PLATE_BLOCK_ID.equals(id)
            || WraithBustersConstants.POSSESSABLE_CANDLE_BLOCK_ID.equals(id)
            || WraithBustersConstants.POSSESSABLE_STATUE_BLOCK_ID.equals(id)
            || WraithBustersConstants.POSSESSABLE_BUSH_BLOCK_ID.equals(id)
            || WraithBustersConstants.POSSESSABLE_HIVE_BLOCK_ID.equals(id)
            || WraithBustersConstants.POSSESSABLE_COCOON_BLOCK_ID.equals(id)
            || WraithBustersConstants.POSSESSABLE_WATCHER_STATUE_BLOCK_ID.equals(id)
            || WraithBustersConstants.POSSESSABLE_SKULL_WALL_BLOCK_ID.equals(id);
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
