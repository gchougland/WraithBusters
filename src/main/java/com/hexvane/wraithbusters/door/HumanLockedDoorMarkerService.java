package com.hexvane.wraithbusters.door;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.arena.RoomDefinition;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hexvane.wraithbusters.setup.PhaseDoorAnalyzer;
import com.hexvane.wraithbusters.team.TeamSetupService;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
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

/**
 * Invisible human-only interact markers on locked doors. Ghosts use phase portals instead;
 * removing block {@code Use} / {@code IsDoor} on the door blocks prevents the generic open prompt.
 */
public final class HumanLockedDoorMarkerService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ConcurrentHashMap<UUID, MarkerSet> SESSION_MARKERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> MARKER_BINDINGS = new ConcurrentHashMap<>();

    private HumanLockedDoorMarkerService() {}

    public static void prepareForRound(@Nonnull GameSession session, @Nonnull World world) {
        defer(world, () -> refreshNow(session, world));
    }

    public static void endRound(@Nonnull GameSession session, @Nonnull World world) {
        defer(world, () -> clearNow(session.getSessionId(), world));
    }

    public static void onRoomDoorOpened(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull RoomDefinition room
    ) {
        defer(world, () -> despawnForRoomNow(session, room, world));
    }

    public static boolean hasTrackedMarkers(@Nullable GameSession session) {
        if (session == null) {
            return false;
        }
        MarkerSet markers = SESSION_MARKERS.get(session.getSessionId());
        return markers != null && !markers.activeRefs.isEmpty();
    }

    public static boolean isTrackedMarker(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull Store<EntityStore> store
    ) {
        return findRoomId(entityRef, store) != null;
    }

    @Nullable
    public static String findRoomId(
        @Nonnull Ref<EntityStore> markerRef,
        @Nonnull Store<EntityStore> store
    ) {
        UUIDComponent uuidComponent = store.getComponent(markerRef, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return null;
        }
        return MARKER_BINDINGS.get(uuidComponent.getUuid());
    }

    /** Living humans during an active round (creative/setup builders also see markers). */
    public static boolean canPlayerSeeMarkers(
        @Nullable GameSession session,
        @Nonnull UUID playerUuid,
        @Nonnull Player player
    ) {
        if (TeamSetupService.canBypassTeamVisibility(playerUuid, player)) {
            return true;
        }
        if (session == null || session.getPhase() != GamePhase.ACTIVE) {
            return false;
        }
        PlayerSessionState state = session.getPlayers().get(playerUuid);
        return state != null && state.getRole() == PlayerRole.HUMAN && state.isAlive();
    }

    private static void refreshNow(@Nonnull GameSession session, @Nonnull World world) {
        clearNow(session.getSessionId(), world);
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        MarkerSet markers = new MarkerSet();
        SESSION_MARKERS.put(session.getSessionId(), markers);
        for (RoomDefinition room : session.getArenaLayout().getRooms()) {
            if (room.getDoorBlocks().isEmpty()) {
                continue;
            }
            spawnForRoom(markers, store, world, room);
        }
    }

    private static void despawnForRoomNow(
        @Nonnull GameSession session,
        @Nonnull RoomDefinition room,
        @Nonnull World world
    ) {
        MarkerSet markers = SESSION_MARKERS.get(session.getSessionId());
        if (markers == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        markers.spawns.removeIf(spawn -> {
            if (!room.getRoomId().equals(spawn.roomId)) {
                return false;
            }
            despawn(store, markers, spawn);
            return true;
        });
    }

    private static void clearNow(@Nonnull UUID sessionId, @Nonnull World world) {
        MarkerSet markers = SESSION_MARKERS.remove(sessionId);
        if (markers == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        for (DoorSpawn spawn : markers.spawns) {
            despawn(store, markers, spawn);
        }
    }

    private static void spawnForRoom(
        @Nonnull MarkerSet markers,
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull RoomDefinition room
    ) {
        Transform transform = doorTransform(world, room);
        if (transform == null) {
            LOGGER.atWarning().log("Could not place human locked-door marker for room %s", room.getRoomId());
            return;
        }
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            LOGGER.atWarning().log("NPCPlugin unavailable; cannot spawn human locked-door marker");
            return;
        }
        Vector3d pos = transform.getPosition();
        Rotation3f rotation = new Rotation3f(transform.getRotation());
        var pair = npc.spawnNPC(
            store,
            WraithBustersConstants.HUMAN_LOCKED_DOOR_NPC_ROLE,
            null,
            new Vector3d(pos.x, pos.y, pos.z),
            rotation
        );
        if (pair == null) {
            LOGGER.atWarning().log("Failed to spawn human locked-door marker for room %s", room.getRoomId());
            return;
        }
        Ref<EntityStore> entityRef = pair.first();
        applyRotation(store, entityRef, rotation);
        bindMarker(store, entityRef, room.getRoomId());

        DoorSpawn spawn = new DoorSpawn();
        spawn.roomId = room.getRoomId();
        spawn.entityRef = entityRef;
        markers.spawns.add(spawn);
        markers.activeRefs.add(entityRef);
    }

    @Nullable
    private static Transform doorTransform(@Nonnull World world, @Nonnull RoomDefinition room) {
        PhaseDoorAnalyzer.AnalysisResult analysis = PhaseDoorAnalyzer.analyzeFromDoorBlocks(world, room.getDoorBlocks());
        if (analysis != null) {
            Vector3d a = analysis.sideA().getPosition();
            Vector3d b = analysis.sideB().getPosition();
            Vector3d midpoint = new Vector3d((a.x + b.x) * 0.5, (a.y + b.y) * 0.5, (a.z + b.z) * 0.5);
            return new Transform(midpoint, analysis.sideA().getRotation());
        }
        double sumX = 0;
        double sumY = 0;
        double sumZ = 0;
        int count = 0;
        for (var block : room.getDoorBlocks()) {
            sumX += block.x + 0.5;
            sumY += block.y + 1.0;
            sumZ += block.z + 0.5;
            count++;
        }
        if (count == 0) {
            return null;
        }
        return new Transform(new Vector3d(sumX / count, sumY / count, sumZ / count), new Rotation3f());
    }

    private static void applyRotation(
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

    private static void bindMarker(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull String roomId
    ) {
        UUIDComponent uuidComponent = store.getComponent(entityRef, UUIDComponent.getComponentType());
        if (uuidComponent != null) {
            MARKER_BINDINGS.put(uuidComponent.getUuid(), roomId);
        }
    }

    private static void despawn(
        @Nonnull Store<EntityStore> store,
        @Nonnull MarkerSet markers,
        @Nonnull DoorSpawn spawn
    ) {
        if (spawn.entityRef != null && spawn.entityRef.isValid()) {
            clearBinding(store, spawn.entityRef);
            store.removeEntity(spawn.entityRef, RemoveReason.REMOVE);
        }
        if (spawn.entityRef != null) {
            markers.activeRefs.remove(spawn.entityRef);
        }
    }

    private static void clearBinding(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> entityRef) {
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }
        UUIDComponent uuidComponent = store.getComponent(entityRef, UUIDComponent.getComponentType());
        if (uuidComponent != null) {
            MARKER_BINDINGS.remove(uuidComponent.getUuid());
        }
    }

    private static void defer(@Nonnull World world, @Nonnull Runnable task) {
        DeferredWorldTasks.run(world, task);
    }

    private static final class MarkerSet {
        private final List<DoorSpawn> spawns = new ArrayList<>();
        private final Set<Ref<EntityStore>> activeRefs = new ReferenceOpenHashSet<>();
    }

    private static final class DoorSpawn {
        private String roomId;
        private Ref<EntityStore> entityRef;
    }
}
