package com.hexvane.wraithbusters.setup;

import com.hexvane.wraithbusters.WraithBustersMessages;
import com.hexvane.wraithbusters.WraithBustersPlugin;
import com.hexvane.wraithbusters.arena.ArenaLayout;
import com.hexvane.wraithbusters.arena.ArenaLayoutStore;
import com.hexvane.wraithbusters.arena.BookSpawnMarker;
import com.hexvane.wraithbusters.arena.BookshelfMarker;
import com.hexvane.wraithbusters.arena.CandleMarker;
import com.hexvane.wraithbusters.arena.GhostPhaseDoorMarker;
import com.hexvane.wraithbusters.arena.PossessableMarker;
import com.hexvane.wraithbusters.arena.RoomDefinition;
import com.hexvane.wraithbusters.ghost.PhasePortalMarkerService;
import com.hexvane.wraithbusters.puzzle.BookColor;
import com.hexvane.wraithbusters.util.StatueAnchorUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

public final class SetupModeService {
    private static final double REMOVE_RADIUS_SQ = 4.0 * 4.0;
    private static final ConcurrentHashMap<UUID, SetupSession> ACTIVE = new ConcurrentHashMap<>();

    private SetupModeService() {}

    public enum PhaseDoorPlaceResult {
        COMPLETED,
        NOT_LOCKED_DOOR,
        NOT_IN_SETUP
    }

    public static void enter(@Nonnull UUID playerUuid, @Nonnull ArenaLayout layout) {
        ACTIVE.put(playerUuid, new SetupSession(layout));
    }

    public static void exit(@Nonnull UUID playerUuid, @Nonnull World world) {
        ACTIVE.remove(playerUuid);
        PhasePortalMarkerService.clearSetup(playerUuid, world);
    }

    /** Ends setup for every player and clears all setup preview portals. */
    public static void exitAll(@Nonnull World world) {
        ACTIVE.clear();
        PhasePortalMarkerService.clearSetupForAll(world);
    }

    public static void exit(@Nonnull UUID playerUuid) {
        ACTIVE.remove(playerUuid);
    }

    public static boolean isActive(@Nonnull UUID playerUuid) {
        return ACTIVE.containsKey(playerUuid);
    }

    @Nonnull
    public static Iterable<UUID> activePlayerUuids() {
        return ACTIVE.keySet();
    }

    /** Replaces ghost phase door markers on every in-memory setup layout for the arena. */
    public static void syncPhaseDoorsForArena(
        @Nonnull String arenaId,
        @Nonnull List<GhostPhaseDoorMarker> doors
    ) {
        for (SetupSession session : ACTIVE.values()) {
            if (arenaId.equals(session.getLayout().getArenaId())) {
                session.getLayout().setGhostPhaseDoors(GhostPhaseDoorMarker.copyAll(doors));
            }
        }
    }

    @Nullable
    public static SetupSession get(@Nonnull UUID playerUuid) {
        return ACTIVE.get(playerUuid);
    }

    @Nonnull
    public static PhaseDoorPlaceResult placePhaseDoorOnLockedDoor(
        @Nonnull UUID playerUuid,
        @Nonnull Vector3i clickedBlock,
        @Nonnull World world
    ) {
        SetupSession session = ACTIVE.get(playerUuid);
        if (session == null) {
            return PhaseDoorPlaceResult.NOT_IN_SETUP;
        }
        PhaseDoorAnalyzer.AnalysisResult analysis = PhaseDoorAnalyzer.analyze(world, clickedBlock);
        if (analysis == null) {
            return PhaseDoorPlaceResult.NOT_LOCKED_DOOR;
        }
        ArenaLayout layout = session.getLayout();
        removeOverlapping(layout, analysis.doorBlocks());
        GhostPhaseDoorMarker door = new GhostPhaseDoorMarker();
        door.setId("door" + layout.getGhostPhaseDoors().size());
        PhaseDoorAnalyzer.applyToMarker(door, analysis);
        layout.getGhostPhaseDoors().add(door);
        PhasePortalMarkerService.refreshSetup(playerUuid, world, layout);
        return PhaseDoorPlaceResult.COMPLETED;
    }

    public static boolean removePhaseDoorAtBlock(
        @Nonnull UUID playerUuid,
        @Nonnull Vector3i clickedBlock,
        @Nonnull World world
    ) {
        SetupSession session = ACTIVE.get(playerUuid);
        if (session == null) {
            return false;
        }
        ArenaLayout layout = session.getLayout();
        GhostPhaseDoorMarker matched = findDoorAtBlock(layout, PhaseDoorAnalyzer.resolveSeedBlock(world, clickedBlock));
        if (matched == null) {
            matched = findDoorAtBlock(layout, clickedBlock);
        }
        if (matched != null) {
            layout.getGhostPhaseDoors().remove(matched);
            PhasePortalMarkerService.refreshSetup(playerUuid, world, layout);
            return true;
        }
        return removeNearestPhaseDoor(playerUuid, new Vector3d(clickedBlock.x + 0.5, clickedBlock.y, clickedBlock.z + 0.5), world);
    }

    public static boolean removeNearestPhaseDoor(
        @Nonnull UUID playerUuid,
        @Nonnull Vector3d near,
        @Nonnull World world
    ) {
        SetupSession session = ACTIVE.get(playerUuid);
        if (session == null) {
            return false;
        }
        ArenaLayout layout = session.getLayout();
        GhostPhaseDoorMarker nearest = null;
        double nearestDistSq = REMOVE_RADIUS_SQ;
        for (GhostPhaseDoorMarker door : layout.getGhostPhaseDoors()) {
            double distSq = Math.min(distanceSq(near, door.getEntry().getPosition()), distanceSq(near, door.getExit().getPosition()));
            if (distSq <= nearestDistSq) {
                nearestDistSq = distSq;
                nearest = door;
            }
        }
        if (nearest == null) {
            return false;
        }
        layout.getGhostPhaseDoors().remove(nearest);
        PhasePortalMarkerService.refreshSetup(playerUuid, world, layout);
        return true;
    }

    public static void markAtPlayer(
        @Nonnull WraithBustersPlugin plugin,
        @Nonnull PlayerRef playerRef,
        @Nonnull TransformComponent transform,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull String markerType,
        @Nullable String extra
    ) throws IOException {
        SetupSession session = ACTIVE.get(playerRef.getUuid());
        if (session == null) {
            playerRef.sendMessage(WraithBustersMessages.translation("setup.notInMode"));
            return;
        }
        if ("phasedoor".equalsIgnoreCase(markerType)) {
            playerRef.sendMessage(WraithBustersMessages.translation("setup.phaseDoor.legacyDeprecated"));
            return;
        }
        if ("room".equalsIgnoreCase(markerType)) {
            markRoomAtLook(playerRef, session, world, ref, store, extra);
            return;
        }
        if ("possessable".equalsIgnoreCase(markerType)) {
            markPossessableAtLook(playerRef, session, world, ref, store, extra);
            return;
        }
        if ("candle".equalsIgnoreCase(markerType)) {
            markCandleAtLook(playerRef, session, ref, store, extra);
            return;
        }
        if ("exorcism".equalsIgnoreCase(markerType)) {
            markExorcismAtLook(playerRef, session, ref, store);
            return;
        }
        if ("bookshelf".equalsIgnoreCase(markerType)) {
            markBookshelfAtLook(playerRef, session, ref, store, extra);
            return;
        }
        if ("book_spawn".equalsIgnoreCase(markerType)) {
            markBookSpawnAtLook(playerRef, session, ref, store, extra);
            return;
        }
        ArenaLayout layout = session.getLayout();
        Vector3i block = blockUnderFeet(transform);
        Transform feet = new Transform(transform.getTransform());
        switch (markerType.toLowerCase()) {
            case "lobbyspawn" -> layout.setLobbySpawn(feet);
            case "humanspawn" -> layout.getHumanSpawns().add(feet);
            case "ghostspawn" -> layout.getGhostSpawns().add(feet);
            case "manapickup" -> layout.getManaPickups().add(block);
            case "small_mouse" -> layout.getCheeseChaseSmallMice().add(feet);
            case "large_mouse" -> layout.setCheeseChaseChumbo(feet);
            default -> {
                playerRef.sendMessage(WraithBustersMessages.translation("setup.unknownType"));
                return;
            }
        }
        playerRef.sendMessage(
            WraithBustersMessages.translation("setup.marked")
                .param("type", markerType)
                .param("x", String.valueOf(block.x))
                .param("y", String.valueOf(block.y))
                .param("z", String.valueOf(block.z))
        );
    }

    @Nonnull
    private static Vector3i blockUnderFeet(@Nonnull TransformComponent transform) {
        Vector3d pos = transform.getPosition();
        return new Vector3i(
            (int) Math.floor(pos.x),
            (int) Math.floor(pos.y),
            (int) Math.floor(pos.z)
        );
    }

    private static void markCandleAtLook(
        @Nonnull PlayerRef playerRef,
        @Nonnull SetupSession session,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable String extra
    ) {
        Vector3i block = SetupTargetUtil.resolveLookedAtBlock(ref, store);
        if (block == null) {
            playerRef.sendMessage(WraithBustersMessages.translation("setup.noTarget"));
            return;
        }
        ArenaLayout layout = session.getLayout();
        CandleMarker candle = new CandleMarker();
        candle.setPuzzleId(extra == null || extra.isBlank() ? "candles" : extra);
        candle.setIndex(layout.getCandles().size());
        candle.setBlockPos(block);
        layout.getCandles().add(candle);
        playerRef.sendMessage(
            WraithBustersMessages.translation("setup.marked")
                .param("type", "candle")
                .param("x", String.valueOf(block.x))
                .param("y", String.valueOf(block.y))
                .param("z", String.valueOf(block.z))
        );
    }

    private static void markExorcismAtLook(
        @Nonnull PlayerRef playerRef,
        @Nonnull SetupSession session,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        Vector3i block = SetupTargetUtil.resolveLookedAtBlock(ref, store);
        if (block == null) {
            playerRef.sendMessage(WraithBustersMessages.translation("setup.noTarget"));
            return;
        }
        session.getLayout().setExorcismTable(block);
        playerRef.sendMessage(
            WraithBustersMessages.translation("setup.marked")
                .param("type", "exorcism")
                .param("x", String.valueOf(block.x))
                .param("y", String.valueOf(block.y))
                .param("z", String.valueOf(block.z))
        );
    }

    private static void markBookshelfAtLook(
        @Nonnull PlayerRef playerRef,
        @Nonnull SetupSession session,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable String extra
    ) {
        Vector3i block = SetupTargetUtil.resolveLookedAtBlock(ref, store);
        if (block == null) {
            playerRef.sendMessage(WraithBustersMessages.translation("setup.noTarget"));
            return;
        }
        BookColor color = BookColor.fromSetupExtra(extra);
        if (color == null) {
            playerRef.sendMessage(WraithBustersMessages.translation("setup.unknownType"));
            return;
        }
        BookshelfMarker marker = new BookshelfMarker();
        marker.setColor(color);
        marker.setBlockPos(block);
        session.getLayout().getBookshelves().add(marker);
        playerRef.sendMessage(
            WraithBustersMessages.translation("setup.marked")
                .param("type", "bookshelf")
                .param("x", String.valueOf(block.x))
                .param("y", String.valueOf(block.y))
                .param("z", String.valueOf(block.z))
        );
    }

    private static void markBookSpawnAtLook(
        @Nonnull PlayerRef playerRef,
        @Nonnull SetupSession session,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable String extra
    ) {
        Vector3i block = SetupTargetUtil.resolveLookedAtBlock(ref, store);
        if (block == null) {
            playerRef.sendMessage(WraithBustersMessages.translation("setup.noTarget"));
            return;
        }
        BookSpawnMarker marker = new BookSpawnMarker();
        if (extra != null && !extra.isBlank() && BookColor.fromSetupExtra(extra) == null) {
            marker.setPuzzleId(extra);
        }
        marker.setBlockPos(block);
        session.getLayout().getBookSpawns().add(marker);
        playerRef.sendMessage(
            WraithBustersMessages.translation("setup.marked")
                .param("type", "book_spawn")
                .param("x", String.valueOf(block.x))
                .param("y", String.valueOf(block.y))
                .param("z", String.valueOf(block.z))
        );
    }

    private static void markPossessableAtLook(
        @Nonnull PlayerRef playerRef,
        @Nonnull SetupSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable String extra
    ) {
        Vector3i block = SetupTargetUtil.resolveLookedAtBlock(ref, store);
        if (block == null) {
            playerRef.sendMessage(WraithBustersMessages.translation("setup.noTarget"));
            return;
        }
        String typeId = extra == null || extra.isBlank() ? "plate" : extra;
        if ("statue".equalsIgnoreCase(typeId)) {
            block = StatueAnchorUtil.resolveStatueAnchor(world, block);
        }
        ArenaLayout layout = session.getLayout();
        PossessableMarker marker = new PossessableMarker();
        marker.setBlockPos(block);
        marker.setTypeId(typeId);
        layout.getPossessables().add(marker);
        playerRef.sendMessage(
            WraithBustersMessages.translation("setup.marked")
                .param("type", "possessable")
                .param("x", String.valueOf(block.x))
                .param("y", String.valueOf(block.y))
                .param("z", String.valueOf(block.z))
        );
    }

    private static void markRoomAtLook(
        @Nonnull PlayerRef playerRef,
        @Nonnull SetupSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable String extra
    ) {
        Vector3i targetBlock = SetupTargetUtil.resolveLookedAtLockedDoor(world, ref, store);
        if (targetBlock == null) {
            playerRef.sendMessage(WraithBustersMessages.translation("setup.room.noTarget"));
            return;
        }
        List<Vector3i> doorBlocks = PhaseDoorAnalyzer.collectLockedDoorAssembly(world, targetBlock);
        if (doorBlocks == null || doorBlocks.isEmpty()) {
            playerRef.sendMessage(WraithBustersMessages.translation("setup.room.notLockedDoor"));
            return;
        }
        doorBlocks = new ArrayList<>(doorBlocks);
        doorBlocks.removeIf(block -> !PhaseDoorAnalyzer.isLockedDoorAt(world, block));
        if (doorBlocks.isEmpty()) {
            playerRef.sendMessage(WraithBustersMessages.translation("setup.room.notLockedDoor"));
            return;
        }
        sortDoorBlocks(doorBlocks);
        Vector3i keyBlock = doorBlocks.getFirst();
        ArenaLayout layout = session.getLayout();
        String roomId = extra == null || extra.isBlank() ? "room" + layout.getRooms().size() : extra;
        RoomDefinition existing = findRoom(layout, roomId);
        if (existing != null) {
            existing.setDoorBlocks(doorBlocks);
        } else {
            RoomDefinition room = new RoomDefinition();
            room.setRoomId(roomId);
            room.setDoorBlocks(doorBlocks);
            room.setKeySpawn(new Vector3i(keyBlock));
            room.setSymbolId("circle");
            room.setPuzzleId("candles");
            layout.getRooms().add(room);
        }
        playerRef.sendMessage(
            WraithBustersMessages.translation("setup.room.marked")
                .param("roomId", roomId)
                .param("count", String.valueOf(doorBlocks.size()))
                .param("x", String.valueOf(keyBlock.x))
                .param("y", String.valueOf(keyBlock.y))
                .param("z", String.valueOf(keyBlock.z))
        );
    }

    private static void sortDoorBlocks(@Nonnull List<Vector3i> doorBlocks) {
        doorBlocks.sort(
            Comparator.comparingInt((Vector3i block) -> block.y)
                .thenComparingInt(block -> block.x)
                .thenComparingInt(block -> block.z)
        );
    }

    public static void save(@Nonnull WraithBustersPlugin plugin, @Nonnull UUID playerUuid, @Nonnull String arenaId) throws IOException {
        SetupSession session = ACTIVE.get(playerUuid);
        if (session == null) {
            return;
        }
        session.getLayout().setArenaId(arenaId);
        ArenaLayoutStore.save(plugin, session.getLayout());
    }

    private static void removeOverlapping(@Nonnull ArenaLayout layout, @Nonnull java.util.List<Vector3i> doorBlocks) {
        layout.getGhostPhaseDoors().removeIf(door -> sharesAnyBlock(door.getDoorBlocks(), doorBlocks));
    }

    private static boolean sharesAnyBlock(
        @Nonnull java.util.List<Vector3i> existing,
        @Nonnull java.util.List<Vector3i> incoming
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

    @Nullable
    private static GhostPhaseDoorMarker findDoorAtBlock(@Nonnull ArenaLayout layout, @Nullable Vector3i clickedBlock) {
        if (clickedBlock == null) {
            return null;
        }
        for (GhostPhaseDoorMarker door : layout.getGhostPhaseDoors()) {
            for (Vector3i block : door.getDoorBlocks()) {
                if (block.x == clickedBlock.x && block.y == clickedBlock.y && block.z == clickedBlock.z) {
                    return door;
                }
            }
        }
        return null;
    }

    private static double distanceSq(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Nullable
    private static RoomDefinition findRoom(@Nonnull ArenaLayout layout, @Nonnull String roomId) {
        for (RoomDefinition room : layout.getRooms()) {
            if (room.getRoomId().equals(roomId)) {
                return room;
            }
        }
        return null;
    }

    public static final class SetupSession {
        @Nonnull
        private final ArenaLayout layout;

        public SetupSession(@Nonnull ArenaLayout layout) {
            this.layout = layout;
        }

        @Nonnull
        public ArenaLayout getLayout() {
            return layout;
        }
    }
}
