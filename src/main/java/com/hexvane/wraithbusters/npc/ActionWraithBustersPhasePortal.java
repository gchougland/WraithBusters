package com.hexvane.wraithbusters.npc;

import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.ghost.PhasePortalService;
import com.hexvane.wraithbusters.setup.SetupModeService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;

public final class ActionWraithBustersPhasePortal extends ActionBase {
    public ActionWraithBustersPhasePortal(
        @Nonnull BuilderActionWraithBustersPhasePortal builder,
        @Nonnull BuilderSupport support
    ) {
        super(builder);
    }

    @Override
    public boolean canExecute(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Role role,
        InfoProvider sensorInfo,
        double dt,
        @Nonnull Store<EntityStore> store
    ) {
        return super.canExecute(ref, role, sensorInfo, dt, store)
            && role.getStateSupport().getInteractionIterationTarget() != null;
    }

    @Override
    public boolean execute(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Role role,
        InfoProvider sensorInfo,
        double dt,
        @Nonnull Store<EntityStore> store
    ) {
        super.execute(ref, role, sensorInfo, dt, store);
        Ref<EntityStore> playerRef = role.getStateSupport().getInteractionIterationTarget();
        if (playerRef == null) {
            return false;
        }
        PlayerRef playerRefComponent = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComponent == null) {
            return false;
        }
        World world = store.getExternalData().getWorld();
        GameSession session = GameRegistry.get().resolveSessionForWorld(world);
        if (session == null && !SetupModeService.isActive(playerRefComponent.getUuid())) {
            return false;
        }
        return PhasePortalService.tryUse(session, world, playerRef, store, ref);
    }
}
