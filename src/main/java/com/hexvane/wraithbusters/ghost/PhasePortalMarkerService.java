package com.hexvane.wraithbusters.ghost;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.arena.ArenaLayout;
import com.hexvane.wraithbusters.arena.GhostPhaseDoorMarker;
import com.hexvane.wraithbusters.arena.PhaseDoorSize;
import com.hexvane.wraithbusters.arena.RoomDefinition;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameRegistry;
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
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

public final class PhasePortalMarkerService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ConcurrentHashMap<UUID, MarkerSet> SETUP_MARKERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, MarkerSet> SESSION_MARKERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, PortalBinding> PORTAL_BINDINGS = new ConcurrentHashMap<>();

    public record PortalBinding(@Nonnull String doorId, boolean entrySide) {}

    private PhasePortalMarkerService() {}

    @Nullable
    public static PortalBinding findBinding(
        @Nonnull Ref<EntityStore> portalRef,
        @Nonnull Store<EntityStore> store
    ) {
        UUIDComponent uuidComponent = store.getComponent(portalRef, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return null;
        }
        return PORTAL_BINDINGS.get(uuidComponent.getUuid());
    }

    public static boolean isTrackedPortal(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull Store<EntityStore> store
    ) {
        return findBinding(entityRef, store) != null;
    }

    public static boolean hasTrackedPortals(@Nullable GameSession session) {
        if (session != null && session.getPhase() == GamePhase.ACTIVE) {
            MarkerSet markers = SESSION_MARKERS.get(session.getSessionId());
            return markers != null && !markers.activeRefs.isEmpty();
        }
        if (!SETUP_MARKERS.isEmpty() || !SESSION_MARKERS.isEmpty()) {
            return true;
        }
        return !PORTAL_BINDINGS.isEmpty();
    }

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
        return activePortalRefs(null);
    }

    @Nonnull
    public static Set<Ref<EntityStore>> activePortalRefs(@Nullable GameSession session) {
        Set<Ref<EntityStore>> refs = new ReferenceOpenHashSet<>();
        if (session != null && session.getPhase() == GamePhase.ACTIVE) {
            MarkerSet sessionMarkers = SESSION_MARKERS.get(session.getSessionId());
            if (sessionMarkers != null) {
                refs.addAll(sessionMarkers.activeRefs);
            }
            return refs;
        }
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
        if (shouldSuppressSetupPreviews(world)) {
            return;
        }
        defer(world, () -> refreshNow(playerUuid, SETUP_MARKERS, world, layout, true));
    }

    public static void clearSetup(@Nonnull UUID playerUuid, @Nonnull World world) {
        defer(world, () -> clearNow(playerUuid, SETUP_MARKERS, world));
    }

    /** Removes all setup preview portals (e.g. before a round starts). */
    public static void clearSetupForAll(@Nonnull World world) {
        defer(world, () -> clearAllSetupNow(world));
    }

    public static void startRound(@Nonnull GameSession session, @Nonnull World world) {
        prepareForRound(session, world);
    }

    /**
     * Clears setup previews and session markers, refreshes door transforms from live door blocks,
     * then spawns portals in one deferred pass.
     */
    public static void prepareForRound(@Nonnull GameSession session, @Nonnull World world) {
        ArenaLayout layout = session.getArenaLayout();
        UUID sessionId = session.getSessionId();
        defer(world, () -> {
            purgeAllPhasePortalNpcs(world);
            refreshNow(sessionId, SESSION_MARKERS, world, layout, false);
        });
    }

    /** Clears and respawns session markers in one deferred pass (avoids stale state between rounds). */
    public static void restartRoundMarkers(@Nonnull GameSession session, @Nonnull World world) {
        prepareForRound(session, world);
    }

    public static void endRound(@Nonnull GameSession session, @Nonnull World world) {
        defer(world, () -> clearNow(session.getSessionId(), SESSION_MARKERS, world));
    }

    public static void clearForLobby(@Nonnull GameSession session, @Nonnull World world) {
        defer(world, () -> clearNow(session.getSessionId(), SESSION_MARKERS, world));
    }

    /**
     * Despawns every tracked phase portal in this world, then respawns from {@code layout}
     * (door transforms refreshed from live door blocks). Caller should load {@code layout} from
     * arena JSON and apply it to the session / setup layouts first.
     */
    public static void resetFromArenaLayout(
        @Nonnull World world,
        @Nonnull ArenaLayout layout,
        @Nullable GameSession session
    ) {
        resetFromArenaLayout(world, layout, session, true);
    }

    public static void resetFromArenaLayout(
        @Nonnull World world,
        @Nonnull ArenaLayout layout,
        @Nullable GameSession session,
        boolean forceSessionRespawn
    ) {
        defer(world, () -> resetFromArenaLayoutNow(world, layout, session, forceSessionRespawn));
    }

    /** Removes phase portal markers that overlap a room door once it has been opened. */
    public static void onRoomDoorOpened(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull RoomDefinition room
    ) {
        defer(world, () -> despawnForRoomDoorNow(session, world, room));
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

    /** Live session or ghost-test portals already occupy this world — skip setup preview NPCs. */
    private static boolean shouldSuppressSetupPreviews(@Nonnull World world) {
        GameSession session = GameRegistry.get().getSessionForWorld(world.getWorldConfig().getUuid());
        if (session == null) {
            return false;
        }
        if (session.getPhase() == GamePhase.ACTIVE) {
            return true;
        }
        return GhostTestService.hasTestMarkers(session);
    }

    private static void resetFromArenaLayoutNow(
        @Nonnull World world,
        @Nonnull ArenaLayout layout,
        @Nullable GameSession session,
        boolean forceSessionRespawn
    ) {
        purgeAllPhasePortalNpcs(world);
        if (session != null) {
            session.clearDisabledPhaseDoors();
        }

        boolean spawnSession = session != null
            && (forceSessionRespawn
                || session.getPhase() == GamePhase.ACTIVE
                || GhostTestService.hasTestMarkers(session));
        if (spawnSession) {
            refreshNow(session.getSessionId(), SESSION_MARKERS, world, layout, false);
        }

        if (!shouldSuppressSetupPreviews(world)) {
            String arenaId = layout.getArenaId();
            for (UUID playerUuid : SetupModeService.activePlayerUuids()) {
                SetupModeService.SetupSession setupSession = SetupModeService.get(playerUuid);
                if (setupSession == null) {
                    continue;
                }
                if (!arenaId.equals(setupSession.getLayout().getArenaId())) {
                    continue;
                }
                refreshNow(playerUuid, SETUP_MARKERS, world, setupSession.getLayout(), true);
            }
        }
    }

    /**
     * Removes every phase-portal NPC in the world (including moved or untracked copies) and drops
     * marker registries so the next refresh spawns a clean pair per door marker.
     */
    private static void purgeAllPhasePortalNpcs(@Nonnull World world) {
        SETUP_MARKERS.clear();
        SESSION_MARKERS.clear();
        if (!DeferredWorldTasks.isStoreOpen(world)) {
            PORTAL_BINDINGS.clear();
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        Query<EntityStore> query = Query.and(NPCEntity.getComponentType());
        List<Ref<EntityStore>> toRemove = new ArrayList<>();
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                if (npc == null || !isPhasePortalRole(npc.getRoleName())) {
                    continue;
                }
                Ref<EntityStore> entityRef = chunk.getReferenceTo(i);
                if (entityRef != null && entityRef.isValid()) {
                    toRemove.add(entityRef);
                }
            }
        });
        for (Ref<EntityStore> entityRef : toRemove) {
            clearPortalBinding(store, entityRef);
            store.removeEntity(entityRef, RemoveReason.REMOVE);
        }
        PORTAL_BINDINGS.clear();
    }

    private static boolean isPhasePortalRole(@Nullable String roleName) {
        return roleName != null && roleName.startsWith("WraithBusters_Phase_Portal");
    }

    private static void clearAllSetupNow(@Nonnull World world) {
        for (UUID key : SETUP_MARKERS.keySet().toArray(UUID[]::new)) {
            clearNow(key, SETUP_MARKERS, world);
        }
    }

    private static void refreshNow(
        @Nonnull UUID key,
        @Nonnull ConcurrentHashMap<UUID, MarkerSet> registry,
        @Nonnull World world,
        @Nonnull ArenaLayout layout,
        boolean setupPreview
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
            if (setupPreview || registry == SESSION_MARKERS) {
                PhaseDoorAnalyzer.refreshMarkerFromWorld(world, door);
            }
            spawnAt(markers, store, door.getEntry(), door.getDoorSize(), door.getId(), true);
            spawnAt(markers, store, door.getExit(), door.getDoorSize(), door.getId(), false);
        }
    }

    private static void despawnForRoomDoorNow(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull RoomDefinition room
    ) {
        MarkerSet markers = SESSION_MARKERS.get(session.getSessionId());
        if (markers == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        for (GhostPhaseDoorMarker door : session.getArenaLayout().getGhostPhaseDoors()) {
            if (!sharesAnyBlock(door.getDoorBlocks(), room.getDoorBlocks())) {
                continue;
            }
            session.disablePhaseDoor(door.getId());
            markers.spawns.removeIf(spawn -> {
                if (!door.getId().equals(spawn.doorId)) {
                    return false;
                }
                despawn(store, markers, spawn);
                return true;
            });
        }
    }

    private static boolean sharesAnyBlock(
        @Nonnull List<Vector3i> existing,
        @Nonnull List<Vector3i> incoming
    ) {
        if (existing.isEmpty()) {
            return false;
        }
        for (Vector3i block : existing) {
            for (Vector3i candidate : incoming) {
                if (block.x == candidate.x && block.y == candidate.y && block.z == candidate.z) {
                    return true;
                }
            }
        }
        return false;
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
        @Nonnull String doorId,
        boolean entrySide
    ) {
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            LOGGER.atWarning().log("NPCPlugin unavailable; cannot spawn phase portal marker");
            return;
        }
        Vector3d pos = transform.getPosition();
        Vector3d spawnPos = new Vector3d(pos.x, pos.y, pos.z);
        Rotation3f rotation = new Rotation3f(transform.getRotation());
        var pair = npc.spawnNPC(
            store,
            portalRoleFor(doorSize),
            null,
            spawnPos,
            rotation
        );
        if (pair == null) {
            LOGGER.atWarning().log("Failed to spawn phase portal marker for %s at %s", doorId, spawnPos);
            return;
        }
        Ref<EntityStore> entityRef = pair.first();
        applyPortalRotation(store, entityRef, rotation);
        bindPortal(store, entityRef, doorId, entrySide);

        PortalSpawn spawn = new PortalSpawn();
        spawn.doorId = doorId;
        spawn.entrySide = entrySide;
        spawn.entityRef = entityRef;
        markers.spawns.add(spawn);
        markers.activeRefs.add(spawn.entityRef);
    }

    private static void applyPortalRotation(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull Rotation3f rotation
    ) {
        TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
        if (transform != null) {
            transform.setRotation(new Rotation3f(rotation));
        }
        HeadRotation headRotation = store.ensureAndGetComponent(entityRef, HeadRotation.getComponentType());
        headRotation.setRotation(rotation);
    }

    private static void bindPortal(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull String doorId,
        boolean entrySide
    ) {
        UUIDComponent uuidComponent = store.getComponent(entityRef, UUIDComponent.getComponentType());
        if (uuidComponent != null) {
            PORTAL_BINDINGS.put(uuidComponent.getUuid(), new PortalBinding(doorId, entrySide));
        }
    }

    private static void clearPortalBinding(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> entityRef) {
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }
        UUIDComponent uuidComponent = store.getComponent(entityRef, UUIDComponent.getComponentType());
        if (uuidComponent != null) {
            PORTAL_BINDINGS.remove(uuidComponent.getUuid());
        }
    }

    private static void despawn(
        @Nonnull Store<EntityStore> store,
        @Nonnull MarkerSet markers,
        @Nonnull PortalSpawn spawn
    ) {
        if (spawn.entityRef != null && spawn.entityRef.isValid()) {
            clearPortalBinding(store, spawn.entityRef);
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
        @Nonnull
        private String doorId = "";
        private boolean entrySide;
        @Nullable
        private Ref<EntityStore> entityRef;
    }
}
