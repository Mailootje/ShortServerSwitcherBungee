package org.Mailootje.shortServerSwitcherBungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.config.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

public class LangManager {

    private final ShortServerSwitcherBungee plugin;
    private final Map<String, Configuration> langs = new HashMap<String, Configuration>();

    public LangManager(ShortServerSwitcherBungee plugin) {
        this.plugin = plugin;
    }

    public void loadLanguages() {
        langs.clear();
        saveDefault("messages_en.yml");
        saveDefault("messages_nl.yml");

        loadOne("en");
        loadOne("nl");
    }

    private void saveDefault(String name) {
        File f = new File(plugin.getDataFolder(), name);
        if (f.exists()) return;
        try (InputStream in = plugin.getResourceAsStream(name)) {
            if (in != null) Files.copy(in, f.toPath());
        } catch (IOException ignored) {}
    }

    private void loadOne(String code) {
        try {
            File f = new File(plugin.getDataFolder(), "messages_" + code + ".yml");
            if (!f.exists()) return;
            Configuration c = ConfigurationProvider.getProvider(YamlConfiguration.class).load(f);
            langs.put(code, c);
        } catch (IOException ignored) {}
    }

    public String msg(CommandSender sender, String key) {
        String code = plugin.getConfigManager().defaultLang;

        if (sender instanceof ProxiedPlayer) {
            ProxiedPlayer p = (ProxiedPlayer) sender;
            // collect perms list (cheap way)
            List<String> perms = new ArrayList<String>();
            for (String perm : plugin.getConfigManager().langByPermission.keySet()) {
                if (p.hasPermission(perm)) perms.add(perm);
            }
            code = plugin.getConfigManager().langForPerms(perms);
        }

        Configuration lang = langs.get(code);
        if (lang == null) lang = langs.get(plugin.getConfigManager().defaultLang);

        if (lang == null) return plugin.getConfigManager().msg(key);
        return lang.getString(key, plugin.getConfigManager().msg(key));
    }
}
