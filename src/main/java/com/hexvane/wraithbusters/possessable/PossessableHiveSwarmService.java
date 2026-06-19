package com.hexvane.wraithbusters.possessable;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Tracks hive-summoned bee carriers: homing movement, poison on hit, timed despawn. */
public final class PossessableHiveSwarmService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ConcurrentHashMap<UUID, SessionBees> SESSIONS = new ConcurrentHashMap<>();
    private static final float TICK_DT_SEC = 0.05f;

    private PossessableHiveSwarmService() {}

    public static void spawn(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Vector3d hiveOrigin,
        @Nullable Ref<EntityStore> preferredHuman,
        @Nonnull WraithBustersPluginConfig config
    ) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            LOGGER.atWarning().log("NPCPlugin unavailable; cannot spawn possessable bee swarm");
            return;
        }

        SessionBees bees = SESSIONS.computeIfAbsent(session.getSessionId(), ignored -> new SessionBees());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int beeCount = Math.max(1, config.getHiveBeeCount());
        long maxDurationMs = config.getHiveBeeMaxDurationSeconds() * 1000L;

        for (int i = 0; i < beeCount; i++) {
            Vector3d spawnPos = new Vector3d(
                hiveOrigin.x + random.nextDouble(-0.35, 0.35),
                hiveOrigin.y + random.nextDouble(0.1, 0.6),
                hiveOrigin.z + random.nextDouble(-0.35, 0.35)
            );
            var pair = npc.spawnNPC(
                store,
                WraithBustersConstants.POSSESSABLE_BEE_NPC_ROLE,
                null,
                spawnPos,
                Rotation3f.ZERO
            );
            if (pair == null) {
                LOGGER.atWarning().log("Failed to spawn possessable bee at %s", spawnPos);
                continue;
            }
            BeeSpawn spawn = new BeeSpawn();
            spawn.entityRef = pair.first();
            spawn.targetRef = preferredHuman;
            spawn.despawnAtMs = System.currentTimeMillis() + maxDurationMs;
            bees.spawns.add(spawn);
        }
    }

    public static void tick(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull WraithBustersPluginConfig config
    ) {
        SessionBees bees = SESSIONS.get(session.getSessionId());
        if (bees == null || bees.spawns.isEmpty()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }

        long now = System.currentTimeMillis();
        float speed = config.getHiveBeeSpeed();
        double hitRadiusSq = config.getHiveBeeHitRadius() * config.getHiveBeeHitRadius();
        List<BeeSpawn> toRemove = new ArrayList<>();

        for (BeeSpawn spawn : bees.spawns) {
            Ref<EntityStore> ref = spawn.entityRef;
            if (ref == null || !ref.isValid()) {
                toRemove.add(spawn);
                continue;
            }
            if (now >= spawn.despawnAtMs) {
                despawnBee(store, ref);
                toRemove.add(spawn);
                continue;
            }

            Ref<EntityStore> target = resolveTarget(session, store, world, spawn.targetRef, ref);
            spawn.targetRef = target;
            if (target == null) {
                continue;
            }

            TransformComponent beeTransform = store.getComponent(ref, TransformComponent.getComponentType());
            TransformComponent targetTransform = store.getComponent(target, TransformComponent.getComponentType());
            if (beeTransform == null || targetTransform == null) {
                continue;
            }

            Vector3d beePos = new Vector3d(beeTransform.getPosition());
            Vector3d targetPos = new Vector3d(targetTransform.getPosition());
            targetPos.y += 0.9;
            Vector3d delta = targetPos.sub(beePos, new Vector3d());
            double distSq = delta.lengthSquared();
            if (distSq <= hitRadiusSq) {
                applyHit(session, store, target, config);
                despawnBee(store, ref);
                toRemove.add(spawn);
                continue;
            }

            if (distSq > 0.0001) {
                delta.normalize();
            } else {
                delta.set(0, 0, 1);
            }
            beePos.x += delta.x * speed * TICK_DT_SEC;
            beePos.y += delta.y * speed * TICK_DT_SEC;
            beePos.z += delta.z * speed * TICK_DT_SEC;
            beeTransform.setPosition(beePos);
        }

        for (BeeSpawn spawn : toRemove) {
            bees.spawns.remove(spawn);
        }
        if (bees.spawns.isEmpty()) {
            SESSIONS.remove(session.getSessionId());
        }
    }

    public static void endRound(@Nonnull GameSession session, @Nonnull World world) {
        clearSession(session.getSessionId(), world);
    }

    public static void clearForLobby(@Nonnull GameSession session, @Nonnull World world) {
        clearSession(session.getSessionId(), world);
    }

    @Nullable
    private static Ref<EntityStore> resolveTarget(
        @Nonnull GameSession session,
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nullable Ref<EntityStore> preferred,
        @Nonnull Ref<EntityStore> beeRef
    ) {
        if (isAliveHuman(session, store, preferred)) {
            return preferred;
        }
        return findNearestHuman(session, world, beeRef);
    }

    private static void applyHit(
        @Nonnull GameSession session,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> humanRef,
        @Nonnull WraithBustersPluginConfig config
    ) {
        if (!isAliveHuman(session, store, humanRef)) {
            return;
        }
        DamageCause cause = resolveEnvironmentCause();
        if (cause != null) {
            DamageSystems.executeDamage(humanRef, store, new Damage(Damage.NULL_SOURCE, cause, config.getHiveHitDamage()));
        }
        EntityEffect poisonEffect = EntityEffect.getAssetMap().getAsset(WraithBustersConstants.HIVE_POISON_EFFECT_ID);
        if (poisonEffect != null) {
            EffectControllerComponent effectController = store.getComponent(humanRef, EffectControllerComponent.getComponentType());
            if (effectController != null) {
                effectController.addEffect(
                    humanRef,
                    poisonEffect,
                    config.getHivePoisonDurationSeconds(),
                    OverlapBehavior.OVERWRITE,
                    store
                );
            }
        }
    }

    private static void despawnBee(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> entityRef) {
        if (entityRef.isValid()) {
            store.removeEntity(entityRef, RemoveReason.REMOVE);
        }
    }

    private static void clearSession(@Nonnull UUID sessionId, @Nonnull World world) {
        SessionBees bees = SESSIONS.remove(sessionId);
        if (bees == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        for (BeeSpawn spawn : new ArrayList<>(bees.spawns)) {
            Ref<EntityStore> ref = spawn.entityRef;
            if (ref != null && ref.isValid()) {
                despawnBee(store, ref);
            }
        }
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

    @Nullable
    private static DamageCause resolveEnvironmentCause() {
        IndexedLookupTableAssetMap<String, DamageCause> map = DamageCause.getAssetMap();
        DamageCause env = map.getAsset("Environment");
        if (env != null) {
            return env;
        }
        return map.getAsset("OutOfWorld");
    }

    private static final class SessionBees {
        private final List<BeeSpawn> spawns = new ArrayList<>();
    }

    private static final class BeeSpawn {
        @Nullable
        private Ref<EntityStore> entityRef;
        @Nullable
        private Ref<EntityStore> targetRef;
        private long despawnAtMs;
    }
}
