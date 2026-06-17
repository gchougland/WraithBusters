package com.hexvane.wraithbusters.pickup;

import com.hexvane.wraithbusters.debug.GhostTestService;
import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hexvane.wraithbusters.ui.GhostManaHudSupport;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

public final class ManaPickupService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double PICKUP_RADIUS_SQ = 0.85 * 0.85;
    private static final ConcurrentHashMap<UUID, SessionOrbs> SESSIONS = new ConcurrentHashMap<>();

    private ManaPickupService() {}

    public static boolean canPlayerSeeManaOrbs(
        @Nonnull GameSession session,
        @Nonnull UUID playerUuid,
        @Nonnull Player player
    ) {
        if (player.getGameMode() == GameMode.Creative) {
            return true;
        }
        if (GhostTestService.isActive(playerUuid)) {
            return true;
        }
        PlayerSessionState state = session.getPlayers().get(playerUuid);
        return state != null && state.getRole() == PlayerRole.GHOST;
    }

    @Nonnull
    public static Set<Ref<EntityStore>> activeOrbRefs(@Nonnull UUID sessionId) {
        SessionOrbs orbs = SESSIONS.get(sessionId);
        if (orbs == null) {
            return Collections.emptySet();
        }
        return orbs.activeRefs;
    }

    public static void startRound(@Nonnull GameSession session, @Nonnull World world) {
        clearSession(session.getSessionId(), world);
        SessionOrbs orbs = new SessionOrbs();
        for (Vector3i blockPos : session.getArenaLayout().getManaPickups()) {
            orbs.spawns.add(new OrbSpawn(blockPos));
        }
        SESSIONS.put(session.getSessionId(), orbs);
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        for (OrbSpawn spawn : orbs.spawns) {
            spawnEntity(store, orbs, spawn);
        }
    }

    public static void endRound(@Nonnull GameSession session, @Nonnull World world) {
        clearSession(session.getSessionId(), world);
    }

    public static void clearForLobby(@Nonnull GameSession session, @Nonnull World world) {
        clearSession(session.getSessionId(), world);
    }

    public static void tick(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull WraithBustersPluginConfig config
    ) {
        if (session.getPhase() != GamePhase.ACTIVE && !GhostTestService.hasTestMarkers(session)) {
            return;
        }
        SessionOrbs orbs = SESSIONS.get(session.getSessionId());
        if (orbs == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (OrbSpawn spawn : orbs.spawns) {
            if (spawn.entityRef == null && spawn.respawnAtMs > 0L && now >= spawn.respawnAtMs) {
                spawn.respawnAtMs = 0L;
                spawnEntity(store, orbs, spawn);
            }
        }
        for (UUID playerUuid : session.playerUuidList()) {
            PlayerSessionState state = session.getPlayers().get(playerUuid);
            if (state == null || (state.getRole() != PlayerRole.GHOST && !GhostTestService.isActive(playerUuid))) {
                continue;
            }
            Ref<EntityStore> playerRef = playerRef(world, playerUuid);
            if (playerRef == null) {
                continue;
            }
            TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
            if (transform == null) {
                continue;
            }
            Vector3d playerPos = transform.getPosition();
            for (OrbSpawn spawn : orbs.spawns) {
                if (spawn.entityRef == null) {
                    continue;
                }
                Vector3d center = spawn.worldCenter();
                double dx = playerPos.x - center.x;
                double dy = playerPos.y - center.y;
                double dz = playerPos.z - center.z;
                if (dx * dx + dy * dy + dz * dz <= PICKUP_RADIUS_SQ) {
                    collect(session, orbs, spawn, playerRef, store, config);
                    break;
                }
            }
        }
    }

    private static void collect(
        @Nonnull GameSession session,
        @Nonnull SessionOrbs orbs,
        @Nonnull OrbSpawn spawn,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull WraithBustersPluginConfig config
    ) {
        PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        PlayerSessionState state = session.getOrCreatePlayer(player.getUuid());
        if (state.getRole() != PlayerRole.GHOST && !GhostTestService.isActive(player.getUuid())) {
            return;
        }
        despawnEntity(store, orbs, spawn);
        int max = config.getGhostMaxMana();
        state.setGhostMana(Math.min(max, state.getGhostMana() + config.getManaPickupAmount()));
        player.sendMessage(Message.translation("server.wraithbusters.mana.collected"));
        Player playerComponent = store.getComponent(playerRef, Player.getComponentType());
        if (playerComponent != null) {
            GhostManaHudSupport.refresh(playerComponent, player, state, config);
        }
        spawn.respawnAtMs = System.currentTimeMillis() + config.getManaPickupRespawnSeconds() * 1000L;
    }

    private static void spawnEntity(
        @Nonnull Store<EntityStore> store,
        @Nonnull SessionOrbs orbs,
        @Nonnull OrbSpawn spawn
    ) {
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            LOGGER.atWarning().log("NPCPlugin unavailable; cannot spawn mana orb");
            return;
        }
        var pair = npc.spawnNPC(
            store,
            WraithBustersConstants.MANA_ORB_NPC_ROLE,
            null,
            spawn.worldCenter(),
            Rotation3f.ZERO
        );
        if (pair == null) {
            LOGGER.atWarning().log("Failed to spawn mana orb at %s", spawn.blockPos);
            return;
        }
        spawn.entityRef = pair.first();
        orbs.activeRefs.add(spawn.entityRef);
    }

    private static void despawnEntity(
        @Nonnull Store<EntityStore> store,
        @Nonnull SessionOrbs orbs,
        @Nonnull OrbSpawn spawn
    ) {
        if (spawn.entityRef != null && spawn.entityRef.isValid()) {
            store.removeEntity(spawn.entityRef, RemoveReason.REMOVE);
        }
        if (spawn.entityRef != null) {
            orbs.activeRefs.remove(spawn.entityRef);
        }
        spawn.entityRef = null;
    }

    private static void clearSession(@Nonnull UUID sessionId, @Nonnull World world) {
        SessionOrbs orbs = SESSIONS.remove(sessionId);
        if (orbs == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        for (OrbSpawn spawn : orbs.spawns) {
            despawnEntity(store, orbs, spawn);
        }
    }

    @Nullable
    private static Ref<EntityStore> playerRef(@Nonnull World world, @Nonnull UUID playerUuid) {
        PlayerRef player = Universe.get().getPlayer(playerUuid);
        if (player == null) {
            return null;
        }
        return player.getReference();
    }

    private static final class SessionOrbs {
        private final List<OrbSpawn> spawns = new ArrayList<>();
        private final Set<Ref<EntityStore>> activeRefs = new ReferenceOpenHashSet<>();
    }

    private static final class OrbSpawn {
        @Nonnull
        private final Vector3i blockPos;
        @Nullable
        private Ref<EntityStore> entityRef;
        private long respawnAtMs;

        private OrbSpawn(@Nonnull Vector3i blockPos) {
            this.blockPos = new Vector3i(blockPos);
        }

        @Nonnull
        private Vector3d worldCenter() {
            return new Vector3d(blockPos.x + 0.5, blockPos.y + 0.75, blockPos.z + 0.5);
        }
    }
}
