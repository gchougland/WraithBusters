package com.hexvane.wraithbusters.inventory;

import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Keeps overworld items separate from the minigame instance inventory. */
public final class MinigameInventoryService {
    private MinigameInventoryService() {}

    public static void stashOverworldInventory(
        @Nonnull GameSession session,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        PlayerSessionState state = playerState(session, playerRef, store);
        if (state == null || state.isInventoryStashed()) {
            return;
        }
        CombinedItemContainer combined = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        List<ItemStack> stashed = new ArrayList<>();
        combined.forEach((slot, stack) -> {
            if (!ItemStack.isEmpty(stack)) {
                stashed.add(stack.withQuantity(stack.getQuantity()));
            }
        });
        InventoryUtils.clear(playerRef, store);
        state.getStashedItems().clear();
        state.getStashedItems().addAll(stashed);
        state.setInventoryStashed(true);
    }

    public static void clearRuntimeInventory(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        InventoryUtils.clear(playerRef, store);
    }

    /** Clears carried inventory after porting when overworld items were already stashed. */
    public static void ensureEmptyIfStashed(
        @Nonnull GameSession session,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        PlayerSessionState state = playerState(session, playerRef, store);
        if (state != null && state.isInventoryStashed()) {
            clearRuntimeInventory(playerRef, store);
        }
    }

    public static void restoreOverworldInventory(
        @Nonnull GameSession session,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        PlayerSessionState state = playerState(session, playerRef, store);
        restoreOverworldInventory(state, playerRef, store);
    }

    public static void restoreOverworldInventory(
        @Nullable PlayerSessionState state,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        if (state == null || !state.isInventoryStashed()) {
            return;
        }
        InventoryUtils.clear(playerRef, store);
        CombinedItemContainer combined = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        for (ItemStack stack : state.getStashedItems()) {
            if (!ItemStack.isEmpty(stack)) {
                combined.addItemStack(stack);
            }
        }
        state.getStashedItems().clear();
        state.setInventoryStashed(false);
    }

    @Nullable
    private static PlayerSessionState playerState(
        @Nonnull GameSession session,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        UUIDComponent uuid = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uuid == null) {
            return null;
        }
        return session.getOrCreatePlayer(uuid.getUuid());
    }
}
