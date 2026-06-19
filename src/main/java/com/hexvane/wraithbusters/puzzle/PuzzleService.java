package com.hexvane.wraithbusters.puzzle;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.arena.CandleMarker;
import com.hexvane.wraithbusters.arena.RoomDefinition;
import com.hexvane.wraithbusters.door.RoomProgressionService;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hexvane.wraithbusters.util.WraithBustersSoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class PuzzleService {
    private static final String CANDLE_OFF_STATE = "Off";
    private static final String CANDLE_ON_STATE = "On";
    private static final String CANDLE_ON_STATE_LEGACY = "default";
    private static final ConcurrentHashMap<String, Boolean> COMPLETED_PUZZLES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, List<Integer>> CANDLE_ON_SEQUENCE = new ConcurrentHashMap<>();

    private PuzzleService() {}

    public static void resetForSession(@Nonnull GameSession session) {
        String prefix = session.getSessionId().toString();
        COMPLETED_PUZZLES.keySet().removeIf(key -> key.startsWith(prefix));
        CANDLE_ON_SEQUENCE.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public static void resetCandlesForRound(@Nonnull GameSession session, @Nonnull World world) {
        resetForSession(session);
        DeferredWorldTasks.run(world, () -> resetCandlesNow(session, world));
    }

    @Nullable
    public static CandleMarker findCandle(@Nonnull GameSession session, @Nonnull Vector3i blockPos) {
        for (CandleMarker candle : session.getArenaLayout().getCandles()) {
            Vector3i pos = candle.getBlockPos();
            if (pos.x == blockPos.x && pos.y == blockPos.y && pos.z == blockPos.z) {
                return candle;
            }
        }
        return null;
    }

    public static void onCandleToggled(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CandleMarker candle
    ) {
        if (session.getPhase() != GamePhase.ACTIVE) {
            return;
        }
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        var playerState = session.getOrCreatePlayer(pr.getUuid());
        if (playerState.getRole() != PlayerRole.HUMAN || !playerState.isAlive()) {
            return;
        }
        var currentRoom = RoomProgressionService.currentRoom(session);
        if (!currentRoom.getPuzzleId().equals(candle.getPuzzleId())) {
            return;
        }
        String puzzleKey = puzzleKey(session, currentRoom, candle.getPuzzleId());
        if (COMPLETED_PUZZLES.containsKey(puzzleKey)) {
            return;
        }
        DeferredWorldTasks.run(world, () -> evaluateCandlePuzzle(session, world, playerRef, store, candle, puzzleKey));
    }

    /** Runs puzzle evaluation on the world thread (call from interactions). */
    public static void evaluateCandleNow(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CandleMarker candle
    ) {
        RoomDefinition currentRoom = RoomProgressionService.currentRoom(session);
        if (!currentRoom.getPuzzleId().equals(candle.getPuzzleId())) {
            return;
        }
        String puzzleKey = puzzleKey(session, currentRoom, candle.getPuzzleId());
        if (COMPLETED_PUZZLES.containsKey(puzzleKey)) {
            return;
        }
        evaluateCandlePuzzle(session, world, playerRef, store, candle, puzzleKey);
    }

    public static boolean toggleCandle(@Nonnull World world, @Nonnull Vector3i blockPos) {
        BlockType blockType = world.getBlockType(blockPos.x, blockPos.y, blockPos.z);
        if (blockType == null) {
            return false;
        }
        if (isCandleLit(world, blockPos)) {
            if (blockType.getBlockForState(CANDLE_OFF_STATE) != null) {
                world.setBlockInteractionState(blockPos, blockType, CANDLE_OFF_STATE);
                WraithBustersSoundUtil.playBlockStateSound(world, blockPos, blockType, CANDLE_OFF_STATE);
            }
            return false;
        }
        String onState = litStateFor(blockType);
        if (onState != null) {
            world.setBlockInteractionState(blockPos, blockType, onState);
            WraithBustersSoundUtil.playBlockStateSound(world, blockPos, blockType, onState);
            return true;
        }
        return false;
    }

    @Nullable
    private static String litStateFor(@Nonnull BlockType blockType) {
        if (blockType.getBlockForState(CANDLE_ON_STATE) != null) {
            return CANDLE_ON_STATE;
        }
        if (blockType.getBlockForState(CANDLE_ON_STATE_LEGACY) != null) {
            return CANDLE_ON_STATE_LEGACY;
        }
        return null;
    }

    private static void evaluateCandlePuzzle(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CandleMarker candle,
        @Nonnull String puzzleKey
    ) {
        if (COMPLETED_PUZZLES.containsKey(puzzleKey)) {
            return;
        }
        RoomDefinition currentRoom = RoomProgressionService.currentRoom(session);
        String puzzleId = candle.getPuzzleId();
        List<CandleMarker> candles = candlesForPuzzle(session, puzzleId);
        if (candles.isEmpty()) {
            return;
        }

        boolean nowLit = toggleCandle(world, candle.getBlockPos());
        List<Integer> onSequence = CANDLE_ON_SEQUENCE.computeIfAbsent(puzzleKey, ignored -> new ArrayList<>());
        recordToggle(onSequence, candle.getIndex(), nowLit);

        if (!allCandlesLit(session, world, puzzleId)) {
            return;
        }

        if (matchesExpectedOnSequence(candles, onSequence)) {
            COMPLETED_PUZZLES.put(puzzleKey, Boolean.TRUE);
            CANDLE_ON_SEQUENCE.remove(puzzleKey);
            PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
            if (pr != null) {
                pr.sendMessage(Message.translation("server.wraithbusters.puzzle.candle.complete"));
                WraithBustersSoundUtil.play2d(playerRef, store, WraithBustersConstants.PUZZLE_SUCCESS_SOUND_EVENT);
            }
            KeySpawnService.spawnKeyForRoom(session, world, currentRoom);
            RoomProgressionService.advanceAfterPuzzle(session);
            CheeseChaseService.onCurrentRoomChanged(session, world);
            LibraryBookService.onCurrentRoomChanged(session, world);
            return;
        }

        resetPuzzleCandles(session, world, puzzleId);
        CANDLE_ON_SEQUENCE.remove(puzzleKey);
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.translation("server.wraithbusters.puzzle.candle.wrongOrder"));
            WraithBustersSoundUtil.play2d(playerRef, store, WraithBustersConstants.PUZZLE_FAIL_SOUND_EVENT);
        }
    }

    private static void recordToggle(@Nonnull List<Integer> onSequence, int candleIndex, boolean nowLit) {
        if (nowLit) {
            onSequence.add(candleIndex);
            return;
        }
        for (int i = onSequence.size() - 1; i >= 0; i--) {
            if (onSequence.get(i) == candleIndex) {
                onSequence.remove(i);
                break;
            }
        }
    }

    private static boolean matchesExpectedOnSequence(
        @Nonnull List<CandleMarker> candles,
        @Nonnull List<Integer> onSequence
    ) {
        if (onSequence.size() != candles.size()) {
            return false;
        }
        for (int i = 0; i < candles.size(); i++) {
            if (onSequence.get(i) != candles.get(i).getIndex()) {
                return false;
            }
        }
        return true;
    }

    private static boolean allCandlesLit(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull String puzzleId
    ) {
        for (CandleMarker candle : candlesForPuzzle(session, puzzleId)) {
            if (!isCandleLit(world, candle.getBlockPos())) {
                return false;
            }
        }
        return !candlesForPuzzle(session, puzzleId).isEmpty();
    }

    private static void resetPuzzleCandles(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull String puzzleId
    ) {
        for (CandleMarker candle : candlesForPuzzle(session, puzzleId)) {
            Vector3i pos = candle.getBlockPos();
            BlockType blockType = world.getBlockType(pos.x, pos.y, pos.z);
            if (blockType != null && blockType.getBlockForState(CANDLE_OFF_STATE) != null) {
                world.setBlockInteractionState(pos, blockType, CANDLE_OFF_STATE);
            }
        }
    }

    private static void resetCandlesNow(@Nonnull GameSession session, @Nonnull World world) {
        for (CandleMarker candle : session.getArenaLayout().getCandles()) {
            Vector3i pos = candle.getBlockPos();
            BlockType blockType = world.getBlockType(pos.x, pos.y, pos.z);
            if (blockType == null) {
                continue;
            }
            if (blockType.getBlockForState(CANDLE_OFF_STATE) != null) {
                world.setBlockInteractionState(pos, blockType, CANDLE_OFF_STATE);
            }
        }
    }

    public static boolean isCandleLit(@Nonnull World world, @Nonnull Vector3i blockPos) {
        BlockType blockType = world.getBlockType(blockPos.x, blockPos.y, blockPos.z);
        if (blockType == null) {
            return false;
        }
        String state = BlockAccessor.getCurrentInteractionState(blockType);
        if (CANDLE_OFF_STATE.equals(state)) {
            return false;
        }
        return CANDLE_ON_STATE.equals(state) || CANDLE_ON_STATE_LEGACY.equals(state);
    }

    @Nonnull
    private static String puzzleKey(
        @Nonnull GameSession session,
        @Nonnull RoomDefinition room,
        @Nonnull String puzzleId
    ) {
        return session.getSessionId() + ":" + room.getRoomId() + ":" + puzzleId;
    }

    @Nonnull
    private static List<CandleMarker> candlesForPuzzle(@Nonnull GameSession session, @Nonnull String puzzleId) {
        List<CandleMarker> candles = new ArrayList<>();
        for (CandleMarker candle : session.getArenaLayout().getCandles()) {
            if (puzzleId.equals(candle.getPuzzleId())) {
                candles.add(candle);
            }
        }
        candles.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));
        return candles;
    }

    /** Debug helper: skip the current room puzzle and spawn its key reward. */
    @Nonnull
    public static ForceCompleteResult forceCompleteCurrentRoom(@Nonnull GameSession session, @Nonnull World world) {
        if (session.getPhase() != GamePhase.ACTIVE) {
            return ForceCompleteResult.NOT_ACTIVE;
        }
        RoomDefinition currentRoom = RoomProgressionService.currentRoom(session);
        String puzzleKey = puzzleKey(session, currentRoom, currentRoom.getPuzzleId());
        if (COMPLETED_PUZZLES.containsKey(puzzleKey)) {
            return ForceCompleteResult.ALREADY_DONE;
        }
        COMPLETED_PUZZLES.put(puzzleKey, Boolean.TRUE);
        CANDLE_ON_SEQUENCE.remove(puzzleKey);
        CheeseChaseService.markRoomComplete(session, currentRoom, world);
        LibraryBookService.markRoomComplete(session, currentRoom, world);
        boolean keySpawned = KeySpawnService.spawnKeyForRoom(session, world, currentRoom);
        RoomProgressionService.advanceAfterPuzzle(session);
        CheeseChaseService.onCurrentRoomChanged(session, world);
        LibraryBookService.onCurrentRoomChanged(session, world);
        return keySpawned ? ForceCompleteResult.COMPLETED_WITH_KEY : ForceCompleteResult.COMPLETED_WITHOUT_KEY;
    }

    public enum ForceCompleteResult {
        NOT_ACTIVE,
        ALREADY_DONE,
        COMPLETED_WITH_KEY,
        COMPLETED_WITHOUT_KEY
    }
}
