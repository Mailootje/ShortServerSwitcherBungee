package org.Mailootje.shortServerSwitcherBungee;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class ProxyJoinListener implements Listener {

    private final ShortServerSwitcherBungee plugin;

    public ProxyJoinListener(ShortServerSwitcherBungee plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PostLoginEvent e) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.autoRouteEnabled) return;

        ProxiedPlayer p = e.getPlayer();
        if (!cfg.autoRouteBypassPerm.isEmpty() && p.hasPermission(cfg.autoRouteBypassPerm)) return;

        if (cfg.autoRouteDefaultCommand == null || cfg.autoRouteDefaultCommand.isEmpty()) return;

        // run their short command (so all checks apply)
        ProxyServer.getInstance().getPluginManager()
                .dispatchCommand(p, cfg.autoRouteDefaultCommand);
    }
}
