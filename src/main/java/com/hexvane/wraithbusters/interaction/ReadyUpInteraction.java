package com.hexvane.wraithbusters.interaction;

import com.hexvane.wraithbusters.WraithBustersPlugin;
import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GameSession;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class ReadyUpInteraction extends WraithBustersBlockInteractionBase {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<ReadyUpInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(ReadyUpInteraction.class, ReadyUpInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("WraithBusters lobby ready toggle.")
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
        UUIDComponent uuid = store.getComponent(playerRef, UUIDComponent.getComponentType());
        GameSession session = GameRegistry.get().getSessionForWorld(world.getWorldConfig().getUuid());
        WraithBustersPlugin plugin = WraithBustersPlugin.get();
        if (uuid == null || session == null || plugin == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        plugin.getGameService().toggleReady(session, uuid.getUuid(), playerRef, store);
        context.getState().state = InteractionState.Finished;
    }
}
