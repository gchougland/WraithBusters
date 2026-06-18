package com.hexvane.wraithbusters.puzzle;

import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.team.Team;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Lets living humans pick up round items (keys, cheese, etc.) while ghosts cannot. */
public final class HumanKeyPickupSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    private final ResourceType<EntityStore, SpatialResource<Ref<EntityStore>, EntityStore>> itemSpatialResource;
    @Nonnull
    private final Query<EntityStore> query;

    public HumanKeyPickupSystem() {
        this.itemSpatialResource = EntityModule.get().getItemSpatialResourceType();
        this.query = Query.and(
            Player.getComponentType(),
            PlayerRef.getComponentType(),
            TransformComponent.getComponentType(),
            Query.not(DeathComponent.getComponentType())
        );
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        PlayerRef pr = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        GameSession session = GameRegistry.get().getSessionForWorld(store.getExternalData().getWorld().getWorldConfig().getUuid());
        if (session == null || session.getPhase() != GamePhase.ACTIVE) {
            return;
        }
        var playerState = session.getOrCreatePlayer(pr.getUuid());
        if (playerState.getTeam() != Team.HUMAN || playerState.getRole() != PlayerRole.HUMAN || !playerState.isAlive()) {
            return;
        }

        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }
        Vector3d position = transform.getPosition();
        SpatialResource<Ref<EntityStore>, EntityStore> itemSpatial = store.getResource(itemSpatialResource);
        List<Ref<EntityStore>> nearbyItems = SpatialResource.getThreadLocalReferenceList();
        itemSpatial.getSpatialStructure().ordered(position, 2.0, nearbyItems);

        for (Ref<EntityStore> itemRef : nearbyItems) {
            if (!itemRef.isValid()) {
                continue;
            }
            if (!store.getArchetype(itemRef).contains(PreventPickup.getComponentType())) {
                continue;
            }
            ItemComponent itemComponent = store.getComponent(itemRef, ItemComponent.getComponentType());
            if (itemComponent == null || itemComponent.getItemStack() == null) {
                continue;
            }
            TransformComponent itemTransform = store.getComponent(itemRef, TransformComponent.getComponentType());
            if (itemTransform == null) {
                continue;
            }
            float pickupRadius = itemComponent.getPickupRadius(commandBuffer);
            if (position.distance(itemTransform.getPosition()) > pickupRadius) {
                continue;
            }
            ItemStack stack = itemComponent.getItemStack();
            var transaction = Player.giveItem(stack, playerRef, commandBuffer);
            if (ItemStack.isEmpty(transaction.getRemainder())) {
                itemComponent.setRemovedByPlayerPickup(true);
                commandBuffer.removeEntity(itemRef, RemoveReason.REMOVE);
                Player.notifyPickupItem(playerRef, stack, itemTransform.getPosition(), commandBuffer);
                break;
            }
        }
    }
}
