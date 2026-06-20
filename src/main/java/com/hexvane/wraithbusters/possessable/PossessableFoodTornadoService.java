package com.hexvane.wraithbusters.possessable;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hexvane.wraithbusters.util.WraithBustersSoundUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Tracks barrel-summoned food tornadoes: NPC ranged combat, retarget humans, timed despawn. */
public final class PossessableFoodTornadoService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ConcurrentHashMap<UUID, SessionTornadoes> SESSIONS = new ConcurrentHashMap<>();

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

        configureFoodTornado(session, store, entityRef, npcEntity, preferredHuman);

        SessionTornadoes tornadoes = SESSIONS.computeIfAbsent(session.getSessionId(), ignored -> new SessionTornadoes());
        TornadoSpawn spawn = new TornadoSpawn();
        spawn.entityRef = entityRef;
        spawn.despawnAtMs = System.currentTimeMillis() + config.getBarrelFoodTornadoDurationSeconds() * 1000L;
        tornadoes.spawns.add(spawn);
        WraithBustersSoundUtil.play3dAtPosition(
            world,
            origin.x,
            origin.y,
            origin.z,
            WraithBustersConstants.BARREL_TORNADO_SPAWN_SOUND_EVENT
        );
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

            retargetIfNeeded(session, world, store, npcEntity, ref);
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

    private static void configureFoodTornado(
        @Nonnull GameSession session,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull NPCEntity npcEntity,
        @Nullable Ref<EntityStore> preferredHuman
    ) {
        Role role = npcEntity.getRole();
        Ref<EntityStore> target = isAliveHuman(session, store, preferredHuman)
            ? preferredHuman
            : findNearestHuman(session, worldFromStore(store), entityRef);
        if (target != null) {
            role.setMarkedTarget(MarkedEntitySupport.DEFAULT_TARGET_SLOT, target);
            role.getStateSupport().setState(entityRef, "Combat", null, store);
        }
    }

    private static void retargetIfNeeded(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull NPCEntity npcEntity,
        @Nonnull Ref<EntityStore> tornadoRef
    ) {
        Role role = npcEntity.getRole();
        Ref<EntityStore> current = role.getMarkedEntitySupport().getMarkedEntityRef(MarkedEntitySupport.DEFAULT_TARGET_SLOT);
        if (isAliveHuman(session, store, current)) {
            return;
        }
        Ref<EntityStore> replacement = findNearestHuman(session, world, tornadoRef);
        if (replacement != null) {
            role.setMarkedTarget(MarkedEntitySupport.DEFAULT_TARGET_SLOT, replacement);
            role.getStateSupport().setState(tornadoRef, "Combat", null, store);
        }
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

    @Nonnull
    private static World worldFromStore(@Nonnull Store<EntityStore> store) {
        return store.getExternalData().getWorld();
    }

    private static final class SessionTornadoes {
        private final List<TornadoSpawn> spawns = new ArrayList<>();
    }

    private static final class TornadoSpawn {
        @Nullable
        private Ref<EntityStore> entityRef;
        private long despawnAtMs;
    }
}
