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
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
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
            World world = store.getExternalData().getWorld();
            Ref<EntityStore> victimEntityRef = archetypeChunk.getReferenceTo(index);
            int poisonDurationSeconds = plugin == null
                ? 12
                : plugin.getPluginConfig().getBushSnapdragonPoisonDurationSeconds();
            DeferredWorldTasks.run(world, () -> applySnapdragonPoison(world, victimEntityRef, poisonDurationSeconds));
            return;
        }
        if (attackerNpc != null
            && WraithBustersConstants.POSSESSABLE_FOOD_TORNADO_NPC_ROLE.equals(attackerNpc.getRoleName())
            && victimState != null
            && victimState.getRole() == PlayerRole.HUMAN
            && victimState.isAlive()) {
            WraithBustersPlugin plugin = WraithBustersPlugin.get();
            float cornDamage = plugin == null ? 3f : plugin.getPluginConfig().getBarrelCornDamage();
            damage.setAmount(Math.min(damage.getAmount(), cornDamage));
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
            if (allowGhostPossessableProjectileDamage(damage, WraithBustersPlugin.get())) {
                return;
            }
            damage.setCancelled(true);
        }
    }

    /**
     * Ghost melee is blocked; possessable projectile hits are allowed and clamped to config.
     * Match on {@link Damage#getInitialAmount()} so armor reduction does not fail the filter.
     */
    private static boolean allowGhostPossessableProjectileDamage(@Nonnull Damage damage, @Nullable WraithBustersPlugin plugin) {
        float amount = damage.getInitialAmount();
        float plateDamage = plugin == null ? WraithBustersConstants.PLATE_PROJECTILE_ASSET_DAMAGE : plugin.getPluginConfig().getPlateDamage();
        float featherDamage = plugin == null ? WraithBustersConstants.FEATHER_PROJECTILE_ASSET_DAMAGE : plugin.getPluginConfig().getWatcherFeatherDamage();
        float cornDamage = plugin == null ? WraithBustersConstants.CORN_PROJECTILE_ASSET_DAMAGE : plugin.getPluginConfig().getBarrelCornDamage();

        if (isPlateProjectileHit(amount, plateDamage)) {
            damage.setAmount(Math.min(damage.getAmount(), plateDamage));
            return true;
        }
        if (isFeatherProjectileHit(amount, featherDamage)) {
            damage.setAmount(Math.min(damage.getAmount(), featherDamage));
            return true;
        }
        if (isCornProjectileHit(amount, cornDamage)) {
            damage.setAmount(Math.min(damage.getAmount(), cornDamage));
            return true;
        }
        return false;
    }

    private static boolean isPlateProjectileHit(float amount, float configDamage) {
        return amount >= configDamage * 0.75f || amount >= WraithBustersConstants.PLATE_PROJECTILE_ASSET_DAMAGE * 0.75f;
    }

    private static boolean isFeatherProjectileHit(float amount, float configDamage) {
        return withinDamageBand(amount, configDamage)
            || withinDamageBand(amount, WraithBustersConstants.FEATHER_PROJECTILE_ASSET_DAMAGE);
    }

    private static boolean isCornProjectileHit(float amount, float configDamage) {
        return withinDamageBand(amount, configDamage)
            || withinDamageBand(amount, WraithBustersConstants.CORN_PROJECTILE_ASSET_DAMAGE);
    }

    private static boolean withinDamageBand(float amount, float expected) {
        return amount >= expected * 0.75f && amount <= expected * 1.25f;
    }

    private static void applySnapdragonPoison(
        @Nonnull World world,
        @Nonnull Ref<EntityStore> humanRef,
        int durationSeconds
    ) {
        if (!DeferredWorldTasks.isStoreOpen(world) || !humanRef.isValid()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        EffectControllerComponent effectController = store.getComponent(humanRef, EffectControllerComponent.getComponentType());
        if (effectController == null) {
            return;
        }
        EntityEffect vanillaPoison = EntityEffect.getAssetMap().getAsset(WraithBustersConstants.VANILLA_SNAPDRAGON_POISON_EFFECT_ID);
        if (vanillaPoison != null && effectController.hasEffect(vanillaPoison)) {
            int vanillaIndex = EntityEffect.getAssetMap().getIndex(vanillaPoison.getId());
            if (vanillaIndex != Integer.MIN_VALUE) {
                effectController.removeEffect(humanRef, vanillaIndex, store);
            }
        }
        EntityEffect possessablePoison = EntityEffect.getAssetMap().getAsset(WraithBustersConstants.SNAPDRAGON_POISON_EFFECT_ID);
        if (possessablePoison != null) {
            effectController.addEffect(humanRef, possessablePoison, durationSeconds, OverlapBehavior.OVERWRITE, store);
        }
    }
}
