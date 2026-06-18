package com.hexvane.wraithbusters.ghost;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.arena.ArenaLayout;
import com.hexvane.wraithbusters.arena.GhostPhaseDoorMarker;
import com.hexvane.wraithbusters.arena.PhaseDoorSize;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hexvane.wraithbusters.debug.GhostTestService;
import com.hexvane.wraithbusters.setup.PhaseDoorAnalyzer;
import com.hexvane.wraithbusters.setup.SetupModeService;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

public final class PhasePortalMarkerService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ConcurrentHashMap<UUID, MarkerSet> SETUP_MARKERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, MarkerSet> SESSION_MARKERS = new ConcurrentHashMap<>();

    private PhasePortalMarkerService() {}

    /** Creative and setup builders see all portal sides. */
    public static boolean canPlayerSeeAllPhasePortals(
        @Nullable GameSession session,
        @Nonnull UUID playerUuid,
        @Nonnull Player player
    ) {
        if (player.getGameMode() == GameMode.Creative) {
            return true;
        }
        return SetupModeService.isActive(playerUuid);
    }

    /** Active-round ghosts (and test ghosts) see all portal sides. */
    public static boolean canPlayerSeePhasePortals(
        @Nullable GameSession session,
        @Nonnull UUID playerUuid
    ) {
        if (GhostTestService.isActive(playerUuid)) {
            return true;
        }
        if (session == null || session.getPhase() != GamePhase.ACTIVE) {
            return false;
        }
        PlayerSessionState state = session.getPlayers().get(playerUuid);
        return state != null && state.getRole() == PlayerRole.GHOST;
    }

    @Nonnull
    public static Set<Ref<EntityStore>> activePortalRefs() {
        Set<Ref<EntityStore>> refs = new ReferenceOpenHashSet<>();
        for (MarkerSet markers : SETUP_MARKERS.values()) {
            refs.addAll(markers.activeRefs);
        }
        for (MarkerSet markers : SESSION_MARKERS.values()) {
            refs.addAll(markers.activeRefs);
        }
        return refs;
    }

    public static void refreshSetup(
        @Nonnull UUID playerUuid,
        @Nonnull World world,
        @Nonnull ArenaLayout layout
    ) {
        defer(world, () -> refreshNow(playerUuid, SETUP_MARKERS, world, layout));
    }

    public static void clearSetup(@Nonnull UUID playerUuid, @Nonnull World world) {
        defer(world, () -> clearNow(playerUuid, SETUP_MARKERS, world));
    }

    /** Removes all setup preview portals (e.g. before a round starts). */
    public static void clearSetupForAll(@Nonnull World world) {
        defer(world, () -> {
            for (UUID key : SETUP_MARKERS.keySet().toArray(UUID[]::new)) {
                clearNow(key, SETUP_MARKERS, world);
            }
        });
    }

    public static void startRound(@Nonnull GameSession session, @Nonnull World world) {
        ArenaLayout layout = session.getArenaLayout();
        UUID sessionId = session.getSessionId();
        defer(world, () -> refreshNow(sessionId, SESSION_MARKERS, world, layout));
    }

    public static void endRound(@Nonnull GameSession session, @Nonnull World world) {
        defer(world, () -> clearNow(session.getSessionId(), SESSION_MARKERS, world));
    }

    public static void clearForLobby(@Nonnull GameSession session, @Nonnull World world) {
        defer(world, () -> clearNow(session.getSessionId(), SESSION_MARKERS, world));
    }

    /** Drops session portal markers without deferring; safe when the world is being removed. */
    public static void shutdownSession(@Nonnull GameSession session, @Nonnull World world) {
        UUID sessionId = session.getSessionId();
        MarkerSet markers = SESSION_MARKERS.remove(sessionId);
        if (markers == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        for (PortalSpawn spawn : markers.spawns) {
            despawn(store, markers, spawn);
        }
    }

    private static void defer(@Nonnull World world, @Nonnull Runnable action) {
        DeferredWorldTasks.run(world, action);
    }

    private static void refreshNow(
        @Nonnull UUID key,
        @Nonnull ConcurrentHashMap<UUID, MarkerSet> registry,
        @Nonnull World world,
        @Nonnull ArenaLayout layout
    ) {
        clearNow(key, registry, world);
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            registry.remove(key);
            return;
        }
        MarkerSet markers = new MarkerSet();
        registry.put(key, markers);
        for (GhostPhaseDoorMarker door : layout.getGhostPhaseDoors()) {
            PhaseDoorAnalyzer.refreshMarkerFromWorld(world, door);
            spawnAt(markers, store, door.getEntry(), door.getDoorSize(), door.getId());
            spawnAt(markers, store, door.getExit(), door.getDoorSize(), door.getId());
        }
    }

    private static void clearNow(
        @Nonnull UUID key,
        @Nonnull ConcurrentHashMap<UUID, MarkerSet> registry,
        @Nonnull World world
    ) {
        MarkerSet markers = registry.remove(key);
        if (markers == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            registry.remove(key);
            return;
        }
        for (PortalSpawn spawn : markers.spawns) {
            despawn(store, markers, spawn);
        }
    }

    @Nonnull
    private static String portalRoleFor(@Nonnull PhaseDoorSize size) {
        return switch (size) {
            case STANDARD_1x2 -> WraithBustersConstants.PHASE_PORTAL_NPC_ROLE_1x2;
            case STANDARD_2x2 -> WraithBustersConstants.PHASE_PORTAL_NPC_ROLE_2x2;
            case MEDIUM_3x3 -> WraithBustersConstants.PHASE_PORTAL_NPC_ROLE_3x3;
            case LARGE_4x4 -> WraithBustersConstants.PHASE_PORTAL_NPC_ROLE_4x4;
        };
    }

    private static void spawnAt(
        @Nonnull MarkerSet markers,
        @Nonnull Store<EntityStore> store,
        @Nonnull Transform transform,
        @Nonnull PhaseDoorSize doorSize,
        @Nonnull String doorId
    ) {
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            LOGGER.atWarning().log("NPCPlugin unavailable; cannot spawn phase portal marker");
            return;
        }
        Vector3d pos = transform.getPosition();
        Vector3d spawnPos = new Vector3d(pos.x, pos.y, pos.z);
        LOGGER.atInfo().log("Spawning phase portal marker for %s at %s", doorId, spawnPos);
        Rotation3f rotation = new Rotation3f(transform.getRotation());
        var pair = npc.spawnNPC(
            store,
            portalRoleFor(doorSize),
            null,
            spawnPos,
            rotation
        );
        if (pair == null) {
            LOGGER.atWarning().log("Failed to spawn phase portal marker at %s", spawnPos);
            return;
        }
        PortalSpawn spawn = new PortalSpawn();
        spawn.entityRef = pair.first();
        markers.spawns.add(spawn);
        markers.activeRefs.add(spawn.entityRef);
    }

    private static void despawn(
        @Nonnull Store<EntityStore> store,
        @Nonnull MarkerSet markers,
        @Nonnull PortalSpawn spawn
    ) {
        if (spawn.entityRef != null && spawn.entityRef.isValid()) {
            store.removeEntity(spawn.entityRef, RemoveReason.REMOVE);
        }
        if (spawn.entityRef != null) {
            markers.activeRefs.remove(spawn.entityRef);
        }
        spawn.entityRef = null;
    }

    private static final class MarkerSet {
        private final List<PortalSpawn> spawns = new ArrayList<>();
        private final Set<Ref<EntityStore>> activeRefs = new ReferenceOpenHashSet<>();
    }

    private static final class PortalSpawn {
        @Nullable
        private Ref<EntityStore> entityRef;
    }
}
