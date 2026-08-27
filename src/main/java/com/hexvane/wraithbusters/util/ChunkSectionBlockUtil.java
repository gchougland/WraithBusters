package com.hexvane.wraithbusters.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3ic;

/**
 * Chunk column and section block access without deprecated {@link World} chunk helpers or
 * {@link com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk} block writes.
 */
public final class ChunkSectionBlockUtil {

    private ChunkSectionBlockUtil() {}

    @Nullable
    public static Ref<ChunkStore> chunkRefIfInMemory(@Nonnull World world, long chunkIndex) {
        Ref<ChunkStore> ref = world.getChunkStore().getChunkReference(chunkIndex);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return ref;
    }

    @Nullable
    public static Ref<ChunkStore> chunkRefIfInMemory(@Nonnull World world, int blockX, int blockZ) {
        return chunkRefIfInMemory(world, ChunkUtil.indexChunkFromBlock(blockX, blockZ));
    }

    public static boolean isChunkInMemory(@Nonnull World world, int blockX, int blockZ) {
        return chunkRefIfInMemory(world, blockX, blockZ) != null;
    }

    @Nullable
    public static BlockChunk blockChunkAt(@Nonnull World world, int blockX, int blockZ) {
        Ref<ChunkStore> ref = chunkRefIfInMemory(world, blockX, blockZ);
        if (ref == null) {
            return null;
        }
        return world.getChunkStore().getStore().getComponent(ref, BlockChunk.getComponentType());
    }

    @Nullable
    public static BlockSection blockSectionAt(@Nonnull World world, int worldX, int worldY, int worldZ) {
        Ref<ChunkStore> sectionRef = sectionRefAt(world, worldX, worldY, worldZ);
        if (sectionRef == null) {
            return null;
        }
        return world.getChunkStore().getStore().getComponent(sectionRef, BlockSection.getComponentType());
    }

    public static int blockId(@Nonnull World world, int x, int y, int z) {
        BlockSection section = blockSectionAt(world, x, y, z);
        if (section == null) {
            return BlockType.EMPTY_ID;
        }
        return section.get(x, y, z);
    }

    @Nullable
    public static BlockType blockType(@Nonnull World world, int x, int y, int z) {
        int id = blockId(world, x, y, z);
        return BlockType.getAssetMap().getAsset(id);
    }

    @Nullable
    public static BlockType blockType(@Nonnull World world, @Nonnull Vector3ic pos) {
        return blockType(world, pos.x(), pos.y(), pos.z());
    }

    public static int rotationIndex(@Nonnull World world, int x, int y, int z) {
        BlockSection section = blockSectionAt(world, x, y, z);
        if (section == null) {
            return RotationTuple.NONE_INDEX;
        }
        return section.getRotationIndex(x, y, z);
    }

    public static int filler(@Nonnull World world, int x, int y, int z) {
        BlockSection section = blockSectionAt(world, x, y, z);
        if (section == null) {
            return FillerBlockUtil.NO_FILLER;
        }
        return section.getFiller(x, y, z);
    }

    public static boolean setTicking(@Nonnull World world, int x, int y, int z, boolean ticking) {
        BlockSection section = blockSectionAt(world, x, y, z);
        if (section == null) {
            return false;
        }
        return section.setTicking(x, y, z, ticking);
    }

    @Nullable
    public static BlockChunk loadBlockChunk(@Nonnull World world, int blockX, int blockZ) {
        BlockChunk inMemory = blockChunkAt(world, blockX, blockZ);
        if (inMemory != null) {
            return inMemory;
        }
        if (!world.isInThread()) {
            return CompletableFuture.supplyAsync(() -> loadBlockChunk(world, blockX, blockZ), world).join();
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
        Ref<ChunkStore> loaded = world.getChunkStore().getChunkReferenceAsync(chunkIndex).join();
        if (loaded == null || !loaded.isValid()) {
            return null;
        }
        return world.getChunkStore().getStore().getComponent(loaded, BlockChunk.getComponentType());
    }

    public static boolean breakBlock(@Nonnull World world, int x, int y, int z, int settings) {
        return setBlockEmpty(world, x, y, z, settings);
    }

    public static boolean performBlockUpdate(
        @Nonnull World world,
        int x,
        int y,
        int z,
        boolean allowPartialLoad
    ) {
        boolean success = true;
        for (int ix = -1; ix < 2; ix++) {
            int wx = x + ix;
            for (int iz = -1; iz < 2; iz++) {
                int wz = z + iz;
                if (allowPartialLoad) {
                    if (loadBlockChunk(world, wx, wz) == null) {
                        success = false;
                        continue;
                    }
                } else if (chunkRefIfInMemory(world, wx, wz) == null) {
                    success = false;
                    continue;
                }
                for (int iy = -1; iy < 2; iy++) {
                    setTicking(world, wx, y + iy, wz, true);
                }
            }
        }
        return success;
    }

    public static boolean setBlock(
        @Nonnull World world,
        int x,
        int y,
        int z,
        int blockId,
        @Nonnull BlockType blockType,
        int rotation,
        int filler,
        int settings
    ) {
        Ref<ChunkStore> sectionRef = sectionRefAt(world, x, y, z);
        if (sectionRef == null) {
            return false;
        }
        return BlockOperations.setBlock(
            world.getChunkStore(),
            sectionRef,
            x,
            y,
            z,
            blockId,
            blockType,
            rotation,
            filler,
            settings
        );
    }

    public static boolean setBlockEmpty(@Nonnull World world, int x, int y, int z, int settings) {
        return setBlock(
            world,
            x,
            y,
            z,
            BlockType.EMPTY_ID,
            BlockType.EMPTY,
            RotationTuple.NONE_INDEX,
            FillerBlockUtil.NO_FILLER,
            settings
        );
    }

    public static void setBlockInteractionState(
        @Nonnull World world,
        int x,
        int y,
        int z,
        @Nonnull BlockType blockType,
        @Nonnull String state,
        boolean force
    ) {
        Ref<ChunkStore> sectionRef = sectionRefAt(world, x, y, z);
        if (sectionRef == null) {
            return;
        }
        BlockOperations.setBlockInteractionState(
            world.getChunkStore(),
            sectionRef,
            x,
            y,
            z,
            blockType,
            state,
            force
        );
    }

    public static void setBlockInteractionState(
        @Nonnull World world,
        @Nonnull Vector3ic pos,
        @Nonnull BlockType blockType,
        @Nonnull String state
    ) {
        setBlockInteractionState(world, pos.x(), pos.y(), pos.z(), blockType, state, false);
    }

    public static boolean testPlaceBlock(
        @Nonnull World world,
        int x,
        int y,
        int z,
        @Nonnull BlockType blockType,
        int rotationIndex,
        @Nonnull BlockOperations.TestBlockFunction func
    ) {
        BlockSection section = blockSectionAt(world, x, y, z);
        if (section == null) {
            return false;
        }
        return BlockOperations.testPlaceBlock(
            world.getChunkStore().getStore(),
            section,
            x,
            y,
            z,
            blockType,
            rotationIndex,
            func
        );
    }

    public static Store<ChunkStore> chunkStore(@Nonnull World world) {
        return world.getChunkStore().getStore();
    }

    @Nullable
    public static Ref<ChunkStore> sectionRefAt(@Nonnull World world, int worldX, int worldY, int worldZ) {
        if (worldY < ChunkUtil.MIN_Y || worldY > ChunkUtil.HEIGHT_MINUS_1) {
            return null;
        }
        Ref<ChunkStore> sectionRef = world.getChunkStore().getChunkSectionReferenceAtBlock(worldX, worldY, worldZ);
        if (sectionRef == null || !sectionRef.isValid()) {
            return null;
        }
        return sectionRef;
    }
}
