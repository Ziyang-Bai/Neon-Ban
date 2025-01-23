package top.efish.neonban;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NeonBanTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("update", "list", "pardon", "version", "report");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("pardon")) {
            List<String> bannedPlayers = new ArrayList<>();
            for (org.bukkit.BanEntry entry : Bukkit.getBanList(org.bukkit.BanList.Type.NAME).getBanEntries()) {
                bannedPlayers.add(entry.getTarget());
            }
            return bannedPlayers;
        }
        return Collections.emptyList();
    }
}