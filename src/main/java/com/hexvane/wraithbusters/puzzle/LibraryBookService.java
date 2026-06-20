package com.hexvane.wraithbusters.puzzle;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.arena.BookSpawnMarker;
import com.hexvane.wraithbusters.arena.BookshelfMarker;
import com.hexvane.wraithbusters.arena.RoomDefinition;
import com.hexvane.wraithbusters.door.RoomProgressionService;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hexvane.wraithbusters.util.BlockPlacementUtil;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hexvane.wraithbusters.util.WraithBustersSoundUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class LibraryBookService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int BOOKS_PER_ROUND = BookColor.values().length;
    private static final ConcurrentHashMap<UUID, SessionLibrary> SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> COMPLETED = new ConcurrentHashMap<>();

    private LibraryBookService() {}

    public static void resetForSession(@Nonnull GameSession session) {
        String prefix = session.getSessionId().toString();
        COMPLETED.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public static void startRound(@Nonnull GameSession session, @Nonnull World world) {
        resetForSession(session);
        clearSession(session, world, true);
        DeferredWorldTasks.run(world, () -> {
            resetAllBookshelvesToMissing(session, world);
            refreshForCurrentRoom(session, world);
        });
    }

    public static void endRound(@Nonnull GameSession session, @Nonnull World world) {
        clearSession(session, world, true);
    }

    public static void onCurrentRoomChanged(@Nonnull GameSession session, @Nonnull World world) {
        DeferredWorldTasks.run(world, () -> refreshForCurrentRoom(session, world));
    }

    /** Marks a library room solved and clears puzzle books without advancing progression. */
    public static void markRoomComplete(
        @Nonnull GameSession session,
        @Nonnull RoomDefinition room,
        @Nonnull World world
    ) {
        if (isCompleted(session, room)) {
            return;
        }
        COMPLETED.put(puzzleKey(session, room), Boolean.TRUE);
        SessionLibrary library = SESSIONS.get(session.getSessionId());
        if (library != null) {
            cleanupSession(session, world, library, false);
        }
        stripBooksFromHumans(session, world);
    }

    @Nullable
    public static BookshelfMarker findBookshelf(@Nonnull GameSession session, @Nonnull Vector3i blockPos) {
        for (BookshelfMarker marker : session.getArenaLayout().getBookshelves()) {
            Vector3i pos = marker.getBlockPos();
            if (pos.x == blockPos.x && pos.y == blockPos.y && pos.z == blockPos.z) {
                return marker;
            }
        }
        return null;
    }

    /** Updates puzzle spawn tracking when a book pickup is harvested or broken. */
    public static void onPickupHarvested(@Nonnull World world, @Nonnull Vector3i blockPos) {
        GameSession session = GameRegistry.get().getSessionForWorld(world.getWorldConfig().getUuid());
        if (session == null) {
            return;
        }
        SessionLibrary library = SESSIONS.get(session.getSessionId());
        if (library == null || !library.active) {
            return;
        }
        if (!library.spawnAssignments.containsKey(blockPos) || library.collectedSpawns.contains(blockPos)) {
            return;
        }
        library.collectedSpawns.add(new Vector3i(blockPos));
        library.activePickupBlocks.remove(blockPos);
    }

    public static boolean tryInsertBook(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3i targetBlock,
        @Nullable ItemStack heldItem
    ) {
        if (session.getPhase() != GamePhase.ACTIVE) {
            return false;
        }
        PlayerSessionState state = playerState(session, playerRef, store);
        if (state == null || state.getRole() != PlayerRole.HUMAN || !state.isAlive()) {
            return false;
        }
        RoomDefinition currentRoom = RoomProgressionService.currentRoom(session);
        if (!isLibraryBooksRoom(currentRoom)) {
            return false;
        }
        if (isCompleted(session, currentRoom)) {
            return false;
        }
        BookshelfMarker shelf = findBookshelf(session, targetBlock);
        if (shelf == null || !WraithBustersConstants.LIBRARY_BOOKS_PUZZLE_ID.equals(shelf.getPuzzleId())) {
            return false;
        }
        SessionLibrary library = SESSIONS.get(session.getSessionId());
        if (library == null || !library.active) {
            send(store, playerRef, "server.wraithbusters.puzzle.libraryBooks.notReady");
            return false;
        }
        Vector3i shelfPos = shelf.getBlockPos();
        if (library.filledShelves.contains(shelfPos)) {
            send(store, playerRef, "server.wraithbusters.puzzle.libraryBooks.alreadyFilled");
            WraithBustersSoundUtil.play2d(playerRef, store, WraithBustersConstants.PUZZLE_FAIL_SOUND_EVENT);
            return false;
        }
        if (heldItem == null || ItemStack.isEmpty(heldItem) || !heldItem.isValid()) {
            send(store, playerRef, "server.wraithbusters.puzzle.libraryBooks.needBook");
            return false;
        }
        BookColor heldColor = BookColor.fromItemId(heldItem.getItemId());
        if (heldColor != shelf.getColor()) {
            send(store, playerRef, "server.wraithbusters.puzzle.libraryBooks.wrongBook");
            WraithBustersSoundUtil.play2d(playerRef, store, WraithBustersConstants.PUZZLE_FAIL_SOUND_EVENT);
            return false;
        }

        CombinedItemContainer inventory = InventoryComponent.getCombined(
            store,
            playerRef,
            InventoryComponent.EVERYTHING
        );
        inventory.removeItemStack(new ItemStack(heldColor.getItemId(), 1), true, true);
        library.filledShelves.add(new Vector3i(shelfPos));
        BlockPlacementUtil.setBlockState(world, shelfPos, WraithBustersConstants.BOOKSHELF_COMPLETE_STATE);
        send(store, playerRef, "server.wraithbusters.puzzle.libraryBooks.inserted");
        WraithBustersSoundUtil.play2d(playerRef, store, WraithBustersConstants.OFFERING_INSERT_SOUND_EVENT);

        List<BookshelfMarker> shelves = bookshelvesForPuzzle(session, WraithBustersConstants.LIBRARY_BOOKS_PUZZLE_ID);
        if (library.filledShelves.size() >= shelves.size() && !shelves.isEmpty()) {
            completePuzzle(session, world, playerRef, store, currentRoom);
        }
        return true;
    }

    private static void resetAllBookshelvesToMissing(@Nonnull GameSession session, @Nonnull World world) {
        for (BookshelfMarker shelf : session.getArenaLayout().getBookshelves()) {
            BlockPlacementUtil.setBlockState(
                world,
                shelf.getBlockPos(),
                WraithBustersConstants.BOOKSHELF_MISSING_STATE
            );
        }
    }

    private static void refreshForCurrentRoom(@Nonnull GameSession session, @Nonnull World world) {
        if (session.getPhase() != GamePhase.ACTIVE) {
            return;
        }
        RoomDefinition currentRoom = RoomProgressionService.currentRoom(session);
        SessionLibrary library = SESSIONS.get(session.getSessionId());
        if (library != null && library.active) {
            if (!isLibraryBooksRoom(currentRoom)) {
                cleanupSession(session, world, library, true);
            }
        }
        if (isLibraryBooksRoom(currentRoom) && !isCompleted(session, currentRoom)) {
            activatePuzzle(session, world);
        }
    }

    public static boolean isLibraryBooksRoom(@Nonnull RoomDefinition room) {
        return WraithBustersConstants.LIBRARY_BOOKS_PUZZLE_ID.equals(room.getPuzzleId())
            || WraithBustersConstants.LIBRARY_ROOM_ID.equals(room.getRoomId());
    }

    private static void activatePuzzle(@Nonnull GameSession session, @Nonnull World world) {
        SessionLibrary library = SESSIONS.computeIfAbsent(session.getSessionId(), ignored -> new SessionLibrary());
        if (library.active) {
            return;
        }

        String puzzleId = WraithBustersConstants.LIBRARY_BOOKS_PUZZLE_ID;
        List<BookSpawnMarker> spawns = bookSpawnsForPuzzle(session, puzzleId);
        List<BookshelfMarker> shelves = bookshelvesForPuzzle(session, puzzleId);
        if (spawns.size() < BOOKS_PER_ROUND || shelves.size() < BOOKS_PER_ROUND) {
            LOGGER.atWarning().log(
                "Library puzzle needs at least %s book spawns and bookshelves (spawns=%s shelves=%s)",
                BOOKS_PER_ROUND,
                spawns.size(),
                shelves.size()
            );
            return;
        }

        List<BookColor> colors = new ArrayList<>(List.of(BookColor.values()));
        Collections.shuffle(colors);
        Collections.shuffle(spawns);
        library.spawnAssignments.clear();
        library.collectedSpawns.clear();
        library.filledShelves.clear();
        library.activePickupBlocks.clear();

        int placed = 0;
        for (int i = 0; i < BOOKS_PER_ROUND; i++) {
            Vector3i spawnPos = new Vector3i(spawns.get(i).getBlockPos());
            BookColor color = colors.get(i);
            library.spawnAssignments.put(spawnPos, color);
            if (BlockPlacementUtil.placeBlock(world, spawnPos, color.getPickupBlockId())) {
                placed++;
            }
            library.activePickupBlocks.add(spawnPos);
        }
        if (placed < BOOKS_PER_ROUND) {
            LOGGER.atWarning().log(
                "Library puzzle placed %s/%s book pickups (chunks may be unloaded near spawn markers)",
                placed,
                BOOKS_PER_ROUND
            );
        } else {
            LOGGER.atInfo().log("Library puzzle activated with %s book pickups", BOOKS_PER_ROUND);
        }

        for (BookshelfMarker shelf : shelves) {
            Vector3i pos = shelf.getBlockPos();
            BlockPlacementUtil.setBlockState(world, pos, WraithBustersConstants.BOOKSHELF_MISSING_STATE);
        }

        library.active = true;
    }

    private static void completePuzzle(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull RoomDefinition room
    ) {
        if (isCompleted(session, room)) {
            return;
        }
        COMPLETED.put(puzzleKey(session, room), Boolean.TRUE);
        SessionLibrary library = SESSIONS.get(session.getSessionId());
        if (library != null) {
            cleanupSession(session, world, library, false);
        }
        stripBooksFromHumans(session, world);
        RoomProgressionService.advanceAfterPuzzle(session);
        KeySpawnService.spawnKeyForRoom(session, world, room);
        send(store, playerRef, "server.wraithbusters.puzzle.libraryBooks.complete");
        WraithBustersSoundUtil.play2dForSessionHumans(
            session,
            world,
            WraithBustersConstants.PUZZLE_SUCCESS_SOUND_EVENT
        );
        onCurrentRoomChanged(session, world);
    }

    private static void cleanupSession(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull SessionLibrary library,
        boolean resetShelvesToMissing
    ) {
        for (Vector3i pickupPos : new ArrayList<>(library.activePickupBlocks)) {
            BlockPlacementUtil.removeBlock(world, pickupPos);
        }
        if (resetShelvesToMissing) {
            for (BookshelfMarker shelf : session.getArenaLayout().getBookshelves()) {
                if (!WraithBustersConstants.LIBRARY_BOOKS_PUZZLE_ID.equals(shelf.getPuzzleId())) {
                    continue;
                }
                BlockPlacementUtil.setBlockState(
                    world,
                    shelf.getBlockPos(),
                    WraithBustersConstants.BOOKSHELF_MISSING_STATE
                );
            }
        }
        library.spawnAssignments.clear();
        library.collectedSpawns.clear();
        library.filledShelves.clear();
        library.activePickupBlocks.clear();
        library.active = false;
    }

    private static void clearSession(
        @Nonnull GameSession session,
        @Nonnull World world,
        boolean resetShelvesToMissing
    ) {
        SessionLibrary library = SESSIONS.remove(session.getSessionId());
        if (library == null) {
            return;
        }
        cleanupSession(session, world, library, resetShelvesToMissing);
        stripBooksFromHumans(session, world);
    }

    private static void stripBooksFromHumans(@Nonnull GameSession session, @Nonnull World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        for (UUID playerUuid : session.playerUuidList()) {
            PlayerSessionState state = session.getOrCreatePlayer(playerUuid);
            if (state.getRole() != PlayerRole.HUMAN) {
                continue;
            }
            PlayerRef player = Universe.get().getPlayer(playerUuid);
            if (player == null) {
                continue;
            }
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            CombinedItemContainer inventory = InventoryComponent.getCombined(
                store,
                ref,
                InventoryComponent.EVERYTHING
            );
            for (BookColor color : BookColor.values()) {
                inventory.removeItemStack(new ItemStack(color.getItemId(), Integer.MAX_VALUE), true, true);
            }
        }
    }

    @Nullable
    private static PlayerSessionState playerState(
        @Nonnull GameSession session,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr == null) {
            return null;
        }
        return session.getOrCreatePlayer(pr.getUuid());
    }

    private static boolean isCompleted(@Nonnull GameSession session, @Nonnull RoomDefinition room) {
        return COMPLETED.containsKey(puzzleKey(session, room));
    }

    @Nonnull
    private static String puzzleKey(@Nonnull GameSession session, @Nonnull RoomDefinition room) {
        return session.getSessionId() + ":" + room.getRoomId() + ":" + room.getPuzzleId();
    }

    @Nonnull
    private static List<BookshelfMarker> bookshelvesForPuzzle(@Nonnull GameSession session, @Nonnull String puzzleId) {
        List<BookshelfMarker> shelves = new ArrayList<>();
        for (BookshelfMarker shelf : session.getArenaLayout().getBookshelves()) {
            if (puzzleId.equals(shelf.getPuzzleId())) {
                shelves.add(shelf);
            }
        }
        return shelves;
    }

    @Nonnull
    private static List<BookSpawnMarker> bookSpawnsForPuzzle(@Nonnull GameSession session, @Nonnull String puzzleId) {
        List<BookSpawnMarker> spawns = new ArrayList<>();
        for (BookSpawnMarker spawn : session.getArenaLayout().getBookSpawns()) {
            if (puzzleId.equals(spawn.getPuzzleId())) {
                spawns.add(spawn);
            }
        }
        return spawns;
    }

    private static void send(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull String key
    ) {
        PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player != null) {
            player.sendMessage(Message.translation(key));
        }
    }

    private static final class SessionLibrary {
        private boolean active;
        private final Map<Vector3i, BookColor> spawnAssignments = new HashMap<>();
        private final Set<Vector3i> collectedSpawns = new HashSet<>();
        private final Set<Vector3i> filledShelves = new HashSet<>();
        private final Set<Vector3i> activePickupBlocks = new HashSet<>();
    }
}
