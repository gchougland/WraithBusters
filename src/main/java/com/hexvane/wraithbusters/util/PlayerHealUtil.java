package com.hexvane.wraithbusters.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public final class PlayerHealUtil {
    private PlayerHealUtil() {}

    public static void fullyHeal(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        EntityStatMap statMap = store.getComponent(playerRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }
        int healthIndex = DefaultEntityStatTypes.getHealth();
        if (healthIndex != Integer.MIN_VALUE) {
            statMap.resetStatValue(healthIndex);
        }
        int staminaIndex = DefaultEntityStatTypes.getStamina();
        if (staminaIndex != Integer.MIN_VALUE) {
            statMap.resetStatValue(staminaIndex);
        }
    }
}
