package com.hexvane.wraithbusters.interaction;

import com.hexvane.wraithbusters.util.BlockSectionQueries;
import com.hexvane.wraithbusters.WraithBustersPlugin;
import com.hexvane.wraithbusters.arena.PossessableMarker;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hexvane.wraithbusters.possessable.PossessableService;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class TempleCandleInteraction extends WraithBustersBlockInteractionBase {
    private static final Map<String, String> STATE_CHANGES = Map.of("default", "Off", "Off", "default");

    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<TempleCandleInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(TempleCandleInteraction.class, TempleCandleInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("WraithBusters spooky temple candle: ghost possess or on/off toggle.")
            .build();

    @Override
    protected void interactWithBlock(
        @Nonnull World world,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nullable ItemStack itemInHand,
        @Nonnull Vector3i targetBlock,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        if (type != InteractionType.Use) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Ref<EntityStore> playerRef = context.getEntity();
        if (playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Store<EntityStore> store = playerRef.getStore();
        PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
        GameSession session = GameRegistry.get().getSessionForWorld(world.getWorldConfig().getUuid());
        WraithBustersPlugin plugin = WraithBustersPlugin.get();
        if (session != null && plugin != null && session.getPhase() == GamePhase.ACTIVE && player != null) {
            PlayerSessionState state = session.getPlayers().get(player.getUuid());
            if (state != null && state.getRole() == PlayerRole.GHOST) {
                PossessableMarker marker = PossessableService.findAt(session, world, targetBlock);
                if (marker != null && "candle".equals(marker.getTypeId())) {
                    PossessableService.ActivateResult result = PossessableService.tryActivate(
                        session,
                        world,
                        playerRef,
                        store,
                        commandBuffer,
                        marker,
                        plugin.getPluginConfig(),
                        targetBlock
                    );
                    switch (result) {
                        case SUCCESS -> {
                            context.getState().state = InteractionState.Finished;
                            return;
                        }
                        case NOT_ACTIVE -> {
                            send(player, "server.wraithbusters.possess.notActive");
                            context.getState().state = InteractionState.Failed;
                            return;
                        }
                        case NOT_GHOST, UNKNOWN_TYPE -> {
                            send(player, "server.wraithbusters.possess.notGhost");
                            context.getState().state = InteractionState.Failed;
                            return;
                        }
                        case NOT_ENOUGH_MANA, NO_TARGET -> {
                            context.getState().state = InteractionState.Failed;
                            return;
                        }
                    }
                }
            }
        }
        if (toggleCandleState(world, commandBuffer, context, targetBlock)) {
            context.getState().state = InteractionState.Finished;
        } else {
            context.getState().state = InteractionState.Failed;
        }
    }

    private static boolean toggleCandleState(
        @Nonnull World world,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context,
        @Nonnull Vector3i targetBlock
    ) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(targetBlock.x, targetBlock.z));
        if (chunk == null) {
            return false;
        }
        BlockType current = chunk.getBlockType(targetBlock);
        String currentState = current.getStateForBlock(current);
        if (currentState == null) {
            currentState = "default";
        }
        String newState = STATE_CHANGES.get(currentState);
        if (newState == null) {
            return false;
        }
        String newBlock = current.getBlockKeyForState(newState);
        if (newBlock == null) {
            return false;
        }
        int newBlockId = BlockType.getAssetMap().getIndex(newBlock);
        if (newBlockId == Integer.MIN_VALUE) {
            return false;
        }
        BlockType newBlockType = BlockType.getAssetMap().getAsset(newBlockId);
        int rotation = BlockSectionQueries.getRotationIndex(world, targetBlock.x, targetBlock.y, targetBlock.z);
        int settings = 262;
        chunk.setBlock(targetBlock.x(), targetBlock.y(), targetBlock.z(), newBlockId, newBlockType, rotation, 0, settings);
        BlockType interactionStateBlock = current.getBlockForState(newState);
        if (interactionStateBlock == null) {
            return true;
        }
        int soundEventIndex = interactionStateBlock.getInteractionSoundEventIndex();
        if (soundEventIndex == 0) {
            return true;
        }
        Ref<EntityStore> ref = context.getEntity();
        SoundUtil.playSoundEvent3d(ref, soundEventIndex, targetBlock.x + 0.5, targetBlock.y + 0.5, targetBlock.z + 0.5, commandBuffer);
        return true;
    }

    private static void send(@Nullable PlayerRef player, @Nonnull String key) {
        if (player != null) {
            player.sendMessage(Message.translation(key));
        }
    }
}
