package com.hexvane.wraithbusters.interaction;

import com.hexvane.wraithbusters.door.LockedDoorService;
import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.arena.RoomDefinition;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class LockedDoorInteraction extends WraithBustersBlockInteractionBase {
    private String symbolId = "circle";

    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<LockedDoorInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(LockedDoorInteraction.class, LockedDoorInteraction::new, SimpleBlockInteraction.CODEC)
            .append(new KeyedCodec<>("SymbolId", Codec.STRING), (o, v) -> o.symbolId = v, o -> o.symbolId)
            .add()
            .documentation("WraithBusters locked door.")
            .build();

    @Override
    protected void simulateInteractWithBlock(
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nullable ItemStack itemInHand,
        @Nonnull World world,
        @Nonnull Vector3i targetBlock
    ) {
        if (type != InteractionType.Use || blocksNonHumanDoorUser(context)) {
            context.getState().state = InteractionState.Failed;
        }
    }

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
        GameSession session = GameRegistry.get().getSessionForWorld(world.getWorldConfig().getUuid());
        Store<EntityStore> store = playerRef.getStore();
        if (!LockedDoorService.isAliveHuman(session, playerRef, store)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        RoomDefinition room = LockedDoorService.findRoomAtDoor(session, targetBlock);
        if (room != null) {
            if (LockedDoorService.tryOpenWithKey(session, world, room, itemInHand, playerRef, store)) {
                context.getState().state = InteractionState.Finished;
                return;
            }
        }
        context.getState().state = InteractionState.Failed;
    }

    /**
     * Ghosts and spectators fly during rounds; suppress the open-door prompt for them on the client.
     * Creative builders may still fly while testing.
     */
    private static boolean blocksNonHumanDoorUser(@Nonnull InteractionContext context) {
        Ref<EntityStore> playerRef = context.getEntity();
        if (playerRef == null) {
            return true;
        }
        Store<EntityStore> store = playerRef.getStore();
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player != null && player.getGameMode() == GameMode.Creative) {
            return false;
        }
        MovementManager movement = store.getComponent(playerRef, MovementManager.getComponentType());
        return movement != null && movement.getSettings().canFly;
    }
}
