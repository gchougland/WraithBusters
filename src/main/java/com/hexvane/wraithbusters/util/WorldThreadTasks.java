package com.hexvane.wraithbusters.util;

import com.hypixel.hytale.server.core.universe.world.World;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;

/** Runs work on a world's thread and drains its deferred task queue. */
public final class WorldThreadTasks {
    private static final long DRAIN_TIMEOUT_MS = 10_000L;

    private WorldThreadTasks() {}

    public static void runOnWorldThread(@Nonnull World world, @Nonnull Runnable task) {
        if (!world.isAlive()) {
            return;
        }
        if (world.isInThread()) {
            task.run();
            return;
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        try {
            world.execute(() -> {
                try {
                    task.run();
                } finally {
                    done.complete(null);
                }
            });
            done.get(DRAIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | RuntimeException ignored) {
            // World stopped accepting tasks or shut down mid-drain.
        }
    }

    /**
     * Runs every task already queued on the world thread (including vanilla portal cleanup)
     * before returning, while chunk/entity stores are still open.
     */
    public static void drainQueue(@Nonnull World world) {
        runOnWorldThread(world, () -> {});
    }
}
