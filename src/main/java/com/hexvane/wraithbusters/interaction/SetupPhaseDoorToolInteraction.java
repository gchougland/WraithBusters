package com.hexvane.wraithbusters.interaction;

import com.hexvane.wraithbusters.WraithBustersMessages;
import com.hexvane.wraithbusters.WraithBustersPlugin;
import com.hexvane.wraithbusters.setup.SetupModeService;
import com.hexvane.wraithbusters.setup.SetupTargetUtil;
import com.hexvane.wraithbusters.setup.PhaseDoorAnalyzer;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

public final class SetupPhaseDoorToolInteraction extends SimpleInstantInteraction {
    private static final double TARGET_DISTANCE = 24.0;

    @Nonnull
    public static final BuilderCodec<SetupPhaseDoorToolInteraction> CODEC =
        BuilderCodec
            .builder(SetupPhaseDoorToolInteraction.class, SetupPhaseDoorToolInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("WraithBusters setup tool: secondary places a bidirectional phase portal on a locked door, primary removes.")
            .build();

    @Override
    public boolean needsRemoteSync() {
        return true;
    }

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Client;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Ref<EntityStore> ref = context.getEntity();
        if (ref == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        UUIDComponent uuid = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        WraithBustersPlugin plugin = WraithBustersPlugin.get();
        if (uuid == null || playerRef == null || plugin == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!SetupModeService.isActive(uuid.getUuid())) {
            playerRef.sendMessage(WraithBustersMessages.translation("setup.notInMode"));
            context.getState().state = InteractionState.Failed;
            return;
        }
        Vector3i hint = resolveTargetHint(type, context, ref, commandBuffer);
        if (hint == null) {
            playerRef.sendMessage(WraithBustersMessages.translation("setup.phaseDoor.noTarget"));
            context.getState().state = InteractionState.Failed;
            return;
        }
        World world = commandBuffer.getExternalData().getWorld();
        Vector3i targetBlock = PhaseDoorAnalyzer.resolveSeedBlock(world, hint);
        if (targetBlock == null) {
            playerRef.sendMessage(WraithBustersMessages.translation("setup.phaseDoor.notLockedDoor"));
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (type == InteractionType.Primary) {
            if (!SetupModeService.removePhaseDoorAtBlock(uuid.getUuid(), targetBlock, world)) {
                playerRef.sendMessage(WraithBustersMessages.translation("setup.phaseDoor.noneRemoved"));
                context.getState().state = InteractionState.Failed;
                return;
            }
            playerRef.sendMessage(WraithBustersMessages.translation("setup.phaseDoor.removed"));
        } else if (type == InteractionType.Secondary) {
            SetupModeService.PhaseDoorPlaceResult result = SetupModeService.placePhaseDoorOnLockedDoor(
                uuid.getUuid(),
                targetBlock,
                world
            );
            switch (result) {
                case COMPLETED -> playerRef.sendMessage(WraithBustersMessages.translation("setup.phaseDoor.completed"));
                case NOT_LOCKED_DOOR -> {
                    playerRef.sendMessage(WraithBustersMessages.translation("setup.phaseDoor.notLockedDoor"));
                    context.getState().state = InteractionState.Failed;
                    return;
                }
                default -> {
                    context.getState().state = InteractionState.Failed;
                    return;
                }
            }
        } else {
            context.getState().state = InteractionState.Failed;
            return;
        }
        context.getState().state = InteractionState.Finished;
    }

    @Nullable
    private static Vector3i resolveTargetHint(
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        InteractionSyncData clientState = context.getClientState();
        if (clientState != null && clientState.raycastHit != null) {
            Position raycastHit = clientState.raycastHit;
            return SetupTargetUtil.blockFromHit(new Vector3d(raycastHit.x, raycastHit.y, raycastHit.z));
        }
        Vector3d fromTargetUtil = TargetUtil.getTargetLocation(ref, TARGET_DISTANCE, commandBuffer);
        if (fromTargetUtil != null) {
            return SetupTargetUtil.blockFromHit(fromTargetUtil);
        }
        if (type == InteractionType.Secondary) {
            TransformComponent transform = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
            if (transform != null) {
                Vector3d pos = transform.getPosition();
                return SetupTargetUtil.blockFromHit(pos);
            }
        }
        return null;
    }
}
