package com.hexvane.wraithbusters.possessable;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hexvane.wraithbusters.puzzle.CheeseChaseService;
import com.hexvane.wraithbusters.util.WraithBustersSoundUtil;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Tracks barrel-summoned food tornadoes: homing chase, corn projectiles, timed despawn. */
public final class PossessableFoodTornadoService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ConcurrentHashMap<UUID, SessionTornadoes> SESSIONS = new ConcurrentHashMap<>();
    private static final float TICK_DT_SEC = 0.05f;

    private PossessableFoodTornadoService() {}

    public static void spawn(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Vector3d origin,
        @Nonnull Ref<EntityStore> ghostRef,
        @Nullable Ref<EntityStore> preferredHuman,
        @Nonnull WraithBustersPluginConfig config
    ) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            LOGGER.atWarning().log("NPCPlugin unavailable; cannot spawn possessable food tornado");
            return;
        }

        var pair = npc.spawnNPC(
            store,
            WraithBustersConstants.POSSESSABLE_FOOD_TORNADO_NPC_ROLE,
            null,
            origin,
            Rotation3f.ZERO
        );
        if (pair == null) {
            LOGGER.atWarning().log("Failed to spawn possessable food tornado at %s", origin);
            return;
        }

        Ref<EntityStore> entityRef = pair.first();
        NPCEntity npcEntity = store.getComponent(entityRef, NPCEntity.getComponentType());
        if (npcEntity == null) {
            return;
        }

        double ignoreDuration = config.getBarrelFoodTornadoDurationSeconds() + 2.0;
        applyIgnoreAttitudes(session, store, npcEntity.getRole(), ignoreDuration);

        SessionTornadoes tornadoes = SESSIONS.computeIfAbsent(session.getSessionId(), ignored -> new SessionTornadoes());
        TornadoSpawn spawn = new TornadoSpawn();
        spawn.entityRef = entityRef;
        spawn.ghostRef = ghostRef;
        spawn.targetRef = isAliveHuman(session, store, preferredHuman)
            ? preferredHuman
            : findNearestHuman(session, world, entityRef);
        spawn.despawnAtMs = System.currentTimeMillis() + config.getBarrelFoodTornadoDurationSeconds() * 1000L;
        spawn.lastShotMs = System.currentTimeMillis();
        tornadoes.spawns.add(spawn);
    }

    public static void tick(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull WraithBustersPluginConfig config
    ) {
        SessionTornadoes tornadoes = SESSIONS.get(session.getSessionId());
        if (tornadoes == null || tornadoes.spawns.isEmpty()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }

        long now = System.currentTimeMillis();
        float speed = config.getBarrelFoodTornadoSpeed();
        long shotIntervalMs = config.getBarrelCornShotIntervalTicks() * 50L;
        double ignoreDuration = config.getBarrelFoodTornadoDurationSeconds() + 2.0;
        List<TornadoSpawn> toRemove = new ArrayList<>();

        for (TornadoSpawn spawn : tornadoes.spawns) {
            Ref<EntityStore> ref = spawn.entityRef;
            if (ref == null || !ref.isValid()) {
                toRemove.add(spawn);
                continue;
            }
            if (now >= spawn.despawnAtMs) {
                despawnWithPoof(store, ref);
                toRemove.add(spawn);
                continue;
            }

            NPCEntity npcEntity = store.getComponent(ref, NPCEntity.getComponentType());
            if (npcEntity == null) {
                toRemove.add(spawn);
                continue;
            }
            applyIgnoreAttitudes(session, store, npcEntity.getRole(), ignoreDuration);

            Ref<EntityStore> target = resolveTarget(session, store, world, spawn.targetRef, ref);
            spawn.targetRef = target;

            TransformComponent tornadoTransform = store.getComponent(ref, TransformComponent.getComponentType());
            if (tornadoTransform == null) {
                continue;
            }

            if (target != null) {
                TransformComponent targetTransform = store.getComponent(target, TransformComponent.getComponentType());
                if (targetTransform != null) {
                    Vector3d tornadoPos = new Vector3d(tornadoTransform.getPosition());
                    Vector3d targetPos = new Vector3d(targetTransform.getPosition());
                    targetPos.y += 0.9;
                    Vector3d delta = targetPos.sub(tornadoPos, new Vector3d());
                    if (delta.lengthSquared() > 0.0001) {
                        delta.normalize();
                    } else {
                        delta.set(0, 0, 1);
                    }
                    tornadoPos.x += delta.x * speed * TICK_DT_SEC;
                    tornadoPos.y += delta.y * speed * TICK_DT_SEC;
                    tornadoPos.z += delta.z * speed * TICK_DT_SEC;
                    tornadoTransform.setPosition(tornadoPos);
                }
            }

            if (target != null && spawn.ghostRef != null && spawn.ghostRef.isValid()
                && now - spawn.lastShotMs >= shotIntervalMs) {
                fireCornShot(world, store, spawn.ghostRef, tornadoTransform.getPosition(), target);
                spawn.lastShotMs = now;
            }
        }

        for (TornadoSpawn spawn : toRemove) {
            tornadoes.spawns.remove(spawn);
        }
        if (tornadoes.spawns.isEmpty()) {
            SESSIONS.remove(session.getSessionId());
        }
    }

    public static void endRound(@Nonnull GameSession session, @Nonnull World world) {
        clearSession(session.getSessionId(), world, true);
    }

    public static void clearForLobby(@Nonnull GameSession session, @Nonnull World world) {
        clearSession(session.getSessionId(), world, true);
    }

    private static void fireCornShot(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ghostRef,
        @Nonnull Vector3d origin,
        @Nonnull Ref<EntityStore> target
    ) {
        TransformComponent targetTransform = store.getComponent(target, TransformComponent.getComponentType());
        if (targetTransform == null) {
            return;
        }
        Vector3d targetPos = new Vector3d(targetTransform.getPosition());
        targetPos.y += 0.9;
        Vector3d dir = targetPos.sub(origin, new Vector3d());
        if (dir.lengthSquared() > 0.0001) {
            dir.normalize();
        } else {
            dir.set(0, 0, 1);
        }
        spawnCornFromStore(store, ghostRef, origin, dir);
        WraithBustersSoundUtil.play3dAtPosition(
            world,
            origin.x,
            origin.y,
            origin.z,
            WraithBustersConstants.BARREL_CORN_LAUNCH_SOUND_EVENT
        );
    }

    private static void spawnCornFromStore(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ghostRef,
        @Nonnull Vector3d position,
        @Nonnull Vector3d direction
    ) {
        ProjectileConfig projectileConfig = ProjectileConfig.getAssetMap()
            .getAsset(WraithBustersConstants.BARREL_CORN_PROJECTILE_CONFIG_ID);
        if (projectileConfig == null) {
            return;
        }

        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        Vector3d dir = new Vector3d(direction);
        Rotation3f rotation = new Rotation3f();
        Direction rotationOffset = projectileConfig.getSpawnRotationOffset();
        rotation.setYaw(PhysicsMath.normalizeTurnAngle(PhysicsMath.headingFromDirection(dir.x, dir.z)));
        rotation.setPitch(PhysicsMath.pitchFromDirection(dir.x, dir.y, dir.z));
        rotation.add(rotationOffset.pitch, rotationOffset.yaw, rotationOffset.roll);
        PhysicsMath.vectorFromAngles(rotation.yaw(), rotation.pitch(), dir);
        Vector3d spawnPos = new Vector3d(position);
        spawnPos.add(projectileConfig.getCalculatedOffset(rotation.pitch(), rotation.yaw()));

        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(spawnPos, rotation));
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rotation));
        holder.addComponent(Interactions.getComponentType(), new Interactions(projectileConfig.getInteractions()));
        var model = projectileConfig.getModel();
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));
        holder.addComponent(
            NetworkId.getComponentType(),
            new NetworkId(store.getExternalData().takeNextNetworkId())
        );
        holder.ensureComponent(ProjectileModule.get().getProjectileComponentType());
        holder.addComponent(Velocity.getComponentType(), new Velocity());
        projectileConfig.getPhysicsConfig().apply(
            holder,
            ghostRef,
            new Vector3d(dir).mul(projectileConfig.getLaunchForce()),
            store,
            false
        );
        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
        holder.addComponent(
            DespawnComponent.getComponentType(),
            new DespawnComponent(store.getResource(TimeResource.getResourceType()).getNow().plus(Duration.ofSeconds(300L)))
        );
        store.addEntity(holder, AddReason.SPAWN);
    }

    private static void applyIgnoreAttitudes(
        @Nonnull GameSession session,
        @Nonnull Store<EntityStore> store,
        @Nonnull Role role,
        double durationSec
    ) {
        forEachGhostRef(session, ref -> role.getWorldSupport().overrideAttitude(ref, Attitude.IGNORE, durationSec));
        CheeseChaseService.forEachActiveNpc(session, ref -> role.getWorldSupport().overrideAttitude(ref, Attitude.IGNORE, durationSec));
    }

    private static void despawnWithPoof(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> entityRef) {
        if (!entityRef.isValid()) {
            return;
        }
        TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
        if (transform != null) {
            Vector3d pos = transform.getPosition();
            ParticleUtil.spawnParticleEffect(
                WraithBustersConstants.POSSESSABLE_FOOD_TORNADO_POOF_PARTICLE,
                pos,
                0.0f,
                0.0f,
                0.0f,
                1.0f,
                WraithBustersConstants.POSSESSABLE_FOOD_TORNADO_POOF_DURATION_SEC,
                store
            );
        }
        store.removeEntity(entityRef, RemoveReason.REMOVE);
    }

    private static void clearSession(@Nonnull UUID sessionId, @Nonnull World world, boolean withPoof) {
        SessionTornadoes tornadoes = SESSIONS.remove(sessionId);
        if (tornadoes == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        for (TornadoSpawn spawn : new ArrayList<>(tornadoes.spawns)) {
            Ref<EntityStore> ref = spawn.entityRef;
            if (ref == null || !ref.isValid()) {
                continue;
            }
            if (withPoof) {
                despawnWithPoof(store, ref);
            } else {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
        }
    }

    @Nullable
    private static Ref<EntityStore> resolveTarget(
        @Nonnull GameSession session,
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nullable Ref<EntityStore> preferred,
        @Nonnull Ref<EntityStore> tornadoRef
    ) {
        if (isAliveHuman(session, store, preferred)) {
            return preferred;
        }
        return findNearestHuman(session, world, tornadoRef);
    }

    @Nullable
    private static Ref<EntityStore> findNearestHuman(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> originRef
    ) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        TransformComponent originTransform = store.getComponent(originRef, TransformComponent.getComponentType());
        if (originTransform == null) {
            return null;
        }
        Vector3d origin = originTransform.getPosition();
        Ref<EntityStore> best = null;
        double bestDist = Double.MAX_VALUE;
        for (var entry : session.getPlayers().entrySet()) {
            if (entry.getValue().getRole() != PlayerRole.HUMAN || !entry.getValue().isAlive()) {
                continue;
            }
            PlayerRef playerRef = Universe.get().getPlayer(entry.getKey());
            if (playerRef == null) {
                continue;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                continue;
            }
            double dist = transform.getPosition().distanceSquared(origin.x, origin.y, origin.z);
            if (dist < bestDist) {
                bestDist = dist;
                best = ref;
            }
        }
        return best;
    }

    private static boolean isAliveHuman(
        @Nonnull GameSession session,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> ref
    ) {
        if (ref == null || !ref.isValid()) {
            return false;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return false;
        }
        PlayerSessionState state = session.getPlayers().get(playerRef.getUuid());
        return state != null && state.getRole() == PlayerRole.HUMAN && state.isAlive();
    }

    private static void forEachGhostRef(@Nonnull GameSession session, @Nonnull Consumer<Ref<EntityStore>> consumer) {
        for (var entry : session.getPlayers().entrySet()) {
            if (entry.getValue().getRole() != PlayerRole.GHOST) {
                continue;
            }
            PlayerRef playerRef = Universe.get().getPlayer(entry.getKey());
            if (playerRef == null) {
                continue;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref != null && ref.isValid()) {
                consumer.accept(ref);
            }
        }
    }

    private static final class SessionTornadoes {
        private final List<TornadoSpawn> spawns = new ArrayList<>();
    }

    private static final class TornadoSpawn {
        @Nullable
        private Ref<EntityStore> entityRef;
        @Nullable
        private Ref<EntityStore> ghostRef;
        @Nullable
        private Ref<EntityStore> targetRef;
        private long despawnAtMs;
        private long lastShotMs;
    }
}
