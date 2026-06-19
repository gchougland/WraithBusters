package com.hexvane.wraithbusters.npc;

import com.hexvane.wraithbusters.arena.RoomDefinition;
import com.hexvane.wraithbusters.door.HumanLockedDoorMarkerService;
import com.hexvane.wraithbusters.door.LockedDoorService;
import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GameSession;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ActionWraithBustersOpenLockedDoor extends ActionBase {
    public ActionWraithBustersOpenLockedDoor(
        @Nonnull BuilderActionWraithBustersOpenLockedDoor builder,
        @Nonnull BuilderSupport support
    ) {
        super(builder);
    }

    @Override
    public boolean canExecute(
        @Nonnull Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref,
        @Nonnull Role role,
        InfoProvider sensorInfo,
        double dt,
        @Nonnull Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store
    ) {
        return super.canExecute(ref, role, sensorInfo, dt, store)
            && role.getStateSupport().getInteractionIterationTarget() != null;
    }

    @Override
    public boolean execute(
        @Nonnull Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref,
        @Nonnull Role role,
        InfoProvider sensorInfo,
        double dt,
        @Nonnull Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store
    ) {
        super.execute(ref, role, sensorInfo, dt, store);
        Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> playerRef =
            role.getStateSupport().getInteractionIterationTarget();
        if (playerRef == null) {
            return false;
        }
        @Nullable String roomId = HumanLockedDoorMarkerService.findRoomId(ref, store);
        if (roomId == null) {
            return false;
        }
        PlayerRef playerRefComponent = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComponent == null) {
            return false;
        }
        World world = store.getExternalData().getWorld();
        GameSession session = GameRegistry.get().resolveSessionForWorld(world);
        if (session == null) {
            return false;
        }
        RoomDefinition room = LockedDoorService.findRoom(session, roomId);
        if (room == null) {
            return false;
        }
        ItemStack itemInHand = InventoryComponent.getItemInHand(store, playerRef);
        return LockedDoorService.tryOpenWithKey(session, world, room, itemInHand, playerRef, store);
    }
}
