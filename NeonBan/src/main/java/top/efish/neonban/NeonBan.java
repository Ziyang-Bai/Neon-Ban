package top.efish.neonban;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

public class NeonBan extends JavaPlugin {

    private String downloadUrl;
    private long updateInterval;
    private String language;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        loadLanguageFile();

        // Schedule auto-update task
        new BukkitRunnable() {
            @Override
            public void run() {
                downloadAndBanPlayers();
            }
        }.runTaskTimer(this, 0L, updateInterval * 20L); // updateInterval in seconds
        getLogger().info(ChatColor.GREEN + "NeonBan Plugin enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info(ChatColor.RED + "NeonBan Plugin disabled!");
    }

    // Load configuration from config.yml
    private void loadConfig() {
        FileConfiguration config = getConfig();
        downloadUrl = config.getString("downloadUrl");
        updateInterval = config.getLong("updateInterval");
        language = config.getString("language");
    }

    // Load the language file
    private void loadLanguageFile() {
        File langFile = new File(getDataFolder(), "lang_" + language + ".xml");
        if (!langFile.exists()) {
            saveResource("lang_" + language + ".xml", false);
        }
        getLogger().info("Loaded language file: " + langFile.getName());
    }

    // Download and ban players
    // 下载并封禁玩家
    private void downloadAndBanPlayers() {
        getLogger().info("Downloading ban list from: " + downloadUrl);
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document document = builder.parse(connection.getInputStream());
                document.getDocumentElement().normalize();

                NodeList playerList = document.getElementsByTagName("player");

                for (int i = 0; i < playerList.getLength(); i++) {
                    Node playerNode = playerList.item(i);

                    if (playerNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element playerElement = (Element) playerNode;

                        // 提取 name 和 uuid
                        String name = playerElement.getElementsByTagName("name").item(0).getTextContent();
                        String uuid = playerElement.getElementsByTagName("uuid").item(0).getTextContent();
                        String reason = playerElement.getElementsByTagName("reason").item(0).getTextContent();

                        // 使用 UUID 封禁玩家，并将 name 正确保存到 banned-players.json
                        Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(name, reason, null, null);
                        getLogger().info("Banned player: Name = " + name + ", UUID = " + uuid + " | Reason: " + reason);
                    }
                }
            } else {
                getLogger().warning("Failed to download ban list. Response code: " + connection.getResponseCode());
            }
        } catch (Exception e) {
            getLogger().severe("Error downloading or parsing ban list: " + e.getMessage());
        }
    }

    // Command handling
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("neonban")) {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /neonban <update|list|pardon>");
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "update":
                    if (sender.hasPermission("neonban.update")) {
                        downloadAndBanPlayers();
                        sender.sendMessage(ChatColor.GREEN + "Ban list updated successfully.");
                    } else {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    }
                    break;
                case "list":
                    if (sender.hasPermission("neonban.list")) {
                        sender.sendMessage(ChatColor.YELLOW + "Current banned players:");
                        Bukkit.getBanList(org.bukkit.BanList.Type.NAME).getBanEntries().forEach(entry ->
                                sender.sendMessage("- " + entry.getTarget()));
                    } else {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    }
                    break;
                case "pardon":
                    if (args.length == 2 && sender.hasPermission("neonban.pardon")) {
                        String targetUUID = args[1];

                        // 遍历 BanList，按 UUID 进行匹配
                        boolean found = false;
                        for (org.bukkit.BanEntry entry : Bukkit.getBanList(org.bukkit.BanList.Type.NAME).getBanEntries()) {
                            if (entry.getTarget().equalsIgnoreCase(targetUUID)) {
                                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(entry.getTarget());
                                sender.sendMessage(ChatColor.GREEN + "Player " + targetUUID + " has been pardoned.");
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            sender.sendMessage(ChatColor.RED + "Player with UUID " + targetUUID + " is not banned.");
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "Usage: /neonban pardon <UUID>");
                    }
                    break;

                default:
                    sender.sendMessage(ChatColor.YELLOW + "Unknown command. Usage: /neonban <update|list|pardon>");
                    break;
            }
            return true;
        }
        return false;
    }
}
