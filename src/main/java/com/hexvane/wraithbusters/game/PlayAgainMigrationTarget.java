package com.hexvane.wraithbusters.game;

import com.hypixel.hytale.server.core.universe.world.World;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

/** Shared lobby destination for moving multiple players after a round ends. */
record PlayAgainMigrationTarget(
    @Nonnull GameSession session,
    @Nonnull CompletableFuture<World> worldFuture
) {}
