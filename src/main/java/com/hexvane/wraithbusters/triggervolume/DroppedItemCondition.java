package com.hexvane.wraithbusters.triggervolume;

import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerCondition;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerContext;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DroppedItemCondition extends TriggerCondition {
    @Nonnull
    public static final BuilderCodec<DroppedItemCondition> CODEC = BuilderCodec.builder(
            DroppedItemCondition.class, DroppedItemCondition::new, BASE_CODEC
        )
        .append(new KeyedCodec<>("Item", Codec.STRING, false), (c, itemId) -> c.itemId = itemId, c -> c.itemId)
        .add()
        .build();

    @Nullable
    private String itemId;

    @Override
    public boolean test(@Nonnull TriggerContext context) {
        Ref<EntityStore> entityRef = context.getEntityRef();
        ItemComponent itemComponent = context.getStore().getComponent(entityRef, ItemComponent.getComponentType());
        if (itemComponent == null) {
            return false;
        }
        ItemStack stack = itemComponent.getItemStack();
        if (ItemStack.isEmpty(stack) || !stack.isValid()) {
            return false;
        }
        if (itemId == null || itemId.isBlank()) {
            return true;
        }
        return itemId.equals(stack.getItemId());
    }
}
