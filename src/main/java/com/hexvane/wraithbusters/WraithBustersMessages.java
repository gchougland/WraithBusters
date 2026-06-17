package com.hexvane.wraithbusters;

import com.hypixel.hytale.server.core.Message;
import javax.annotation.Nonnull;

/** Keys in {@code Server/Languages/en-US/server.lang} are loaded with a {@code server.} prefix. */
public final class WraithBustersMessages {
    private static final String PREFIX = "server.wraithbusters.";

    private WraithBustersMessages() {}

    @Nonnull
    public static Message translation(@Nonnull String key) {
        return Message.translation(PREFIX + key);
    }

    @Nonnull
    public static String commandDescription(@Nonnull String key) {
        return PREFIX + key;
    }
}
