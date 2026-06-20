package com.hexvane.wraithbusters.team;

import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class TeamAssigner {
    private TeamAssigner() {}

    public static int computeGhostCount(int playerCount, @Nonnull WraithBustersPluginConfig config) {
        if (playerCount <= 0) {
            return 0;
        }
        int humansPerGhost = Math.max(1, config.getHumansPerExtraGhost());
        int maxGhosts = playerCount > 1 ? playerCount - 1 : playerCount;
        int ghosts = Math.min(Math.max(config.getMinGhosts(), 1), maxGhosts);
        // One ghost per N humans (not total players): scale up until humanCount / N <= ghosts.
        while (ghosts < maxGhosts && (playerCount - ghosts) / humansPerGhost > ghosts) {
            ghosts++;
        }
        return ghosts;
    }

    @Nonnull
    public static Map<UUID, Team> assign(@Nonnull List<UUID> players, @Nonnull WraithBustersPluginConfig config) {
        List<UUID> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);
        int ghostCount = computeGhostCount(shuffled.size(), config);
        Map<UUID, Team> result = new HashMap<>();
        for (int i = 0; i < shuffled.size(); i++) {
            result.put(shuffled.get(i), i < ghostCount ? Team.GHOST : Team.HUMAN);
        }
        return result;
    }
}
