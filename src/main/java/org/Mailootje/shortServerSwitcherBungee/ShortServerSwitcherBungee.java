package org.Mailootje.shortServerSwitcherBungee;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShortServerSwitcherBungee extends Plugin {

    private ConfigManager configManager;

    private final Map<String, ServerSwitchCommand> registered = new HashMap<String, ServerSwitchCommand>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<UUID, Map<String, Long>>();
    private final Map<UUID, RateLimiter> rateLimiters = new HashMap<UUID, RateLimiter>();

    private QueueManager queueManager;
    private ConfirmManager confirmManager;
    private LangManager langManager;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        configManager.load();

        this.langManager = new LangManager(this);
        langManager.loadLanguages();

        this.queueManager = new QueueManager(this);
        this.confirmManager = new ConfirmManager(this);

        ProxyServer.getInstance().getPluginManager()
                .registerCommand(this, new SwitcherCommand(this));
        ProxyServer.getInstance().getPluginManager()
                .registerCommand(this, new LeaveQueueCommand(this));

        ProxyServer.getInstance().getPluginManager()
                .registerListener(this, (Listener) new ProxyJoinListener(this));

        registerShortCommands();

        queueManager.start();
        confirmManager.start();

        getLogger().info("ShortServerSwitcherBungee enabled.");
    }

    @Override
    public void onDisable() {
        unregisterShortCommands();
        if (queueManager != null) queueManager.stop();
        if (confirmManager != null) confirmManager.stop();
    }

    public void reloadPlugin() {
        unregisterShortCommands();
        configManager.load();
        langManager.loadLanguages();
        registerShortCommands();
        if (queueManager != null) {
            queueManager.stop();
            queueManager.start();
        }
        if (confirmManager != null) {
            confirmManager.stop();
            confirmManager.start();
        }
    }

    private void registerShortCommands() {
        if (!configManager.isAutoRegister()) return;

        for (Map.Entry<String, ConfigManager.CommandDef> entry : configManager.getCommands().entrySet()) {
            String cmdName = entry.getKey().toLowerCase();
            ConfigManager.CommandDef def = entry.getValue();

            registerOne(cmdName, def);

            if (def.aliases != null) {
                for (String alias : def.aliases) {
                    if (alias == null || alias.trim().isEmpty()) continue;
                    registerOne(alias.toLowerCase(), def);
                }
            }
        }
    }

    private void registerOne(String name, ConfigManager.CommandDef def) {
        if (registered.containsKey(name)) return;

        ServerSwitchCommand cmd = new ServerSwitchCommand(this, name, def);
        ProxyServer.getInstance().getPluginManager().registerCommand(this, cmd);
        registered.put(name, cmd);
    }

    private void unregisterShortCommands() {
        for (ServerSwitchCommand cmd : registered.values()) {
            ProxyServer.getInstance().getPluginManager().unregisterCommand(cmd);
        }
        registered.clear();
    }

    public long getLastUse(UUID uuid, String key) {
        Map<String, Long> map = cooldowns.get(uuid);
        if (map == null) return 0L;
        Long v = map.get(key);
        return v == null ? 0L : v;
    }

    public void setLastUse(UUID uuid, String key, long time) {
        Map<String, Long> map = cooldowns.get(uuid);
        if (map == null) {
            map = new HashMap<String, Long>();
            cooldowns.put(uuid, map);
        }
        map.put(key, time);
    }

    public RateLimiter limiter(UUID uuid) {
        RateLimiter r = rateLimiters.get(uuid);
        if (r == null) {
            r = new RateLimiter();
            rateLimiters.put(uuid, r);
        }
        return r;
    }

    public ConfigManager getConfigManager() { return configManager; }
    public QueueManager getQueueManager() { return queueManager; }
    public ConfirmManager getConfirmManager() { return confirmManager; }
    public LangManager getLangManager() { return langManager; }
}
