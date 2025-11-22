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

    // auto perms
    private boolean autoPermsEnabled;
    private String autoPermPrefix;

    // cooldown
    public boolean cooldownEnabled;
    public String cooldownBypassPerm;
    public int cooldownDefault;
    public Map<String, Integer> cooldownPerCommand = new HashMap<>();

    // rate limit
    public boolean rateEnabled;
    public String rateBypassPerm;
    public int rateMax;
    public int ratePerSeconds;
    public int rateBlockSeconds;

    // full / queue
    public boolean fullEnabled;
    public String fullBypassPerm;

    public boolean queueEnabled;
    public int queueCheckInterval;
    public int queueMaxWait;
    public String queueLeaveCommand;

    // failover
    public Map<String, List<String>> failover = new HashMap<>();

    private Map<String, String> messages = new HashMap<>();
    private List<String> helpLines = new ArrayList<>();

    public static class CommandDef {
        public String server;
        public String permission;
        public List<String> aliases;
        public String key; // main key for cooldown/perm grouping
    }

    private final Map<String, CommandDef> commands = new LinkedHashMap<>();

    public ConfigManager(ShortServerSwitcherBungee plugin) {
        this.plugin = plugin;
    }

    public void load() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

            File file = new File(plugin.getDataFolder(), "config.yml");
            if (!file.exists()) {
                try (InputStream in = plugin.getResourceAsStream("config.yml")) {
                    Files.copy(Objects.requireNonNull(in), file.toPath());
                }
            }

            this.config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);

            this.autoRegister = config.getBoolean("auto-register", true);
            this.globalPermission = config.getString("global-permission", "");

            // auto-perms
            Configuration ap = config.getSection("auto-permissions");
            this.autoPermsEnabled = ap != null && ap.getBoolean("enabled", true);
            this.autoPermPrefix = ap != null ? ap.getString("prefix", "shortswitch.") : "shortswitch.";

            // cooldowns
            Configuration cd = config.getSection("cooldowns");
            cooldownEnabled = cd != null && cd.getBoolean("enabled", false);
            cooldownBypassPerm = cd != null ? cd.getString("bypass-permission", "shortswitch.bypass.cooldown") : "";
            cooldownDefault = cd != null ? cd.getInt("default-seconds", 0) : 0;
            cooldownPerCommand.clear();
            if (cd != null) {
                Configuration per = cd.getSection("per-command");
                if (per != null) {
                    for (String k : per.getKeys()) {
                        cooldownPerCommand.put(k.toLowerCase(), per.getInt(k));
                    }
                }
            }

            // rate-limit
            Configuration rl = config.getSection("rate-limit");
            rateEnabled = rl != null && rl.getBoolean("enabled", false);
            rateBypassPerm = rl != null ? rl.getString("bypass-permission", "shortswitch.bypass.ratelimit") : "";
            rateMax = rl != null ? rl.getInt("max-switches", 5) : 5;
            ratePerSeconds = rl != null ? rl.getInt("per-seconds", 10) : 10;
            rateBlockSeconds = rl != null ? rl.getInt("block-seconds", 30) : 30;

            // full / queue
            Configuration fs = config.getSection("full-servers");
            fullEnabled = fs != null && fs.getBoolean("enabled", true);
            fullBypassPerm = fs != null ? fs.getString("bypass-permission", "shortswitch.bypass.full") : "";

            Configuration q = config.getSection("queue");
            queueEnabled = q != null && q.getBoolean("enabled", false);
            queueCheckInterval = q != null ? q.getInt("check-interval-seconds", 5) : 5;
            queueMaxWait = q != null ? q.getInt("max-wait-seconds", 300) : 300;
            queueLeaveCommand = q != null ? q.getString("leave-command", "leavequeue") : "leavequeue";

            // failover
            failover.clear();
            Configuration fo = config.getSection("failover");
            if (fo != null) {
                for (String key : fo.getKeys()) {
                    failover.put(key.toLowerCase(), fo.getStringList(key));
                }
            }

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
                    def.key = key.toLowerCase();
                    def.server = one.getString("server", "");
                    def.permission = one.getString("permission", "");
                    def.aliases = one.getStringList("aliases");

                    if (def.server == null || def.server.trim().isEmpty()) {
                        plugin.getLogger().warning("Command '" + key + "' skipped: no server set.");
                        continue;
                    }

                    if ((def.permission == null || def.permission.isEmpty()) && autoPermsEnabled) {
                        def.permission = autoPermPrefix + def.key;
                    }

                    commands.put(def.key, def);
                }
            }

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load config.yml");
            e.printStackTrace();
        }
    }

    public boolean isAutoRegister() { return autoRegister; }
    public String getGlobalPermission() { return globalPermission == null ? "" : globalPermission; }

    public String msg(String key) { return messages.getOrDefault(key, ""); }
    public List<String> getHelpLines() { return helpLines == null ? Collections.emptyList() : helpLines; }

    public Map<String, CommandDef> getCommands() { return commands; }

    public int cooldownFor(String key) {
        return cooldownPerCommand.getOrDefault(key.toLowerCase(), cooldownDefault);
    }

    public List<String> failoverFor(String key, String baseServer) {
        List<String> list = failover.get(key.toLowerCase());
        if (list == null || list.isEmpty()) return Collections.singletonList(baseServer);
        return list;
    }
}
