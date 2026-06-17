package com.hexvane.wraithbusters.command;

import com.hexvane.wraithbusters.WraithBustersMessages;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public final class WbCommand extends AbstractCommandCollection {
    public WbCommand() {
        super("wb", WraithBustersMessages.commandDescription("commands.root"));
        WraithBustersCommand.registerSubCommands(this);
    }
}
