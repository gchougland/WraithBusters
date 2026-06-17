package com.hexvane.wraithbusters.ui;

import com.hexvane.wraithbusters.WraithBustersPlugin;
import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.team.Team;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class RoundEndPage extends InteractiveCustomUIPage<RoundEndPage.PageData> {
    @Nonnull
    private final UUID sessionId;
    private boolean templateAppended;

    public RoundEndPage(@Nonnull PlayerRef playerRef, @Nonnull GameSession session) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.sessionId = session.getSessionId();
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("WraithBusters/RoundEndPage.ui");
            templateAppended = true;
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#PlayAgainButton",
                EventData.of("Action", "PlayAgain"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#LeaveButton",
                EventData.of("Action", "Leave"),
                false
            );
        }
        GameSession session = GameRegistry.get().getSession(sessionId);
        if (session != null && session.getWinningTeam() == Team.HUMAN) {
            commandBuilder.set("#RoundEndTitle.TextSpans", Message.translation("server.wraithbusters.end.humansWin"));
        } else {
            commandBuilder.set("#RoundEndTitle.TextSpans", Message.translation("server.wraithbusters.end.ghostsWin"));
        }
        commandBuilder.set("#PlayAgainButton.TextSpans", Message.translation("server.wraithbusters.end.playAgain"));
        commandBuilder.set("#LeaveButton.TextSpans", Message.translation("server.wraithbusters.end.leave"));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        GameSession session = GameRegistry.get().getSession(sessionId);
        WraithBustersPlugin plugin = WraithBustersPlugin.get();
        World world = store.getExternalData().getWorld();
        if (session == null || plugin == null) {
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        if ("PlayAgain".equals(data.action)) {
            player.getPageManager().setPage(ref, store, Page.None);
            plugin.getGameService().playAgain(session, world);
            return;
        }
        player.getPageManager().setPage(ref, store, Page.None);
        plugin.getGameService().leavePlayer(session, ref, store);
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .build();

        @Nullable
        private String action;
    }
}
