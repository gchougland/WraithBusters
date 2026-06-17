package com.hexvane.wraithbusters.door;

import com.hexvane.wraithbusters.arena.RoomDefinition;
import com.hexvane.wraithbusters.game.GameSession;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class LockedDoorService {
    private LockedDoorService() {}

    @Nullable
    public static RoomDefinition findRoomAtDoor(@Nonnull GameSession session, @Nonnull Vector3i blockPos) {
        for (RoomDefinition room : session.getArenaLayout().getRooms()) {
            for (Vector3i door : room.getDoorBlocks()) {
                if (door.x == blockPos.x && door.y == blockPos.y && door.z == blockPos.z) {
                    return room;
                }
            }
        }
        return null;
    }

    public static boolean tryOpenWithKey(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull RoomDefinition room,
        @Nullable ItemStack keyInHand,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        PlayerSessionState state = playerState(session, playerRef, store);
        if (state == null || state.getRole() != PlayerRole.HUMAN || !state.isAlive()) {
            return false;
        }
        if (!RoomProgressionService.isDoorUnlocked(session, room.getRoomId())) {
            send(store, playerRef, "server.wraithbusters.door.locked");
            return false;
        }
        if (!RoomProgressionService.isStartingRoom(session, room.getRoomId())) {
            if (keyInHand == null || !DoorKeySymbols.isKeyItem(keyInHand.getItemId())) {
                send(store, playerRef, "server.wraithbusters.door.needKey");
                return false;
            }
            String keySymbol = DoorKeySymbols.symbolFromItemId(keyInHand.getItemId());
            String roomSymbol = DoorKeySymbols.normalizeSymbolId(room.getSymbolId());
            if (!roomSymbol.equals(keySymbol)) {
                send(store, playerRef, "server.wraithbusters.door.wrongKey");
                return false;
            }
        }
        RoomDoorService.openDoor(world, room);
        send(store, playerRef, "server.wraithbusters.door.opened");
        return true;
    }

    @Nullable
    private static PlayerSessionState playerState(
        @Nonnull GameSession session,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr == null) {
            return null;
        }
        return session.getPlayers().get(pr.getUuid());
    }

    private static void send(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef, @Nonnull String key) {
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.translation(key));
        }
    }
}
