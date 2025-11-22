package org.Mailootje.shortServerSwitcherBungee;

import net.md_5.bungee.api.*;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.*;

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
        final ConfigManager cfg = plugin.getConfigManager();

        if (!(sender instanceof ProxiedPlayer)) {
            send(sender, plugin.getLangManager().msg(sender, "player-only"));
            return;
        }

        final ProxiedPlayer player = (ProxiedPlayer) sender;
        final UUID uuid = player.getUniqueId();
        final String key = def.key;
        final String baseServer = def.server;

        // global perm
        final String globalPerm = cfg.getGlobalPermission();
        if (!globalPerm.isEmpty() && !player.hasPermission(globalPerm)) {
            send(player, plugin.getLangManager().msg(player, "no-permission"));
            return;
        }

        // per-command perm
        if (def.permission != null && !def.permission.isEmpty() && !player.hasPermission(def.permission)) {
            send(player, plugin.getLangManager().msg(player, "no-permission"));
            return;
        }

        // (6) maintenance / enabled
        if (!def.enabled) {
            send(player, plugin.getLangManager().msg(player, "maintenance")
                    .replace("{server}", baseServer));
            return;
        }

        // (6) allow-permission gate
        if (def.allowPermission != null && !def.allowPermission.isEmpty()
                && !player.hasPermission(def.allowPermission)) {
            send(player, plugin.getLangManager().msg(player, "no-permission"));
            return;
        }

        // already there?
        if (player.getServer() != null &&
                player.getServer().getInfo().getName().equalsIgnoreCase(baseServer)) {
            send(player, plugin.getLangManager().msg(player, "already-there")
                    .replace("{server}", baseServer));
            return;
        }

        // rate limit
        if (cfg.rateEnabled && (cfg.rateBypassPerm.isEmpty() || !player.hasPermission(cfg.rateBypassPerm))) {
            RateLimiter lim = plugin.limiter(uuid);
            if (lim.isBlocked(cfg.rateBlockSeconds) ||
                    !lim.tryHit(cfg.rateMax, cfg.ratePerSeconds, cfg.rateBlockSeconds)) {

                int left = lim.blockSecondsLeft(cfg.rateBlockSeconds);
                send(player, plugin.getLangManager().msg(player, "ratelimited")
                        .replace("{seconds}", String.valueOf(left)));
                return;
            }
        }

        // cooldown
        if (cfg.cooldownEnabled && (cfg.cooldownBypassPerm.isEmpty() || !player.hasPermission(cfg.cooldownBypassPerm))) {
            int cd = cfg.cooldownFor(key);
            if (cd > 0) {
                long last = plugin.getLastUse(uuid, key);
                long now = System.currentTimeMillis();
                long diff = now - last;
                if (diff < cd * 1000L) {
                    long left = (cd * 1000L - diff) / 1000L;
                    send(player, plugin.getLangManager().msg(player, "ratelimited")
                            .replace("{seconds}", String.valueOf(left)));
                    return;
                }
                plugin.setLastUse(uuid, key, now);
            }
        }

        // (7) confirm click for selected keys
        if (cfg.confirmEnabled && cfg.confirmCommands.contains(key.toLowerCase())) {
            if (!plugin.getConfirmManager().hasPending(player, key)) {
                plugin.getConfirmManager().askConfirm(player, key, baseServer, def.motd);
                return;
            }
            // if pending, the click will re-run command and pass through
            plugin.getConfirmManager().clearPending(player, key);
        }

        // build candidates
        final List<String> candidates;
        if (cfg.smartSelectEnabled) {
            candidates = cfg.smartFor(key, baseServer);
            plugin.getQueueManager().connectSmart(player, key, candidates, def.motd);
        } else {
            candidates = cfg.failoverFor(key, baseServer);
            plugin.getQueueManager().connectFailover(player, key, candidates, def.motd);
        }
    }

    private void send(CommandSender sender, String msg) {
        if (msg == null || msg.isEmpty()) return;
        sender.sendMessage(new TextComponent(ChatColor.translateAlternateColorCodes('&', msg)));
    }
}
