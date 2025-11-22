package org.Mailootje.shortServerSwitcherBungee;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

public class ShortServerSwitcherBungee extends Plugin {

    private ConfigManager configManager;

    // commandName -> command instance
    private final Map<String, ServerSwitchCommand> registered = new HashMap<>();

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        configManager.load();

        // main admin/help command
        ProxyServer.getInstance().getPluginManager()
                .registerCommand(this, new SwitcherCommand(this));

        registerShortCommands();

        getLogger().info("ShortServerSwitcherBungee enabled. Loaded "
                + configManager.getCommands().size() + " short commands.");
    }

    @Override
    public void onDisable() {
        unregisterShortCommands();
        getLogger().info("ShortServerSwitcherBungee disabled.");
    }

    public void reloadPlugin() {
        unregisterShortCommands();
        configManager.load();
        registerShortCommands();
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

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
