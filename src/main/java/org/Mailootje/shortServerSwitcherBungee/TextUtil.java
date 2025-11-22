package org.Mailootje.shortServerSwitcherBungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;

public class TextUtil {
    public static TextComponent tc(String msg) {
        return new TextComponent(ChatColor.translateAlternateColorCodes('&', msg));
    }
}

