package com.hexvane.wraithbusters.util;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Thin wrappers over {@link ChunkSectionBlockUtil} for existing call sites. */
public final class BlockSectionQueries {
    private BlockSectionQueries() {}

    @Nullable
    public static BlockSection getSectionAtBlock(@Nonnull World world, int x, int y, int z) {
        return ChunkSectionBlockUtil.blockSectionAt(world, x, y, z);
    }

    public static int getRotationIndex(@Nonnull World world, int x, int y, int z) {
        return ChunkSectionBlockUtil.rotationIndex(world, x, y, z);
    }

    public static int getFiller(@Nonnull World world, int x, int y, int z) {
        return ChunkSectionBlockUtil.filler(world, x, y, z);
    }

    /** Returns null when the column is not in memory, so callers skip work during world startup. */
    @Nullable
    public static BlockType getBlockTypeIfLoaded(@Nonnull World world, int x, int y, int z) {
        if (!ChunkSectionBlockUtil.isChunkInMemory(world, x, z)) {
            return null;
        }
        return ChunkSectionBlockUtil.blockType(world, x, y, z);
    }
}
