package com.hexvane.wraithbusters.puzzle;

import com.hexvane.wraithbusters.WraithBustersPlugin;
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
    private static final ConcurrentHashMap<String, Boolean> COMPLETED_PUZZLES = new ConcurrentHashMap<>();

    private PuzzleService() {}

    public static void resetForSession(@Nonnull GameSession session) {
        COMPLETED_PUZZLES.keySet().removeIf(key -> key.startsWith(session.getSessionId().toString()));
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
        String puzzleId = candle.getPuzzleId();
        String completedKey = session.getSessionId() + ":" + puzzleId;
        if (COMPLETED_PUZZLES.containsKey(completedKey)) {
            return;
        }
        DeferredWorldTasks.run(world, () -> evaluateCandlePuzzle(session, world, playerRef, store, candle, puzzleId, completedKey));
    }

    private static void evaluateCandlePuzzle(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CandleMarker candle,
        @Nonnull String puzzleId,
        @Nonnull String completedKey
    ) {
        if (COMPLETED_PUZZLES.containsKey(completedKey)) {
            return;
        }
        if (!matchesSolution(session, world, puzzleId)) {
            return;
        }
        RoomDefinition solvedRoom = RoomProgressionService.currentRoom(session);
        COMPLETED_PUZZLES.put(completedKey, Boolean.TRUE);
        RoomProgressionService.advanceAfterPuzzle(session);
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.translation("server.wraithbusters.puzzle.candle.complete"));
        }
        WraithBustersPlugin plugin = WraithBustersPlugin.get();
        if (plugin != null) {
            KeySpawnService.spawnKeyForRoom(session, world, solvedRoom);
        }
    }

    private static boolean matchesSolution(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull String puzzleId
    ) {
        for (CandleMarker candle : candlesForPuzzle(session, puzzleId)) {
            if (isCandleLit(world, candle.getBlockPos()) != candle.isRequiredOn()) {
                return false;
            }
        }
        return true;
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
        return state == null || "default".equals(state) || "On".equals(state);
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
}
