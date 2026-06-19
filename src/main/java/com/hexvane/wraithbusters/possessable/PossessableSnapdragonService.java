package com.hexvane.wraithbusters.possessable;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.WraithBustersPlugin;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hexvane.wraithbusters.puzzle.CheeseChaseService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
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
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Tracks bush-summoned snapdragons: human targeting, ghost/mouse ignore, timed smoke-poof despawn. */
public final class PossessableSnapdragonService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ConcurrentHashMap<UUID, SessionSnapdragons> SESSIONS = new ConcurrentHashMap<>();

    private PossessableSnapdragonService() {}

    public static void spawn(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Vector3d spawnPos,
        @Nullable Ref<EntityStore> preferredHuman
    ) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            LOGGER.atWarning().log("NPCPlugin unavailable; cannot spawn possessable snapdragon");
            return;
        }
        var pair = npc.spawnNPC(
            store,
            WraithBustersConstants.POSSESSABLE_SNAPDRAGON_NPC_ROLE,
            null,
            spawnPos,
            Rotation3f.ZERO
        );
        if (pair == null) {
            LOGGER.atWarning().log("Failed to spawn possessable snapdragon at %s", spawnPos);
            return;
        }
        Ref<EntityStore> entityRef = pair.first();
        NPCEntity npcEntity = store.getComponent(entityRef, NPCEntity.getComponentType());
        if (npcEntity == null) {
            return;
        }
        WraithBustersPluginConfig config = WraithBustersPlugin.get().getPluginConfig();
        double ignoreDuration = config.getBushSnapdragonDurationSeconds() + 2.0;
        configureSnapdragon(session, store, entityRef, npcEntity, preferredHuman, ignoreDuration);
        SessionSnapdragons snapdragons = SESSIONS.computeIfAbsent(session.getSessionId(), ignored -> new SessionSnapdragons());
        SnapdragonSpawn spawn = new SnapdragonSpawn();
        spawn.entityRef = entityRef;
        spawn.despawnAtMs = System.currentTimeMillis() + config.getBushSnapdragonDurationSeconds() * 1000L;
        snapdragons.spawns.add(spawn);
        snapdragons.activeRefs.add(entityRef);
    }

    public static void tick(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull WraithBustersPluginConfig config
    ) {
        SessionSnapdragons snapdragons = SESSIONS.get(session.getSessionId());
        if (snapdragons == null || snapdragons.spawns.isEmpty()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        long now = System.currentTimeMillis();
        double ignoreDuration = config.getBushSnapdragonDurationSeconds() + 2.0;
        List<SnapdragonSpawn> toRemove = new ArrayList<>();
        for (SnapdragonSpawn spawn : snapdragons.spawns) {
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
            retargetIfNeeded(session, world, store, npcEntity, ref);
        }
        for (SnapdragonSpawn spawn : toRemove) {
            snapdragons.spawns.remove(spawn);
            if (spawn.entityRef != null) {
                snapdragons.activeRefs.remove(spawn.entityRef);
            }
        }
        if (snapdragons.spawns.isEmpty()) {
            SESSIONS.remove(session.getSessionId());
        }
    }

    public static void endRound(@Nonnull GameSession session, @Nonnull World world) {
        clearSession(session.getSessionId(), world, true);
    }

    public static void clearForLobby(@Nonnull GameSession session, @Nonnull World world) {
        clearSession(session.getSessionId(), world, true);
    }

    private static void configureSnapdragon(
        @Nonnull GameSession session,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull NPCEntity npcEntity,
        @Nullable Ref<EntityStore> preferredHuman,
        double ignoreDuration
    ) {
        Role role = npcEntity.getRole();
        Ref<EntityStore> target = isAliveHuman(session, store, preferredHuman)
            ? preferredHuman
            : findNearestHuman(session, worldFromStore(store), entityRef);
        if (target != null) {
            role.setMarkedTarget(MarkedEntitySupport.DEFAULT_TARGET_SLOT, target);
            role.getStateSupport().setState(entityRef, "Combat", null, store);
        }
        applyIgnoreAttitudes(session, store, role, ignoreDuration);
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

    private static void retargetIfNeeded(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull NPCEntity npcEntity,
        @Nonnull Ref<EntityStore> snapdragonRef
    ) {
        Role role = npcEntity.getRole();
        Ref<EntityStore> current = role.getMarkedEntitySupport().getMarkedEntityRef(MarkedEntitySupport.DEFAULT_TARGET_SLOT);
        if (isAliveHuman(session, store, current)) {
            return;
        }
        Ref<EntityStore> replacement = findNearestHuman(session, world, snapdragonRef);
        if (replacement != null) {
            role.setMarkedTarget(MarkedEntitySupport.DEFAULT_TARGET_SLOT, replacement);
            role.getStateSupport().setState(snapdragonRef, "Combat", null, store);
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
                WraithBustersConstants.POSSESSABLE_SNAPDRAGON_POOF_PARTICLE,
                pos,
                0.0f,
                0.0f,
                0.0f,
                1.0f,
                WraithBustersConstants.POSSESSABLE_SNAPDRAGON_POOF_DURATION_SEC,
                store
            );
        }
        store.removeEntity(entityRef, RemoveReason.REMOVE);
    }

    private static void clearSession(@Nonnull UUID sessionId, @Nonnull World world, boolean withPoof) {
        SessionSnapdragons snapdragons = SESSIONS.remove(sessionId);
        if (snapdragons == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        for (SnapdragonSpawn spawn : new ArrayList<>(snapdragons.spawns)) {
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

    @Nonnull
    private static World worldFromStore(@Nonnull Store<EntityStore> store) {
        return store.getExternalData().getWorld();
    }

    private static final class SessionSnapdragons {
        private final List<SnapdragonSpawn> spawns = new ArrayList<>();
        private final Set<Ref<EntityStore>> activeRefs = new ReferenceOpenHashSet<>();
    }

    private static final class SnapdragonSpawn {
        @Nullable
        private Ref<EntityStore> entityRef;
        private long despawnAtMs;
    }
}
