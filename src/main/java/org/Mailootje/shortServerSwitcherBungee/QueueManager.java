package org.Mailootje.shortServerSwitcherBungee;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.scheduler.ScheduledTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class QueueManager {

    private final ShortServerSwitcherBungee plugin;
    private ScheduledTask task;

    // key -> queue entries
    private final Map<String, Deque<Entry>> queues = new ConcurrentHashMap<String, Deque<Entry>>();

    static class Entry {
        UUID uuid;
        String key;
        List<String> candidates;
        long joinAt;
        Entry(ProxiedPlayer p, String key, List<String> candidates) {
            this.uuid = p.getUniqueId();
            this.key = key;
            this.candidates = candidates;
            this.joinAt = System.currentTimeMillis();
        }
    }

    public QueueManager(ShortServerSwitcherBungee plugin) {
        this.plugin = plugin;
    }

    public void start() {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.queueEnabled) return;

        task = ProxyServer.getInstance().getScheduler().schedule(
                plugin,
                new Runnable() {
                    @Override
                    public void run() {
                        tick();
                    }
                },
                cfg.queueCheckInterval,
                cfg.queueCheckInterval,
                TimeUnit.SECONDS
        );
    }

    public void stop() {
        if (task != null) task.cancel();
        queues.clear();
    }

    public void enqueue(ProxiedPlayer player, String key, List<String> candidates) {
        final ConfigManager cfg = plugin.getConfigManager();
        Deque<Entry> q = queues.get(key.toLowerCase());
        if (q == null) {
            q = new ArrayDeque<Entry>();
            queues.put(key.toLowerCase(), q);
        }

        // don't double-queue
        for (Entry e : q) {
            if (e.uuid.equals(player.getUniqueId())) {
                return;
            }
        }

        q.addLast(new Entry(player, key, candidates));
        int pos = q.size();

        player.sendMessage(TextUtil.tc(
                cfg.msg("queued")
                        .replace("{server}", candidates.get(0))
                        .replace("{pos}", String.valueOf(pos))
        ));
    }

    public boolean leaveQueue(ProxiedPlayer player) {
        UUID id = player.getUniqueId();
        for (Deque<Entry> q : queues.values()) {
            Iterator<Entry> it = q.iterator();
            while (it.hasNext()) {
                if (it.next().uuid.equals(id)) {
                    it.remove();
                    return true;
                }
            }
        }
        return false;
    }

    private void tick() {
        final ConfigManager cfg = plugin.getConfigManager();
        long now = System.currentTimeMillis();

        for (Map.Entry<String, Deque<Entry>> mapEntry : queues.entrySet()) {
            final Deque<Entry> q = mapEntry.getValue();
            if (q.isEmpty()) continue;

            final Entry head = q.peekFirst();
            final ProxiedPlayer player = ProxyServer.getInstance().getPlayer(head.uuid);
            if (player == null) {
                q.pollFirst();
                continue;
            }

            // timeout
            long waited = (now - head.joinAt) / 1000L;
            if (waited > cfg.queueMaxWait) {
                q.pollFirst();
                player.sendMessage(TextUtil.tc(
                        cfg.msg("queue-timeout").replace("{server}", head.candidates.get(0))
                ));
                continue;
            }

            // try connect to first available candidate
            tryCandidates(player, head.candidates, 0, new Runnable() {
                @Override
                public void run() {
                    q.pollFirst(); // success -> pop head
                }
            });
        }
    }

    private void tryCandidates(final ProxiedPlayer player,
                               final List<String> candidates,
                               final int index,
                               final Runnable onSuccess) {

        if (index >= candidates.size()) return;

        final String target = candidates.get(index);
        final ServerInfo info = ProxyServer.getInstance().getServerInfo(target);

        if (info == null) {
            tryCandidates(player, candidates, index + 1, onSuccess);
            return;
        }

        info.ping(new net.md_5.bungee.api.Callback<ServerPing>() {
            @Override
            public void done(ServerPing ping, Throwable err) {
                if (err != null || ping == null || ping.getPlayers() == null) {
                    tryCandidates(player, candidates, index + 1, onSuccess);
                    return;
                }

                int online = ping.getPlayers().getOnline();
                int max = ping.getPlayers().getMax();

                if (online >= max) {
                    tryCandidates(player, candidates, index + 1, onSuccess);
                    return;
                }

                player.sendMessage(TextUtil.tc(
                        plugin.getConfigManager().msg("switching").replace("{server}", target)
                ));
                player.connect(info);
                onSuccess.run();
            }
        });
    }
}
