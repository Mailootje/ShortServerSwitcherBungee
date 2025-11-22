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
    public Map<String, Integer> cooldownPerCommand = new HashMap<String, Integer>();

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
    public int queueGraceSeconds;
    public String queueLeaveCommand;

    // queue tiers
    public static class QueueTier {
        public String permission;
        public int priority;
    }
    public Map<String, QueueTier> queueTiers = new LinkedHashMap<String, QueueTier>();

    // queue status
    public boolean queueStatusEnabled;
    public int queueStatusInterval;
    public String queueStatusActionbar;

    // auto-route
    public boolean autoRouteEnabled;
    public String autoRouteDefaultCommand;
    public boolean autoRouteIfFullQueue;
    public String autoRouteBypassPerm;

    // smart select
    public boolean smartSelectEnabled;
    public String smartSelectMode;
    public Map<String, List<String>> smartCandidates = new HashMap<String, List<String>>();

    // failover
    public Map<String, List<String>> failover = new HashMap<String, List<String>>();

    // confirm
    public boolean confirmEnabled;
    public int confirmTimeoutSeconds;
    public Set<String> confirmCommands = new HashSet<String>();

    // language
    public String defaultLang;
    public Map<String, String> langByPermission = new HashMap<String, String>();

    private Map<String, String> messages = new HashMap<String, String>();
    private List<String> helpLines = new ArrayList<String>();

    public static class CommandDef {
        public String server;
        public String permission;
        public List<String> aliases;
        public String key;

        // v3 extras
        public boolean enabled = true;
        public String motd = "";
        public String allowPermission = "";
    }

    private final Map<String, CommandDef> commands = new LinkedHashMap<String, CommandDef>();

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
            autoPermsEnabled = ap != null && ap.getBoolean("enabled", true);
            autoPermPrefix = ap != null ? ap.getString("prefix", "shortswitch.") : "shortswitch.";

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

            // full
            Configuration fs = config.getSection("full-servers");
            fullEnabled = fs != null && fs.getBoolean("enabled", true);
            fullBypassPerm = fs != null ? fs.getString("bypass-permission", "shortswitch.bypass.full") : "";

            // queue
            Configuration q = config.getSection("queue");
            queueEnabled = q != null && q.getBoolean("enabled", false);
            queueCheckInterval = q != null ? q.getInt("check-interval-seconds", 5) : 5;
            queueMaxWait = q != null ? q.getInt("max-wait-seconds", 300) : 300;
            queueGraceSeconds = q != null ? q.getInt("grace-seconds", 0) : 0;
            queueLeaveCommand = q != null ? q.getString("leave-command", "leavequeue") : "leavequeue";

            // tiers
            queueTiers.clear();
            if (q != null) {
                Configuration tiers = q.getSection("tiers");
                if (tiers != null) {
                    for (String t : tiers.getKeys()) {
                        Configuration one = tiers.getSection(t);
                        if (one == null) continue;
                        QueueTier tier = new QueueTier();
                        tier.permission = one.getString("permission", "");
                        tier.priority = one.getInt("priority", 0);
                        queueTiers.put(t.toLowerCase(), tier);
                    }
                }
            }

            // queue status
            queueStatusEnabled = false;
            queueStatusInterval = 10;
            queueStatusActionbar = "";
            if (q != null) {
                Configuration st = q.getSection("status");
                if (st != null) {
                    queueStatusEnabled = st.getBoolean("enabled", false);
                    queueStatusInterval = st.getInt("interval-seconds", 10);
                    queueStatusActionbar = st.getString("actionbar", "");
                }
            }

            // auto-route
            Configuration ar = config.getSection("auto-route");
            autoRouteEnabled = ar != null && ar.getBoolean("enabled", false);
            autoRouteDefaultCommand = ar != null ? ar.getString("default-command", "") : "";
            autoRouteIfFullQueue = ar != null && ar.getBoolean("if-full-queue", true);
            autoRouteBypassPerm = ar != null ? ar.getString("bypass-permission", "shortswitch.bypass.autoroute") : "";

            // smart select
            Configuration ss = config.getSection("smart-select");
            smartSelectEnabled = ss != null && ss.getBoolean("enabled", false);
            smartSelectMode = ss != null ? ss.getString("mode", "least-players") : "least-players";
            smartCandidates.clear();
            if (ss != null) {
                Configuration cand = ss.getSection("candidates");
                if (cand != null) {
                    for (String k : cand.getKeys()) {
                        smartCandidates.put(k.toLowerCase(), cand.getStringList(k));
                    }
                }
            }

            // failover
            failover.clear();
            Configuration fo = config.getSection("failover");
            if (fo != null) {
                for (String key : fo.getKeys()) {
                    failover.put(key.toLowerCase(), fo.getStringList(key));
                }
            }

            // confirm
            Configuration cf = config.getSection("confirm");
            confirmEnabled = cf != null && cf.getBoolean("enabled", false);
            confirmTimeoutSeconds = cf != null ? cf.getInt("timeout-seconds", 10) : 10;
            confirmCommands.clear();
            if (cf != null) {
                for (String ckey : cf.getStringList("commands")) {
                    confirmCommands.add(ckey.toLowerCase());
                }
            }

            // lang
            Configuration lang = config.getSection("lang");
            defaultLang = lang != null ? lang.getString("default", "en") : "en";
            langByPermission.clear();
            if (lang != null) {
                Configuration pp = lang.getSection("per-permission");
                if (pp != null) {
                    for (String perm : pp.getKeys()) {
                        langByPermission.put(perm, pp.getString(perm, defaultLang));
                    }
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
                    def.enabled = one.getBoolean("enabled", true);
                    def.motd = one.getString("motd", "");
                    def.allowPermission = one.getString("allow-permission", "");

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

    public List<String> smartFor(String key, String baseServer) {
        List<String> list = smartCandidates.get(key.toLowerCase());
        if (list == null || list.isEmpty()) return Collections.singletonList(baseServer);
        return list;
    }

    public String langForPerms(Collection<String> playerPerms) {
        for (Map.Entry<String, String> e : langByPermission.entrySet()) {
            if (playerPerms.contains(e.getKey())) return e.getValue();
        }
        return defaultLang;
    }
}
