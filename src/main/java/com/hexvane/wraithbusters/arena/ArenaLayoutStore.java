package com.hexvane.wraithbusters.arena;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.WraithBustersPlugin;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ArenaLayoutStore {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private ArenaLayoutStore() {}

    @Nonnull
    public static Path arenasDirectory(@Nonnull WraithBustersPlugin plugin) {
        return plugin.getDataDirectory().resolve("wraithbusters").resolve("arenas");
    }

    public static void ensureDefaultArenaFiles(@Nonnull WraithBustersPlugin plugin) {
        ensureBundledArenaFile(plugin, WraithBustersConstants.DEFAULT_ARENA_ID);
    }

    private static void ensureBundledArenaFile(@Nonnull WraithBustersPlugin plugin, @Nonnull String arenaId) {
        Path file = arenasDirectory(plugin).resolve(arenaId + ".json");
        if (Files.isRegularFile(file)) {
            return;
        }
        ArenaLayout bundled = loadBundled(arenaId);
        if (bundled == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            save(plugin, bundled);
            LOGGER.atInfo().log("Installed bundled default arena %s", arenaId);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to install bundled arena %s", arenaId);
        }
    }

    @Nonnull
    public static ArenaLayout loadOrDefault(@Nonnull WraithBustersPlugin plugin, @Nonnull String arenaId) {
        Path file = arenasDirectory(plugin).resolve(arenaId + ".json");
        if (Files.isRegularFile(file)) {
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                ArenaLayout layout = ArenaLayoutJson.fromJson(text);
                if (layout != null) {
                    layout.ensureDefaultSpawns();
                    return layout;
                }
            } catch (IOException e) {
                LOGGER.atWarning().withCause(e).log("Failed to load arena %s", arenaId);
            }
        }
        ArenaLayout bundled = loadBundled(arenaId);
        if (bundled != null) {
            return bundled;
        }
        return createFallbackLayout(arenaId);
    }

    public static void save(@Nonnull WraithBustersPlugin plugin, @Nonnull ArenaLayout layout) throws IOException {
        Path dir = arenasDirectory(plugin);
        Files.createDirectories(dir);
        Path file = dir.resolve(layout.getArenaId() + ".json");
        Files.writeString(file, ArenaLayoutJson.toJson(layout) + "\n", StandardCharsets.UTF_8);
    }

    @Nonnull
    public static ArenaLayout createDefaultLayout(@Nonnull String arenaId) {
        ArenaLayout bundled = loadBundled(arenaId);
        if (bundled != null) {
            return bundled;
        }
        return createFallbackLayout(arenaId);
    }

    @Nullable
    private static ArenaLayout loadBundled(@Nonnull String arenaId) {
        String resourcePath = "wraithbusters/arenas/" + arenaId + ".json";
        try (InputStream input = ArenaLayoutStore.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                return null;
            }
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            ArenaLayout layout = ArenaLayoutJson.fromJson(text);
            if (layout != null) {
                layout.ensureDefaultSpawns();
            }
            return layout;
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load bundled arena %s", arenaId);
            return null;
        }
    }

    @Nonnull
    private static ArenaLayout createFallbackLayout(@Nonnull String arenaId) {
        ArenaLayout layout = new ArenaLayout();
        layout.setArenaId(arenaId);
        layout.setLobbySpawn(new Transform(0, 100, 0));
        layout.getHumanSpawns().add(new Transform(2, 100, 0));
        layout.getGhostSpawns().add(new Transform(-2, 110, 0));
        layout.setExorcismTable(new org.joml.Vector3i(0, 101, 4));

        RoomDefinition room = new RoomDefinition();
        room.setRoomId("library");
        room.addDoorBlock(new org.joml.Vector3i(0, 100, 2));
        room.setSymbolId("circle");
        room.setPuzzleId("candles");
        room.setKeySpawn(new org.joml.Vector3i(0, 100, 3));
        layout.getRooms().add(room);

        for (int i = 0; i < 3; i++) {
            CandleMarker candle = new CandleMarker();
            candle.setPuzzleId("candles");
            candle.setIndex(i);
            candle.setBlockPos(new org.joml.Vector3i(i, 100, 1));
            layout.getCandles().add(candle);
        }

        PossessableMarker plate = new PossessableMarker();
        plate.setTypeId("plate");
        plate.setBlockPos(new org.joml.Vector3i(-2, 100, 1));
        layout.getPossessables().add(plate);

        layout.getManaPickups().add(new org.joml.Vector3i(-1, 101, 1));
        return layout;
    }

    @Nonnull
    public static ArenaLayout copyForRuntime(@Nonnull ArenaLayout source) {
        String json = ArenaLayoutJson.toJson(source);
        ArenaLayout copy = ArenaLayoutJson.fromJson(json);
        if (copy == null) {
            return createDefaultLayout(WraithBustersConstants.DEFAULT_ARENA_ID);
        }
        copy.ensureDefaultSpawns();
        return copy;
    }
}
