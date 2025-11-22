package org.Mailootje.shortServerSwitcherBungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.scheduler.ScheduledTask;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ConfirmManager {

    private final ShortServerSwitcherBungee plugin;
    private ScheduledTask cleanupTask;

    // uuid -> key -> expireAt
    private final Map<UUID, Map<String, Long>> pending =
            new ConcurrentHashMap<UUID, Map<String, Long>>();

    public ConfirmManager(ShortServerSwitcherBungee plugin) {
        this.plugin = plugin;
    }

    public void start() {
        cleanupTask = plugin.getProxy().getScheduler().schedule(
                plugin,
                new Runnable() {
                    @Override public void run() { cleanup(); }
                },
                2, 2, TimeUnit.SECONDS
        );
    }

    public void stop() {
        if (cleanupTask != null) cleanupTask.cancel();
        pending.clear();
    }

    public boolean hasPending(ProxiedPlayer p, String key) {
        Map<String, Long> m = pending.get(p.getUniqueId());
        if (m == null) return false;
        Long exp = m.get(key.toLowerCase());
        return exp != null && exp > System.currentTimeMillis();
    }

    public void clearPending(ProxiedPlayer p, String key) {
        Map<String, Long> m = pending.get(p.getUniqueId());
        if (m != null) m.remove(key.toLowerCase());
    }

    public void askConfirm(final ProxiedPlayer p,
                           final String key,
                           final String server,
                           final String motd) {

        ConfigManager cfg = plugin.getConfigManager();
        long expAt = System.currentTimeMillis() + cfg.confirmTimeoutSeconds * 1000L;

        Map<String, Long> m = pending.get(p.getUniqueId());
        if (m == null) {
            m = new ConcurrentHashMap<String, Long>();
            pending.put(p.getUniqueId(), m);
        }
        m.put(key.toLowerCase(), expAt);

        String text = plugin.getLangManager()
                .msg(p, "confirm")
                .replace("{server}", server);

        TextComponent tc = new TextComponent(ChatColor.translateAlternateColorCodes('&', text));

        tc.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/" + key
        ));

        // ✅ New non-deprecated hover API:
        tc.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new Text("Click to confirm")
        ));

        p.sendMessage(tc);

        if (motd != null && !motd.isEmpty()) {
            p.sendMessage(TextUtil.tc(motd));
        }
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<String, Long>> e : pending.entrySet()) {
            Iterator<Map.Entry<String, Long>> it = e.getValue().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Long> pe = it.next();
                if (pe.getValue() <= now) it.remove();
            }
        }
    }
}
