package com.hexvane.wraithbusters.interaction;

import com.hexvane.wraithbusters.WraithBustersPlugin;
import com.hexvane.wraithbusters.door.DoorKeySymbols;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class ExorcismInteraction extends WraithBustersBlockInteractionBase {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<ExorcismInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(ExorcismInteraction.class, ExorcismInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("WraithBusters attic exorcism table.")
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
        GameSession session = GameRegistry.get().getSessionForWorld(world.getWorldConfig().getUuid());
        WraithBustersPlugin plugin = WraithBustersPlugin.get();
        if (session == null || plugin == null || session.getPhase() != GamePhase.ACTIVE) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Store<EntityStore> store = playerRef.getStore();
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        var state = session.getOrCreatePlayer(pr.getUuid());
        if (state.getRole() != PlayerRole.HUMAN || !state.isAlive()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!session.isAtticUnlocked() && !state.hasAtticKey() && !hasAtticKeyInInventory(playerRef, store)) {
            pr.sendMessage(Message.translation("server.wraithbusters.exorcism.needKey"));
            context.getState().state = InteractionState.Failed;
            return;
        }
        plugin.getGameService().humansWin(session, world);
        context.getState().state = InteractionState.Finished;
    }

    private static boolean hasAtticKeyInInventory(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        CombinedItemContainer inventory = InventoryComponent.getCombined(
            store, playerRef, InventoryComponent.EVERYTHING
        );
        if (inventory == null) {
            return false;
        }
        return inventory.countItemStacks(
            stack -> DoorKeySymbols.ATTIC_KEY_ITEM.equals(stack.getItemId())
        ) > 0;
    }
}
