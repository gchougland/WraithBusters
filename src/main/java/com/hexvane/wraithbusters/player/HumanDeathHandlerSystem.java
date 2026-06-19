package com.hexvane.wraithbusters.player;

import com.hexvane.wraithbusters.WraithBustersPlugin;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.team.Team;
import com.hexvane.wraithbusters.team.TeamSetupService;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hexvane.wraithbusters.util.WraithBustersVoiceUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public final class HumanDeathHandlerSystem extends DeathSystems.OnDeathSystem {
    @Nonnull
    private final WraithBustersPlugin plugin;

    public HumanDeathHandlerSystem(@Nonnull WraithBustersPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType(), EntityStatMap.getComponentType());
    }

    @Override
    public void onComponentAdded(
        @Nonnull Ref<EntityStore> victimRef,
        @Nonnull DeathComponent death,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        World world = store.getExternalData().getWorld();
        GameSession session = GameRegistry.get().getSessionForWorld(world.getWorldConfig().getUuid());
        if (session == null || session.getPhase() != GamePhase.ACTIVE) {
            return;
        }
        PlayerRef pr = store.getComponent(victimRef, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        var state = session.getOrCreatePlayer(pr.getUuid());
        if (state.getTeam() != Team.HUMAN || !state.isAlive()) {
            return;
        }
        state.setAlive(false);
        state.setRole(PlayerRole.SPECTATOR);
        DeferredWorldTasks.run(world, () -> {
            if (!victimRef.isValid()) {
                return;
            }
            TeamSetupService.applySpectator(victimRef, world.getEntityStore().getStore());
            TeamSetupService.hidePlayerFromSession(session, pr.getUuid());
            TeamSetupService.refreshVisibility(session, world);
            WraithBustersVoiceUtil.unsilence(pr);
        });
        commandBuffer.removeComponent(victimRef, DeathComponent.getComponentType());
    }
}
