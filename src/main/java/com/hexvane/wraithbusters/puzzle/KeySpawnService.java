package com.hexvane.wraithbusters.puzzle;

import com.hexvane.wraithbusters.arena.RoomDefinition;
import com.hexvane.wraithbusters.door.DoorKeySymbols;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.team.Team;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3i;

public final class KeySpawnService {
    private static final ConcurrentHashMap<UUID, SessionKeys> SESSIONS = new ConcurrentHashMap<>();

    private KeySpawnService() {}

    public static void startRound(@Nonnull GameSession session, @Nonnull World world) {
        clearSession(session.getSessionId(), world);
    }

    public static void endRound(@Nonnull GameSession session, @Nonnull World world) {
        clearSession(session.getSessionId(), world);
    }

    public static void clearForLobby(@Nonnull GameSession session, @Nonnull World world) {
        clearSession(session.getSessionId(), world);
    }

    public static void spawnKeyForRoom(@Nonnull GameSession session, @Nonnull World world, @Nonnull RoomDefinition room) {
        String symbol = resolveSpawnSymbol(session, room);
        broadcastKeyFound(session, symbol);
        DeferredWorldTasks.run(world, () -> spawnKeyEntity(session, world, room, symbol));
    }

    @Nonnull
    private static String resolveSpawnSymbol(@Nonnull GameSession session, @Nonnull RoomDefinition room) {
        int nextIndex = session.getCurrentRoomIndex();
        if (session.isAtticUnlocked() || nextIndex >= session.getActiveRoomChain().size()) {
            return DoorKeySymbols.ATTIC_SYMBOL;
        }
        if (nextIndex < session.getActiveRoomChain().size()) {
            String nextRoomId = session.getActiveRoomChain().get(nextIndex);
            return session.getArenaLayout().getRooms().stream()
                .filter(r -> r.getRoomId().equals(nextRoomId))
                .map(RoomDefinition::getSymbolId)
                .map(DoorKeySymbols::normalizeSymbolId)
                .findFirst()
                .orElse(DoorKeySymbols.normalizeSymbolId(room.getSymbolId()));
        }
        return DoorKeySymbols.normalizeSymbolId(room.getSymbolId());
    }

    private static void broadcastKeyFound(@Nonnull GameSession session, @Nonnull String symbol) {
        String displaySymbol = DoorKeySymbols.displayName(symbol);
        for (UUID playerUuid : session.playerUuidList()) {
            if (session.getOrCreatePlayer(playerUuid).getTeam() != Team.HUMAN) {
                continue;
            }
            PlayerRef pr = Universe.get().getPlayer(playerUuid);
            if (pr != null) {
                pr.sendMessage(
                    Message.translation("server.wraithbusters.key.found").param("symbol", displaySymbol)
                );
            }
            if (DoorKeySymbols.ATTIC_SYMBOL.equals(symbol)) {
                session.getOrCreatePlayer(playerUuid).setHasAtticKey(true);
            }
        }
    }

    private static void spawnKeyEntity(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull RoomDefinition room,
        @Nonnull String symbol
    ) {
        if (!DeferredWorldTasks.isStoreOpen(world)) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        SessionKeys keys = SESSIONS.computeIfAbsent(session.getSessionId(), ignored -> new SessionKeys());
        despawnTrackedKeys(store, keys);

        String itemId = DoorKeySymbols.itemIdForSymbol(symbol);
        ItemStack stack = new ItemStack(itemId, 1);
        if (!stack.isValid()) {
            return;
        }

        Vector3i blockPos = room.getKeySpawn();
        Vector3d position = new Vector3d(blockPos.x + 0.5, blockPos.y + 0.35, blockPos.z + 0.5);
        Holder<EntityStore> holder = ItemComponent.generateItemDrop(
            store, stack, position, Rotation3f.ZERO, 0.0F, 0.0F, 0.0F
        );
        if (holder == null) {
            return;
        }
        ItemComponent itemComponent = holder.getComponent(ItemComponent.getComponentType());
        if (itemComponent != null) {
            itemComponent.setPickupDelay(0.0F);
        }
        Ref<EntityStore> entityRef = store.addEntity(holder, AddReason.SPAWN);
        if (entityRef != null) {
            keys.activeRefs.add(entityRef);
        }
    }

    private static void despawnTrackedKeys(@Nonnull Store<EntityStore> store, @Nonnull SessionKeys keys) {
        for (Ref<EntityStore> ref : keys.activeRefs) {
            if (ref != null && ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
        }
        keys.activeRefs.clear();
    }

    private static void clearSession(@Nonnull UUID sessionId, @Nonnull World world) {
        SessionKeys keys = SESSIONS.remove(sessionId);
        if (keys == null || !DeferredWorldTasks.isStoreOpen(world)) {
            return;
        }
        despawnTrackedKeys(world.getEntityStore().getStore(), keys);
    }

    private static final class SessionKeys {
        private final Set<Ref<EntityStore>> activeRefs = new ReferenceOpenHashSet<>();
    }
}
