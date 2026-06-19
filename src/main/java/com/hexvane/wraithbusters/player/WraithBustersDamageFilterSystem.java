package com.hexvane.wraithbusters.player;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.WraithBustersPlugin;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Ghosts are invulnerable and cannot punch humans; plate projectiles still damage humans. */
public final class WraithBustersDamageFilterSystem extends DamageEventSystem {
    @Nonnull
    private final Query<EntityStore> query = Query.and(Player.getComponentType(), PlayerRef.getComponentType());

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage
    ) {
        GameSession session = GameRegistry.get().getSessionForWorld(store.getExternalData().getWorld().getWorldConfig().getUuid());
        if (session == null || session.getPhase() != GamePhase.ACTIVE) {
            return;
        }
        PlayerRef victimRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
        if (victimRef == null) {
            return;
        }
        PlayerSessionState victimState = session.getPlayers().get(victimRef.getUuid());
        if (victimState != null && victimState.getRole() == PlayerRole.GHOST) {
            damage.setCancelled(true);
            return;
        }
        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return;
        }
        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (!attackerRef.isValid()) {
            return;
        }
        NPCEntity attackerNpc = store.getComponent(attackerRef, NPCEntity.getComponentType());
        if (attackerNpc != null
            && WraithBustersConstants.POSSESSABLE_SNAPDRAGON_NPC_ROLE.equals(attackerNpc.getRoleName())
            && victimState != null
            && victimState.getRole() == PlayerRole.HUMAN
            && victimState.isAlive()) {
            WraithBustersPlugin plugin = WraithBustersPlugin.get();
            float snapdragonDamage = plugin == null ? 12f : plugin.getPluginConfig().getBushSnapdragonDamage();
            damage.setAmount(Math.min(damage.getAmount(), snapdragonDamage));
            return;
        }
        PlayerRef attackerPlayer = store.getComponent(attackerRef, PlayerRef.getComponentType());
        if (attackerPlayer == null) {
            return;
        }
        PlayerSessionState attackerState = session.getPlayers().get(attackerPlayer.getUuid());
        if (attackerState == null || attackerState.getRole() != PlayerRole.GHOST) {
            return;
        }
        if (victimState != null && victimState.getRole() == PlayerRole.HUMAN && victimState.isAlive()) {
            WraithBustersPlugin plugin = WraithBustersPlugin.get();
            float plateDamage = plugin == null ? 25f : plugin.getPluginConfig().getPlateDamage();
            if (damage.getAmount() < plateDamage * 0.75f) {
                damage.setCancelled(true);
            }
        }
    }
}
