package org.Mailootje.shortServerSwitcherBungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class LeaveQueueCommand extends Command {

    private final ShortServerSwitcherBungee plugin;

    public LeaveQueueCommand(ShortServerSwitcherBungee plugin) {
        super(plugin.getConfigManager().queueLeaveCommand);
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) return;
        ProxiedPlayer p = (ProxiedPlayer) sender;

        boolean left = plugin.getQueueManager().leaveQueue(p);
        if (left) {
            p.sendMessage(TextUtil.tc(plugin.getConfigManager().msg("queue-left")));
        }
    }
}

