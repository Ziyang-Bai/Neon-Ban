package neonban.efish.top.neonBan;

import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.net.URL;
import java.util.Date;
import java.util.UUID;
import java.util.logging.Level;

public final class NeonBan extends JavaPlugin {

    private String downloadUrl;
    private int updateInterval;
    private String language;
    private Document langDoc;

    @Override
    public void onEnable() {
        getLogger().info("[NeonBan] Plugin is starting up...");
        loadConfig();
        loadLanguageFile();
        getServer().getScheduler().runTaskTimer(this, this::downloadAndBanPlayers, 0, updateInterval * 20L);
        getLogger().info("[NeonBan] Plugin has started successfully.");
    }

    @Override
    public void onDisable() {
        getLogger().info("[NeonBan] Plugin is shutting down...");
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("[NeonBan] Plugin has shut down successfully.");
    }

    private void loadConfig() {
        try {
            File configFile = new File(getDataFolder(), "config.xml");
            if (!configFile.exists()) {
                saveResource("config.xml", false);
            }
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(configFile);
            doc.getDocumentElement().normalize();

            downloadUrl = doc.getElementsByTagName("downloadUrl").item(0).getTextContent();
            updateInterval = Integer.parseInt(doc.getElementsByTagName("updateInterval").item(0).getTextContent());
            language = doc.getElementsByTagName("language").item(0).getTextContent();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to load config", e);
        }
    }

    private void loadLanguageFile() {
        try {
            File langFile = new File(getDataFolder(), "lang_" + language + ".xml");
            if (!langFile.exists()) {
                saveResource("lang_" + language + ".xml", false);
            }
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            langDoc = dBuilder.parse(langFile);
            langDoc.getDocumentElement().normalize();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to load language file", e);
        }
    }

    private String getLang(String key) {
        try {
            return langDoc.getElementsByTagName(key).item(0).getTextContent();
        } catch (Exception e) {
            return "Missing language key: " + key;
        }
    }

    private void downloadAndBanPlayers() {
        try {
            URL url = new URL(downloadUrl);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(url.openStream());

            doc.getDocumentElement().normalize();
            NodeList nList = doc.getElementsByTagName("player");

            for (int temp = 0; temp < nList.getLength(); temp++) {
                Node nNode = nList.item(temp);

                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    String uuidStr = eElement.getElementsByTagName("uuid").item(0).getTextContent();
                    String reason = eElement.getElementsByTagName("reason").item(0).getTextContent();

                    try {
                        UUID uuid = UUID.fromString(uuidStr);

                        // Ban the player based on UUID
                        BanList banList = Bukkit.getBanList(BanList.Type.PROFILE);
                        BanEntry existingBan = banList.getBanEntry(uuid);
                        if (existingBan == null) {
                            banList.addBan(uuid, reason, null, "NeonBan");
                            getLogger().info("Banned player with UUID: " + uuid + " for reason: " + reason);
                        }
                    } catch (IllegalArgumentException ex) {
                        getLogger().log(Level.WARNING, "Invalid UUID: " + uuidStr, ex);
                    }
                }
            }
            getLogger().info(getLang("updateSuccess"));
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, getLang("updateFail"), e);
        }
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("neonban")) {
            if (args.length == 0) {
                return false;
            }
            if (args[0].equalsIgnoreCase("update")) {
                if (sender.hasPermission("neonban.update")) {
                    downloadAndBanPlayers();
                    sender.sendMessage(getLang("updateSuccess"));
                } else {
                    sender.sendMessage(getLang("noPermission"));
                }
                return true;
            } else if (args[0].equalsIgnoreCase("list")) {
                if (sender.hasPermission("neonban.list")) {
                    BanList banList = Bukkit.getBanList(BanList.Type.NAME);
                    sender.sendMessage(getLang("listHeader"));
                    for (BanEntry entry : banList.getBanEntries()) {
                        sender.sendMessage(entry.getTarget() + ": " + entry.getReason());
                    }
                } else {
                    sender.sendMessage(getLang("noPermission"));
                }
                return true;
            } else if (args[0].equalsIgnoreCase("pardon")) {
                if (args.length < 2) {
                    return false;
                }
                if (sender.hasPermission("neonban.pardon")) {
                    try {
                        UUID uuid = UUID.fromString(args[1]);
                        BanList banList = Bukkit.getBanList(BanList.Type.NAME);
                        banList.pardon(uuid.toString());
                        sender.sendMessage(getLang("pardonSuccess"));
                    } catch (IllegalArgumentException ex) {
                        sender.sendMessage(getLang("invalidUUID"));
                    }
                } else {
                    sender.sendMessage(getLang("noPermission"));
                }
                return true;
            }
        }
        return false;
    }
}
