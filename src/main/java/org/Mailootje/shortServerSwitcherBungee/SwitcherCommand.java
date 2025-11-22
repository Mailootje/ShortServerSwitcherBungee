package org.Mailootje.shortServerSwitcherBungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;

public class SwitcherCommand extends Command {

    private final ShortServerSwitcherBungee plugin;

    public SwitcherCommand(ShortServerSwitcherBungee plugin) {
        super("switcher", null, "switcherreload", "switcherhelp");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ConfigManager cfg = plugin.getConfigManager();

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            for (String line : cfg.getHelpLines()) {
                send(sender, line);
            }
            return;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPlugin();
            send(sender, cfg.msg("reloaded"));
            return;
        }

        // unknown subcommand -> help
        for (String line : cfg.getHelpLines()) {
            send(sender, line);
        }
    }

    private void send(CommandSender sender, String msg) {
        if (msg == null || msg.isEmpty()) return;
        sender.sendMessage(new TextComponent(ChatColor.translateAlternateColorCodes('&', msg)));
    }
}
