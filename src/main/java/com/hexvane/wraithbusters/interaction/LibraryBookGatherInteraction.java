package com.hexvane.wraithbusters.interaction;

import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.puzzle.LibraryBookService;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class LibraryBookGatherInteraction extends WraithBustersBlockInteractionBase {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<LibraryBookGatherInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(LibraryBookGatherInteraction.class, LibraryBookGatherInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("WraithBusters library book gather interaction.")
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
        if (session == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (session.getPhase() != GamePhase.ACTIVE) {
            send(player, "server.wraithbusters.puzzle.libraryBooks.notActive");
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (player == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        var playerState = session.getOrCreatePlayer(player.getUuid());
        if (playerState.getRole() != PlayerRole.HUMAN || !playerState.isAlive()) {
            send(player, "server.wraithbusters.puzzle.libraryBooks.humansOnly");
            context.getState().state = InteractionState.Failed;
            return;
        }
        DeferredWorldTasks.run(
            world,
            () -> LibraryBookService.tryGatherBook(session, world, playerRef, store, targetBlock)
        );
        context.getState().state = InteractionState.Finished;
    }

    private static void send(@Nullable PlayerRef player, @Nonnull String key) {
        if (player != null) {
            player.sendMessage(Message.translation(key));
        }
    }
}
