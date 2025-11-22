package org.Mailootje.shortServerSwitcherBungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.List;
import java.util.UUID;

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
            send(sender, cfg.msg("player-only"));
            return;
        }
        final ProxiedPlayer player = (ProxiedPlayer) sender;
        final UUID uuid = player.getUniqueId();

        // global perm
        final String globalPerm = cfg.getGlobalPermission();
        if (!globalPerm.isEmpty() && !player.hasPermission(globalPerm)) {
            send(player, cfg.msg("no-permission"));
            return;
        }

        // per-command perm (auto generated if empty)
        if (def.permission != null && !def.permission.isEmpty() && !player.hasPermission(def.permission)) {
            send(player, cfg.msg("no-permission"));
            return;
        }

        final String key = def.key;
        final String baseServer = def.server;

        // already on server?
        if (player.getServer() != null &&
                player.getServer().getInfo().getName().equalsIgnoreCase(baseServer)) {
            send(player, cfg.msg("already-there").replace("{server}", baseServer));
            return;
        }

        // rate limit
        if (cfg.rateEnabled && (cfg.rateBypassPerm.isEmpty() || !player.hasPermission(cfg.rateBypassPerm))) {
            RateLimiter lim = plugin.limiter(uuid);
            if (lim.isBlocked(cfg.rateBlockSeconds)) {
                int left = lim.blockSecondsLeft(cfg.rateBlockSeconds);
                send(player, cfg.msg("ratelimited").replace("{seconds}", String.valueOf(left)));
                return;
            }
            if (!lim.tryHit(cfg.rateMax, cfg.ratePerSeconds, cfg.rateBlockSeconds)) {
                int left = lim.blockSecondsLeft(cfg.rateBlockSeconds);
                send(player, cfg.msg("ratelimited").replace("{seconds}", String.valueOf(left)));
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
                    send(player, cfg.msg("ratelimited").replace("{seconds}", String.valueOf(left)));
                    return;
                }
                plugin.setLastUse(uuid, key, now);
            }
        }

        // try failover list
        List<String> candidates = cfg.failoverFor(key, baseServer);
        tryCandidate(player, candidates, 0, key);
    }

    private void tryCandidate(final ProxiedPlayer player, final List<String> candidates, final int index, final String key) {
        final ConfigManager cfg = plugin.getConfigManager();

        if (index >= candidates.size()) {
            // all full/offline
            if (cfg.queueEnabled) {
                plugin.getQueueManager().enqueue(player, key, candidates);
            } else {
                send(player, cfg.msg("full").replace("{server}", candidates.get(0)));
            }
            return;
        }

        final String target = candidates.get(index);
        final ServerInfo info = ProxyServer.getInstance().getServerInfo(target);

        if (info == null) {
            tryCandidate(player, candidates, index + 1, key);
            return;
        }

        // check fullness via ping
        info.ping(new net.md_5.bungee.api.Callback<ServerPing>() {
            @Override
            public void done(ServerPing ping, Throwable err) {
                if (err != null || ping == null || ping.getPlayers() == null) {
                    // can't ping -> treat as unavailable, try next
                    tryCandidate(player, candidates, index + 1, key);
                    return;
                }

                int online = ping.getPlayers().getOnline();
                int max = ping.getPlayers().getMax();

                boolean full = cfg.fullEnabled && online >= max &&
                        (cfg.fullBypassPerm.isEmpty() || !player.hasPermission(cfg.fullBypassPerm));

                if (full) {
                    send(player, cfg.msg("full").replace("{server}", target));
                    tryCandidate(player, candidates, index + 1, key);
                    return;
                }

                send(player, cfg.msg("switching").replace("{server}", target));
                player.connect(info);
            }
        });
    }

    private void send(CommandSender sender, String msg) {
        if (msg == null || msg.isEmpty()) return;
        sender.sendMessage(new TextComponent(ChatColor.translateAlternateColorCodes('&', msg)));
    }
}
