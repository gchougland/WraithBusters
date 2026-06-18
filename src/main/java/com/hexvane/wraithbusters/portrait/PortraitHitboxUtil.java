package com.hexvane.wraithbusters.portrait;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Aligns portrait NPC collision with the placed block's hitbox. */
public final class PortraitHitboxUtil {
    private PortraitHitboxUtil() {}

    public static void applyBlockHitbox(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull BlockType blockType,
        @Nonnull RotationTuple rotationTuple
    ) {
        BlockBoundingBoxes hitboxes = BlockBoundingBoxes.getAssetMap().getAsset(blockType.getHitboxTypeIndex());
        if (hitboxes == null) {
            return;
        }
        Box blockBox = new Box(hitboxes.get(rotationTuple.index()).getBoundingBox());
        double centerX = blockBox.middleX();
        double centerY = blockBox.middleY();
        double centerZ = blockBox.middleZ();
        Box centered = new Box(
            blockBox.min.x - centerX,
            blockBox.min.y - centerY,
            blockBox.min.z - centerZ,
            blockBox.max.x - centerX,
            blockBox.max.y - centerY,
            blockBox.max.z - centerZ
        );
        BoundingBox component = store.getComponent(ref, BoundingBox.getComponentType());
        if (component == null) {
            return;
        }
        component.setBaseModelBox(centered.clone());
        component.setBoundingBox(centered);
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform != null) {
            Rotation3f rotation = transform.getRotation();
            component.applyRotation(rotation.pitch(), rotation.yaw(), rotation.roll());
        }
    }
}
