package org.Mailootje.shortServerSwitcherBungee;

import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

public class ConfigManager {

    private final ShortServerSwitcherBungee plugin;
    private Configuration config;

    private boolean autoRegister;
    private String globalPermission;

    private Map<String, String> messages = new HashMap<>();
    private List<String> helpLines = new ArrayList<>();

    public static class CommandDef {
        public String server;
        public String permission;
        public List<String> aliases;
    }

    private final Map<String, CommandDef> commands = new LinkedHashMap<>();

    public ConfigManager(ShortServerSwitcherBungee plugin) {
        this.plugin = plugin;
    }

    public void load() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            File file = new File(plugin.getDataFolder(), "config.yml");
            if (!file.exists()) {
                try (InputStream in = plugin.getResourceAsStream("config.yml")) {
                    Files.copy(Objects.requireNonNull(in), file.toPath());
                }
            }

            this.config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);

            this.autoRegister = config.getBoolean("auto-register", true);
            this.globalPermission = config.getString("global-permission", "");

            // messages
            messages.clear();
            Configuration msgSec = config.getSection("messages");
            if (msgSec != null) {
                for (String key : msgSec.getKeys()) {
                    if (key.equalsIgnoreCase("help")) continue;
                    messages.put(key, msgSec.getString(key, ""));
                }
                helpLines = msgSec.getStringList("help");
            }

            // commands
            commands.clear();
            Configuration cmdSec = config.getSection("commands");
            if (cmdSec != null) {
                for (String key : cmdSec.getKeys()) {
                    Configuration one = cmdSec.getSection(key);
                    if (one == null) continue;

                    CommandDef def = new CommandDef();
                    def.server = one.getString("server", "");
                    def.permission = one.getString("permission", "");
                    def.aliases = one.getStringList("aliases");

                    if (def.server == null || def.server.trim().isEmpty()) {
                        plugin.getLogger().warning("Command '" + key + "' skipped: no server set.");
                        continue;
                    }

                    commands.put(key.toLowerCase(), def);
                }
            }

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load config.yml");
            e.printStackTrace();
        }
    }

    public boolean isAutoRegister() {
        return autoRegister;
    }

    public String getGlobalPermission() {
        return globalPermission == null ? "" : globalPermission;
    }

    public String msg(String key) {
        return messages.getOrDefault(key, "");
    }

    public List<String> getHelpLines() {
        return helpLines == null ? Collections.emptyList() : helpLines;
    }

    public Map<String, CommandDef> getCommands() {
        return commands;
    }
}
