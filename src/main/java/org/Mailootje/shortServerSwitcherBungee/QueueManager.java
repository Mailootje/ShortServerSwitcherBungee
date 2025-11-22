package org.Mailootje.shortServerSwitcherBungee;

import net.md_5.bungee.api.*;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.scheduler.ScheduledTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class QueueManager {

    private final ShortServerSwitcherBungee plugin;
    private ScheduledTask tickTask;
    private ScheduledTask statusTask;

    // key -> queue
    private final Map<String, Deque<Entry>> queues = new ConcurrentHashMap<String, Deque<Entry>>();

    // grace storage: uuid -> lastEntry
    private final Map<UUID, Entry> grace = new ConcurrentHashMap<UUID, Entry>();

    static class Entry {
        UUID uuid;
        String key;
        List<String> candidates;
        long joinAt;
        int priority;
        String motd;

        Entry(ProxiedPlayer p, String key, List<String> candidates, int priority, String motd) {
            this.uuid = p.getUniqueId();
            this.key = key;
            this.candidates = candidates;
            this.priority = priority;
            this.joinAt = System.currentTimeMillis();
            this.motd = motd == null ? "" : motd;
        }
    }

    public QueueManager(ShortServerSwitcherBungee plugin) {
        this.plugin = plugin;
    }

    public void start() {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.queueEnabled) return;

        tickTask = ProxyServer.getInstance().getScheduler().schedule(
                plugin, new Runnable() {
                    @Override public void run() { tick(); }
                },
                cfg.queueCheckInterval, cfg.queueCheckInterval, TimeUnit.SECONDS);

        if (cfg.queueStatusEnabled) {
            statusTask = ProxyServer.getInstance().getScheduler().schedule(
                    plugin, new Runnable() {
                        @Override public void run() { statusTick(); }
                    },
                    cfg.queueStatusInterval, cfg.queueStatusInterval, TimeUnit.SECONDS);
        }
    }

    public void stop() {
        if (tickTask != null) tickTask.cancel();
        if (statusTask != null) statusTask.cancel();
        queues.clear();
        grace.clear();
    }

    // ===== connect entry points =====

    public void connectFailover(ProxiedPlayer player, String key, List<String> candidates, String motd) {
        tryCandidate(player, candidates, 0, key, motd, false);
    }

    public void connectSmart(final ProxiedPlayer player, final String key, final List<String> candidates, final String motd) {
        // ping all candidates and pick least players
        final Map<String, Integer> onlineMap = new ConcurrentHashMap<String, Integer>();
        final int total = candidates.size();

        for (final String c : candidates) {
            final ServerInfo info = ProxyServer.getInstance().getServerInfo(c);
            if (info == null) {
                onlineMap.put(c, Integer.MAX_VALUE);
                continue;
            }
            info.ping(new Callback<ServerPing>() {
                @Override public void done(ServerPing ping, Throwable err) {
                    if (err != null || ping == null || ping.getPlayers() == null) {
                        onlineMap.put(c, Integer.MAX_VALUE);
                    } else {
                        onlineMap.put(c, ping.getPlayers().getOnline());
                    }

                    if (onlineMap.size() >= total) {
                        // sort by least online
                        List<String> sorted = new ArrayList<String>(candidates);
                        Collections.sort(sorted, new Comparator<String>() {
                            @Override public int compare(String a, String b) {
                                return Integer.compare(onlineMap.get(a), onlineMap.get(b));
                            }
                        });
                        tryCandidate(player, sorted, 0, key, motd, true);
                    }
                }
            });
        }
    }

    // ===== queue ops =====

    public void enqueue(ProxiedPlayer player, String key, List<String> candidates, String motd) {
        final ConfigManager cfg = plugin.getConfigManager();
        final String qKey = key.toLowerCase();
        Deque<Entry> q = queues.get(qKey);
        if (q == null) {
            q = new ArrayDeque<Entry>();
            queues.put(qKey, q);
        }

        // restore grace spot if exists
        Entry g = grace.remove(player.getUniqueId());
        if (g != null && g.key.equalsIgnoreCase(qKey)) {
            q.addFirst(g);
        }

        // no double-queue
        for (Entry e : q) {
            if (e.uuid.equals(player.getUniqueId())) return;
        }

        int prio = priorityFor(player);
        Entry entry = new Entry(player, qKey, candidates, prio, motd);

        // (1) priority insert
        insertByPriority(q, entry);

        int pos = positionOf(q, player.getUniqueId());
        player.sendMessage(TextUtil.tc(
                plugin.getLangManager().msg(player, "queued")
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

    // ===== internals =====

    private void tick() {
        final ConfigManager cfg = plugin.getConfigManager();
        long now = System.currentTimeMillis();

        for (Map.Entry<String, Deque<Entry>> mapEntry : queues.entrySet()) {
            final Deque<Entry> q = mapEntry.getValue();
            if (q.isEmpty()) continue;

            final Entry head = q.peekFirst();
            final ProxiedPlayer player = ProxyServer.getInstance().getPlayer(head.uuid);

            if (player == null) {
                // (14) grace if disconnected
                q.pollFirst();
                if (cfg.queueGraceSeconds > 0) {
                    grace.put(head.uuid, head);
                    ProxyServer.getInstance().getScheduler().schedule(
                            plugin, new Runnable() {
                                @Override public void run() { grace.remove(head.uuid); }
                            },
                            cfg.queueGraceSeconds, TimeUnit.SECONDS);
                }
                continue;
            }

            long waited = (now - head.joinAt) / 1000L;
            if (waited > cfg.queueMaxWait) {
                q.pollFirst();
                player.sendMessage(TextUtil.tc(
                        plugin.getLangManager().msg(player, "queue-timeout")
                                .replace("{server}", head.candidates.get(0))
                ));
                continue;
            }

            tryCandidate(player, head.candidates, 0, head.key, head.motd, false);
        }
    }

    private void statusTick() {
        final ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.queueStatusEnabled) return;

        for (Map.Entry<String, Deque<Entry>> me : queues.entrySet()) {
            Deque<Entry> q = me.getValue();
            if (q.isEmpty()) continue;

            int i = 0;
            for (Entry e : q) {
                i++;
                ProxiedPlayer p = ProxyServer.getInstance().getPlayer(e.uuid);
                if (p == null) continue;

                String msg = cfg.queueStatusActionbar
                        .replace("{server}", e.candidates.get(0))
                        .replace("{pos}", String.valueOf(i))
                        .replace("{ahead}", String.valueOf(i - 1));

                p.sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(
                        ChatColor.translateAlternateColorCodes('&', msg)
                ));
            }
        }
    }

    private void tryCandidate(final ProxiedPlayer player,
                              final List<String> candidates,
                              final int index,
                              final String key,
                              final String motd,
                              final boolean smartMode) {

        final ConfigManager cfg = plugin.getConfigManager();
        if (index >= candidates.size()) {
            if (cfg.queueEnabled && (smartMode || cfg.autoRouteIfFullQueue)) {
                enqueue(player, key, candidates, motd);
            } else {
                player.sendMessage(TextUtil.tc(
                        plugin.getLangManager().msg(player, "full")
                                .replace("{server}", candidates.get(0))
                ));
            }
            return;
        }

        final String target = candidates.get(index);
        final ServerInfo info = ProxyServer.getInstance().getServerInfo(target);

        if (info == null) {
            tryCandidate(player, candidates, index + 1, key, motd, smartMode);
            return;
        }

        info.ping(new Callback<ServerPing>() {
            @Override
            public void done(ServerPing ping, Throwable err) {
                if (err != null || ping == null || ping.getPlayers() == null) {
                    tryCandidate(player, candidates, index + 1, key, motd, smartMode);
                    return;
                }

                int online = ping.getPlayers().getOnline();
                int max = ping.getPlayers().getMax();

                boolean full = cfg.fullEnabled && online >= max &&
                        (cfg.fullBypassPerm.isEmpty() || !player.hasPermission(cfg.fullBypassPerm));

                if (full) {
                    tryCandidate(player, candidates, index + 1, key, motd, smartMode);
                    return;
                }

                if (motd != null && !motd.isEmpty()) {
                    player.sendMessage(TextUtil.tc(ChatColor.translateAlternateColorCodes('&', motd)));
                }

                player.sendMessage(TextUtil.tc(
                        plugin.getLangManager().msg(player, "switching")
                                .replace("{server}", target)
                ));
                player.connect(info);

                // remove from queue if in one
                leaveQueue(player);
            }
        });
    }

    private int priorityFor(ProxiedPlayer p) {
        ConfigManager cfg = plugin.getConfigManager();
        int best = 0;
        for (ConfigManager.QueueTier t : cfg.queueTiers.values()) {
            if (t.permission != null && !t.permission.isEmpty() && p.hasPermission(t.permission)) {
                best = Math.max(best, t.priority);
            } else if (t.permission == null || t.permission.isEmpty()) {
                best = Math.max(best, t.priority);
            }
        }
        return best;
    }

    private void insertByPriority(Deque<Entry> q, Entry e) {
        if (q.isEmpty()) {
            q.add(e);
            return;
        }
        List<Entry> tmp = new ArrayList<Entry>(q);
        tmp.add(e);
        Collections.sort(tmp, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) {
                return Integer.compare(b.priority, a.priority);
            }
        });
        q.clear();
        for (Entry x : tmp) q.addLast(x);
    }

    private int positionOf(Deque<Entry> q, UUID id) {
        int i = 0;
        for (Entry e : q) {
            i++;
            if (e.uuid.equals(id)) return i;
        }
        return i;
    }
}
