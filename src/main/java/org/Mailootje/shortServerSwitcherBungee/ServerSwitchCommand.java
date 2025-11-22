package org.Mailootje.shortServerSwitcherBungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class ServerSwitchCommand extends Command {

    private final ShortServerSwitcherBungee plugin;
    private final ConfigManager.CommandDef def;

    public ServerSwitchCommand(ShortServerSwitcherBungee plugin, String name, ConfigManager.CommandDef def) {
        super(name);
        this.plugin = plugin;
        this.def = def;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ConfigManager cfg = plugin.getConfigManager();

        if (!(sender instanceof ProxiedPlayer)) {
            send(sender, cfg.msg("player-only"));
            return;
        }

        ProxiedPlayer player = (ProxiedPlayer) sender;

        // global permission
        String globalPerm = cfg.getGlobalPermission();
        if (globalPerm != null && !globalPerm.isEmpty() && !player.hasPermission(globalPerm)) {
            send(player, cfg.msg("no-permission"));
            return;
        }

        // per-command permission
        if (def.permission != null && !def.permission.isEmpty() && !player.hasPermission(def.permission)) {
            send(player, cfg.msg("no-permission"));
            return;
        }

        String target = def.server;

        if (ProxyServer.getInstance().getServerInfo(target) == null) {
            send(player, cfg.msg("unknown-server"));
            return;
        }

        send(player, cfg.msg("switching").replace("{server}", target));

        // YOUR required dispatch style:
        // send <player name that ran the command> <server>
        ProxyServer.getInstance().getPluginManager()
                .dispatchCommand(ProxyServer.getInstance().getConsole(),
                        "send " + player.getName() + " " + target);
    }

    private void send(CommandSender sender, String msg) {
        if (msg == null || msg.isEmpty()) return;
        sender.sendMessage(new TextComponent(ChatColor.translateAlternateColorCodes('&', msg)));
    }
}
