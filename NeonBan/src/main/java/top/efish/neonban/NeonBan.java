package top.efish.neonban;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.BanList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class NeonBan extends JavaPlugin {

    private String downloadUrl;
    private String pardonUrl;
    private long updateInterval;
    private String language;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        this.getCommand("neonban").setTabCompleter(new NeonBanTabCompleter());
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
        pardonUrl = config.getString("pardonUrl");
        updateInterval = config.getLong("updateInterval");
        language = config.getString("language");
    }

    private List<String> downloadPardonedPlayers() {
        List<String> pardonedUUIDs = new ArrayList<>();
        getLogger().info("Downloading pardoned players list from: " + pardonUrl);
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(pardonUrl).openConnection();
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
                        String uuid = playerElement.getElementsByTagName("uuid").item(0).getTextContent();
                        pardonedUUIDs.add(uuid);
                    }
                }
            } else {
                getLogger().warning("Failed to download pardoned players list. Response code: " + connection.getResponseCode());
            }
        } catch (Exception e) {
            getLogger().severe("Error downloading or parsing pardoned players list: " + e.getMessage());
        }
        return pardonedUUIDs;
    }

    // Download and ban players
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

                // Download pardoned players
                List<String> pardonedUUIDs = downloadPardonedPlayers();

                // Pardon players that are in the pardoned list
                for (String uuid : pardonedUUIDs) {
                    org.bukkit.BanEntry banEntry = Bukkit.getBanList(BanList.Type.NAME).getBanEntry(uuid);
                    if (banEntry != null) {
                        Bukkit.getBanList(BanList.Type.NAME).pardon(uuid);
                        getLogger().info("Pardoned player (UUID: " + uuid + ") due to being in the pardoned list.");
                    }
                }

                for (int i = 0; i < playerList.getLength(); i++) {
                    Node playerNode = playerList.item(i);

                    if (playerNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element playerElement = (Element) playerNode;

                        String name = playerElement.getElementsByTagName("name").item(0).getTextContent();
                        String uuid = playerElement.getElementsByTagName("uuid").item(0).getTextContent();
                        String reason = playerElement.getElementsByTagName("reason").item(0).getTextContent();
                        String docket = playerElement.getElementsByTagName("docket").item(0).getTextContent();
                        String fullreason = "You have been banned by the NeonBan for " + reason + " Docket: " + docket;

                        // Check if the player is pardoned
                        if (!pardonedUUIDs.contains(uuid)) {
                            Bukkit.getBanList(BanList.Type.NAME).addBan(name, fullreason, null, null);
                            getLogger().info("Banned player: Name = " + name + ", UUID = " + uuid + " | Reason: " + reason);
                        } else {
                            getLogger().info("Player " + name + " (UUID: " + uuid + ") is pardoned and will not be banned.");
                        }
                    }
                }
            } else {
                getLogger().warning("Failed to download ban list. Response code: " + connection.getResponseCode());
            }
        } catch (Exception e) {
            getLogger().severe("Error downloading or parsing ban list: " + e.getMessage());
        }
    }

    private List<String> loadPardonedPlayers() {
        List<String> pardonedUUIDs = new ArrayList<>();
        try {
            File pardonedFile = new File(getDataFolder(), "pardoned-players.xml");
            if (pardonedFile.exists()) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document document = builder.parse(pardonedFile);
                document.getDocumentElement().normalize();

                NodeList playerList = document.getElementsByTagName("player");
                for (int i = 0; i < playerList.getLength(); i++) {
                    Node playerNode = playerList.item(i);
                    if (playerNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element playerElement = (Element) playerNode;
                        String uuid = playerElement.getElementsByTagName("uuid").item(0).getTextContent();
                        pardonedUUIDs.add(uuid);
                    }
                }
            }
        } catch (Exception e) {
            getLogger().severe("Error loading pardoned players: " + e.getMessage());
        }
        return pardonedUUIDs;
    }

    private void savePardonedPlayer(String uuid) {
        try {
            File pardonedFile = new File(getDataFolder(), "pardoned-players.xml");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document;
            Element rootElement;

            if (pardonedFile.exists()) {
                document = builder.parse(pardonedFile);
                rootElement = document.getDocumentElement();
            } else {
                document = builder.newDocument();
                rootElement = document.createElement("pardonedPlayers");
                document.appendChild(rootElement);
            }

            Element playerElement = document.createElement("player");
            Element uuidElement = document.createElement("uuid");
            uuidElement.appendChild(document.createTextNode(uuid));
            playerElement.appendChild(uuidElement);
            rootElement.appendChild(playerElement);

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(document);
            StreamResult result = new StreamResult(pardonedFile);
            transformer.transform(source, result);

            getLogger().info("Saved pardoned player: UUID = " + uuid);
        } catch (Exception e) {
            getLogger().severe("Error saving pardoned player: " + e.getMessage());
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
                        Bukkit.getBanList(BanList.Type.NAME).getBanEntries().forEach(entry ->
                                sender.sendMessage("- " + entry.getTarget()));
                    } else {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    }
                    break;
                case "pardon":
                    if (args.length == 2 && sender.hasPermission("neonban.pardon")) {
                        String targetUUID = args[1];
                        boolean found = false;
                        for (org.bukkit.BanEntry entry : Bukkit.getBanList(BanList.Type.NAME).getBanEntries()) {
                            if (entry.getTarget().equalsIgnoreCase(targetUUID)) {
                                Bukkit.getBanList(BanList.Type.NAME).pardon(entry.getTarget());
                                savePardonedPlayer(entry.getTarget());
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
                case "version":
                    sender.sendMessage(ChatColor.YELLOW + "NeonBan version: " + getDescription().getVersion());
                    break;
                case "report":
                    sender.sendMessage(ChatColor.YELLOW + "Report the issue to the inspector: https://github.com/Ziyang-Bai/Neon-Ban-List/issues");
                    break;
                default:
                    sender.sendMessage(ChatColor.YELLOW + "Unknown command. Usage: /neonban <update|list|pardon|version|report>");
                    break;
            }
            return true;
        }
        return false;
    }
}