package com.hexvane.wraithbusters.possessable;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hexvane.wraithbusters.util.WraithBustersSoundUtil;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Tracks skull-wall flaming skull carriers: homing movement, damage on hit, timed despawn. */
public final class PossessableFlamingSkullService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ConcurrentHashMap<UUID, SessionSkulls> SESSIONS = new ConcurrentHashMap<>();
    private static final float TICK_DT_SEC = 0.05f;

    private PossessableFlamingSkullService() {}

    public static void spawn(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Vector3d origin,
        @Nullable Ref<EntityStore> preferredHuman,
        @Nonnull WraithBustersPluginConfig config
    ) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            LOGGER.atWarning().log("NPCPlugin unavailable; cannot spawn possessable flaming skull");
            return;
        }

        SessionSkulls skulls = SESSIONS.computeIfAbsent(session.getSessionId(), ignored -> new SessionSkulls());
        long maxDurationMs = config.getSkullMaxDurationSeconds() * 1000L;
        var pair = npc.spawnNPC(
            store,
            WraithBustersConstants.POSSESSABLE_FLAMING_SKULL_NPC_ROLE,
            null,
            origin,
            Rotation3f.ZERO
        );
        if (pair == null) {
            LOGGER.atWarning().log("Failed to spawn possessable flaming skull at %s", origin);
            return;
        }
        WraithBustersSoundUtil.play3dAtPosition(
            world,
            origin.x,
            origin.y,
            origin.z,
            WraithBustersConstants.SKULL_SPAWN_SOUND_EVENT
        );
        SkullSpawn spawn = new SkullSpawn();
        spawn.entityRef = pair.first();
        spawn.targetRef = preferredHuman;
        spawn.despawnAtMs = System.currentTimeMillis() + maxDurationMs;
        skulls.spawns.add(spawn);
    }

    public static void tick(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull WraithBustersPluginConfig config
    ) {
        SessionSkulls skulls = SESSIONS.get(session.getSessionId());
        if (skulls == null || skulls.spawns.isEmpty()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }

        long now = System.currentTimeMillis();
        float speed = config.getSkullSpeed();
        double hitRadiusSq = config.getSkullHitRadius() * config.getSkullHitRadius();
        List<SkullSpawn> toRemove = new ArrayList<>();

        for (SkullSpawn spawn : skulls.spawns) {
            Ref<EntityStore> ref = spawn.entityRef;
            if (ref == null || !ref.isValid()) {
                toRemove.add(spawn);
                continue;
            }
            if (now >= spawn.despawnAtMs) {
                despawnSkull(store, ref);
                toRemove.add(spawn);
                continue;
            }

            Ref<EntityStore> target = resolveTarget(session, store, world, spawn.targetRef, ref);
            spawn.targetRef = target;
            if (target == null) {
                continue;
            }

            TransformComponent skullTransform = store.getComponent(ref, TransformComponent.getComponentType());
            TransformComponent targetTransform = store.getComponent(target, TransformComponent.getComponentType());
            if (skullTransform == null || targetTransform == null) {
                continue;
            }

            Vector3d skullPos = new Vector3d(skullTransform.getPosition());
            Vector3d targetPos = new Vector3d(targetTransform.getPosition());
            targetPos.y += 0.9;
            Vector3d delta = targetPos.sub(skullPos, new Vector3d());
            double distSq = delta.lengthSquared();
            if (distSq <= hitRadiusSq) {
                applyHit(session, world, store, target, skullPos, config);
                despawnSkull(store, ref);
                toRemove.add(spawn);
                continue;
            }

            if (distSq > 0.0001) {
                delta.normalize();
            } else {
                delta.set(0, 0, 1);
            }
            skullPos.x += delta.x * speed * TICK_DT_SEC;
            skullPos.y += delta.y * speed * TICK_DT_SEC;
            skullPos.z += delta.z * speed * TICK_DT_SEC;
            skullTransform.setPosition(skullPos);
        }

        for (SkullSpawn spawn : toRemove) {
            skulls.spawns.remove(spawn);
        }
        if (skulls.spawns.isEmpty()) {
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
        @Nonnull Ref<EntityStore> skullRef
    ) {
        if (isAliveHuman(session, store, preferred)) {
            return preferred;
        }
        return findNearestHuman(session, world, skullRef);
    }

    private static void applyHit(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> humanRef,
        @Nonnull Vector3d hitPos,
        @Nonnull WraithBustersPluginConfig config
    ) {
        if (!isAliveHuman(session, store, humanRef)) {
            return;
        }
        DamageCause cause = resolveEnvironmentCause();
        if (cause != null) {
            DamageSystems.executeDamage(humanRef, store, new Damage(Damage.NULL_SOURCE, cause, config.getSkullDamage()));
        }
        ParticleUtil.spawnParticleEffect(
            WraithBustersConstants.SKULL_HIT_PARTICLE,
            hitPos,
            0.0f,
            0.0f,
            0.0f,
            WraithBustersConstants.SKULL_HIT_PARTICLE_SCALE,
            WraithBustersConstants.SKULL_HIT_PARTICLE_DURATION_SEC,
            store
        );
        WraithBustersSoundUtil.play3dAtPosition(
            world,
            hitPos.x,
            hitPos.y,
            hitPos.z,
            WraithBustersConstants.SKULL_HIT_SOUND_EVENT
        );
    }

    private static void despawnSkull(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> entityRef) {
        if (entityRef.isValid()) {
            store.removeEntity(entityRef, RemoveReason.REMOVE);
        }
    }

    private static void clearSession(@Nonnull UUID sessionId, @Nonnull World world) {
        SessionSkulls skulls = SESSIONS.remove(sessionId);
        if (skulls == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        for (SkullSpawn spawn : new ArrayList<>(skulls.spawns)) {
            Ref<EntityStore> ref = spawn.entityRef;
            if (ref != null && ref.isValid()) {
                despawnSkull(store, ref);
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

    private static final class SessionSkulls {
        private final List<SkullSpawn> spawns = new ArrayList<>();
    }

    private static final class SkullSpawn {
        @Nullable
        private Ref<EntityStore> entityRef;
        @Nullable
        private Ref<EntityStore> targetRef;
        private long despawnAtMs;
    }
}
