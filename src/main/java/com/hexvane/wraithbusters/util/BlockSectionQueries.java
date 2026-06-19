package com.hexvane.wraithbusters.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.math.util.ChunkUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Reads block section data through chunk-store section refs (replaces deprecated WorldChunk accessors). */
public final class BlockSectionQueries {
    private BlockSectionQueries() {}

    @Nullable
    public static BlockSection getSectionAtBlock(@Nonnull World world, int x, int y, int z) {
        if (y < 0 || y >= 320) {
            return null;
        }
        Ref<ChunkStore> sectionRef = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return null;
        }
        Store<ChunkStore> store = world.getChunkStore().getStore();
        return store.getComponent(sectionRef, BlockSection.getComponentType());
    }

    public static int getRotationIndex(@Nonnull World world, int x, int y, int z) {
        BlockSection section = getSectionAtBlock(world, x, y, z);
        return section == null ? 0 : section.getRotationIndex(x, y, z);
    }

    public static int getFiller(@Nonnull World world, int x, int y, int z) {
        BlockSection section = getSectionAtBlock(world, x, y, z);
        return section == null ? 0 : section.getFiller(x, y, z);
    }

    /** Avoids blocking chunk loads during world startup (see {@code IWorldChunks#waitForFutureWithoutLock}). */
    @Nullable
    public static BlockType getBlockTypeIfLoaded(@Nonnull World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return null;
        }
        return chunk.getBlockType(x, y, z);
    }
}
