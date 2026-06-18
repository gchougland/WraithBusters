package com.hexvane.wraithbusters.portrait;

import com.hexvane.wraithbusters.WraithBustersConstants;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public enum PortraitVariant {
    SLOTH(WraithBustersConstants.SLOTH_PORTRAIT_BLOCK_ID, WraithBustersConstants.SLOTH_PORTRAIT_NPC_ROLE),
    KLOPS(WraithBustersConstants.KLOPS_PORTRAIT_BLOCK_ID, WraithBustersConstants.KLOPS_PORTRAIT_NPC_ROLE),
    OUTLANDER(WraithBustersConstants.OUTLANDER_PORTRAIT_BLOCK_ID, WraithBustersConstants.OUTLANDER_PORTRAIT_NPC_ROLE);

    @Nonnull
    private final String blockId;
    @Nonnull
    private final String npcRoleId;

    PortraitVariant(@Nonnull String blockId, @Nonnull String npcRoleId) {
        this.blockId = blockId;
        this.npcRoleId = npcRoleId;
    }

    @Nonnull
    public String blockId() {
        return blockId;
    }

    @Nonnull
    public String npcRoleId() {
        return npcRoleId;
    }

    @Nullable
    public static PortraitVariant fromBlockId(@Nullable String blockId) {
        if (blockId == null) {
            return null;
        }
        for (PortraitVariant variant : values()) {
            if (variant.blockId.equals(blockId)) {
                return variant;
            }
        }
        return null;
    }
}
