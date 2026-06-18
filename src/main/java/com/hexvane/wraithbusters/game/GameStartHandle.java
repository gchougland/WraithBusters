package com.hexvane.wraithbusters.game;

import com.hypixel.hytale.server.core.universe.world.World;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

/** Session plus in-flight instance world creation for {@code /wb start}. */
public record GameStartHandle(
    @Nonnull GameSession session,
    @Nonnull CompletableFuture<World> worldFuture
) {}
