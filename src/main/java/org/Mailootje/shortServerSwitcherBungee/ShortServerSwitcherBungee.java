package org.Mailootje.shortServerSwitcherBungee;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShortServerSwitcherBungee extends Plugin {

    private ConfigManager configManager;

    // commandName -> command instance
    private final Map<String, ServerSwitchCommand> registered = new HashMap<>();

    // cooldowns: player -> (commandKey -> lastUseMillis)
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    // rate limit: player -> limiter
    private final Map<UUID, RateLimiter> rateLimiters = new HashMap<>();

    private QueueManager queueManager;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        configManager.load();

        this.queueManager = new QueueManager(this);

        // admin/help command
        ProxyServer.getInstance().getPluginManager()
                .registerCommand(this, new SwitcherCommand(this));

        // leave queue command (optional)
        ProxyServer.getInstance().getPluginManager()
                .registerCommand(this, new LeaveQueueCommand(this));

        registerShortCommands();

        queueManager.start();

        getLogger().info("ShortServerSwitcherBungee enabled. Loaded "
                + configManager.getCommands().size() + " short commands.");
    }

    @Override
    public void onDisable() {
        unregisterShortCommands();
        if (queueManager != null) queueManager.stop();
        getLogger().info("ShortServerSwitcherBungee disabled.");
    }

    public void reloadPlugin() {
        unregisterShortCommands();
        configManager.load();
        registerShortCommands();
        if (queueManager != null) {
            queueManager.stop();
            queueManager.start();
        }
    }

    private void registerShortCommands() {
        if (!configManager.isAutoRegister()) return;

        for (Map.Entry<String, ConfigManager.CommandDef> entry : configManager.getCommands().entrySet()) {
            String cmdName = entry.getKey().toLowerCase();
            ConfigManager.CommandDef def = entry.getValue();

            // main command
            registerOne(cmdName, def);

            // aliases
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

    // ===== cooldown helpers =====
    public long getLastUse(UUID uuid, String key) {
        return cooldowns.getOrDefault(uuid, new HashMap<>()).getOrDefault(key, 0L);
    }

    public void setLastUse(UUID uuid, String key, long time) {
        cooldowns.computeIfAbsent(uuid, k -> new HashMap<>()).put(key, time);
    }

    // ===== rate limit helpers =====
    public RateLimiter limiter(UUID uuid) {
        return rateLimiters.computeIfAbsent(uuid, k -> new RateLimiter());
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public QueueManager getQueueManager() {
        return queueManager;
    }
}
