package com.hexvane.wraithbusters.interaction;

import com.hexvane.wraithbusters.WraithBustersPlugin;
import com.hexvane.wraithbusters.arena.PossessableMarker;
import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.possessable.PossessableService;
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

public final class PossessInteraction extends WraithBustersBlockInteractionBase {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<PossessInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(PossessInteraction.class, PossessInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("WraithBusters ghost possessable activation.")
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
        if (session == null || plugin == null) {
            send(player, "server.wraithbusters.possess.noSession");
            context.getState().state = InteractionState.Failed;
            return;
        }
        PossessableMarker marker = PossessableService.findAt(session, world, targetBlock);
        if (marker == null) {
            send(player, "server.wraithbusters.possess.noMarker");
            context.getState().state = InteractionState.Failed;
            return;
        }
        PossessableService.ActivateResult result = PossessableService.tryActivate(
            session,
            world,
            playerRef,
            store,
            commandBuffer,
            marker,
            plugin.getPluginConfig()
        );
        switch (result) {
            case SUCCESS -> context.getState().state = InteractionState.Finished;
            case NOT_ACTIVE -> {
                send(player, "server.wraithbusters.possess.notActive");
                context.getState().state = InteractionState.Failed;
            }
            case NOT_GHOST -> {
                send(player, "server.wraithbusters.possess.notGhost");
                context.getState().state = InteractionState.Failed;
            }
            case NOT_ENOUGH_MANA, NO_TARGET, UNKNOWN_TYPE -> context.getState().state = InteractionState.Failed;
        }
    }

    private static void send(@Nullable PlayerRef player, @Nonnull String key) {
        if (player != null) {
            player.sendMessage(Message.translation(key));
        }
    }
}
