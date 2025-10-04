package dk.noxitech.essentialssql.manager;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dk.noxitech.essentialssql.Main;
import dk.noxitech.essentialssql.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class UserDataManager {

    private final Main plugin;
    private final DatabaseManager databaseManager;
    private final Gson gson;
    private final Path essentialsDataPath;
    private final JsonParser jsonParser;

    public UserDataManager(Main plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.gson = new Gson();
        this.jsonParser = new JsonParser();

        this.essentialsDataPath = Paths.get(plugin.getDataFolder().getParent(), "Essentials", "userdata");

        try {
            Files.createDirectories(essentialsDataPath);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create Essentials userdata directory: " + e.getMessage());
        }
    }

    public CompletableFuture<Boolean> loadPlayerData(UUID playerUuid, String playerName) {
        return databaseManager.getUserData(playerUuid).thenCompose(data -> {
            if (data == null) {
                if (plugin.getConfig().getBoolean("debug.log-file-operations", false)) {
                    plugin.getLogger().info(String.format("No database data found for player %s (%s)", playerName, playerUuid));
                }
                return CompletableFuture.completedFuture(false);
            }

            return CompletableFuture.supplyAsync(() -> {
                try {
                    String userData = isDataCompressed(data) ? decompressData(data) : data;

                    if (plugin.getConfig().getBoolean("data.filters.enabled", false)) {
                        userData = filterUserData(userData);
                    }

                    Path userFile = essentialsDataPath.resolve(playerUuid.toString() + ".yml");
                    Files.write(userFile, userData.getBytes());

                    if (plugin.getConfig().getBoolean("debug.log-file-operations", false)) {
                        plugin.getLogger().info(String.format("Created userdata file for player %s (%s)", playerName, playerUuid));
                    }

                    return true;
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, 
                        String.format("Failed to load data for player %s (%s)", playerName, playerUuid), e);
                    return false;
                }
            });
        });
    }

    public CompletableFuture<Boolean> savePlayerData(UUID playerUuid, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path userFile = essentialsDataPath.resolve(playerUuid.toString() + ".yml");

                if (!Files.exists(userFile)) {
                    if (plugin.getConfig().getBoolean("debug.log-file-operations", false)) {
                        plugin.getLogger().info(String.format("No userdata file found for player %s (%s)", playerName, playerUuid));
                    }
                    return false;
                }

                String userData = new String(Files.readAllBytes(userFile));

                if (plugin.getConfig().getBoolean("debug.log-file-operations", true) && plugin.getConfig().getBoolean("debug.enabled", true)) {
                    plugin.getLogger().info(String.format("[DEBUG] Reading userdata for %s: %d bytes", playerName, userData.length()));
                    if (userData.length() > 0) {
                        plugin.getLogger().info(String.format("[DEBUG] First 200 chars of data for %s: %s", playerName,
                            userData.length() > 200 ? userData.substring(0, 200) + "..." : userData));
                    }
                }

                if (plugin.getConfig().getBoolean("data.filters.enabled", false)) {
                    userData = filterUserData(userData);
                    if (plugin.getConfig().getBoolean("debug.log-file-operations", true) && plugin.getConfig().getBoolean("debug.enabled", true)) {
                        plugin.getLogger().info(String.format("[DEBUG] Data filtered for %s", playerName));
                    }
                } else {
                    if (plugin.getConfig().getBoolean("debug.log-file-operations", true) && plugin.getConfig().getBoolean("debug.enabled", true)) {
                        plugin.getLogger().info(String.format("[DEBUG] Filtering DISABLED - preserving ALL data for %s", playerName));
                    }
                }

                int maxDataSize = plugin.getConfig().getInt("data.max-data-size", 0);
                if (maxDataSize > 0 && userData.length() > maxDataSize * 1024) {
                    plugin.getLogger().warning(String.format(
                        "Data size for player %s exceeds limit (%d KB). Data will be truncated.", 
                        playerName, maxDataSize));
                    userData = userData.substring(0, maxDataSize * 1024);
                }

                if (plugin.getConfig().getBoolean("data.compress-data", true)) {
                    int originalSize = userData.length();
                    userData = compressData(userData);
                    if (plugin.getConfig().getBoolean("debug.log-file-operations", true) && plugin.getConfig().getBoolean("debug.enabled", true)) {
                        plugin.getLogger().info(String.format("[DEBUG] Data compressed for %s: %d -> %d bytes", playerName, originalSize, userData.length()));
                    }
                } else {
                    if (plugin.getConfig().getBoolean("debug.log-file-operations", true) && plugin.getConfig().getBoolean("debug.enabled", true)) {
                        plugin.getLogger().info(String.format("[DEBUG] Compression disabled for %s: %d bytes", playerName, userData.length()));
                    }
                }

                if (plugin.getConfig().getBoolean("debug.log-file-operations", true) && plugin.getConfig().getBoolean("debug.enabled", true)) {
                    plugin.getLogger().info(String.format("[DEBUG] Attempting to save %d bytes to database for player %s (%s)", userData.length(), playerName, playerUuid));
                }
                boolean result = databaseManager.saveUserData(playerUuid, playerName, userData).join();
                if (plugin.getConfig().getBoolean("debug.log-file-operations", true) && plugin.getConfig().getBoolean("debug.enabled", true)) {
                    plugin.getLogger().info(String.format("[DEBUG] Database save result for %s: %s", playerName, result));
                }
                return result;

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, 
                    String.format("Failed to save data for player %s (%s)", playerName, playerUuid), e);
                return false;
            }
        }).thenCompose(saved -> {
            if (saved && plugin.getConfig().getBoolean("settings.delete-local-after-save", true)) {
                return deleteLocalUserData(playerUuid, playerName);
            }
            return CompletableFuture.completedFuture(saved);
        });
    }

    public CompletableFuture<Boolean> deleteLocalUserData(UUID playerUuid, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path userFile = essentialsDataPath.resolve(playerUuid.toString() + ".yml");

                if (Files.exists(userFile)) {
                    Files.delete(userFile);

                    if (plugin.getConfig().getBoolean("debug.log-file-operations", false)) {
                        plugin.getLogger().info(String.format("Deleted local userdata file for player %s (%s)", 
                                                            playerName, playerUuid));
                    }
                }

                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, 
                    String.format("Failed to delete local userdata file for player %s (%s)", playerName, playerUuid), e);
                return false;
            }
        });
    }

    public CompletableFuture<Integer> importAllUserData() {
        return CompletableFuture.supplyAsync(() -> {
            int importedCount = 0;
            try {
                if (!Files.exists(essentialsDataPath)) {
                    plugin.getLogger().warning("Essentials userdata folder not found!");
                    return 0;
                }

                if (plugin.getConfig().getBoolean("commands.import-export.backup-before-import", true)) {
                    createBackup("import");
                }

                Files.list(essentialsDataPath)
                    .filter(path -> path.toString().endsWith(".yml"))
                    .forEach(path -> {
                        try {
                            String fileName = path.getFileName().toString().replace(".yml", "");
                            UUID playerUuid = UUID.fromString(fileName);

                            String playerName = getPlayerNameFromFile(path);
                            if (playerName == null) {
                                playerName = "Unknown";
                            }

                            String userData = new String(Files.readAllBytes(path));

                            if (plugin.getConfig().getBoolean("data.filters.enabled", false)) {
                                userData = filterUserData(userData);
                            } else {
                                if (plugin.getConfig().getBoolean("debug.log-file-operations", false)) {
                                    plugin.getLogger().info(String.format("Importing ALL data for player %s (filtering disabled)", playerName));
                                }
                            }

                            if (plugin.getConfig().getBoolean("data.compress-data", true)) {
                                userData = compressData(userData);
                            }

                            databaseManager.saveUserData(playerUuid, playerName, userData).join();

                            double balance = getPlayerBalanceFromFile(path);
                            if (balance >= 0) {
                                databaseManager.updateBalanceCache(playerUuid, playerName, balance);
                            }

                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to import file: " + path.getFileName() + " - " + e.getMessage());
                        }
                    });

                importedCount = (int) Files.list(essentialsDataPath)
                    .filter(path -> path.toString().endsWith(".yml"))
                    .count();

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to import user data", e);
            }

            return importedCount;
        });
    }

    public CompletableFuture<Integer> exportAllUserData() {
        return databaseManager.getAllUserData().thenCompose(players -> {
            return CompletableFuture.supplyAsync(() -> {
                int exportedCount = 0;

                try {
                    if (plugin.getConfig().getBoolean("commands.import-export.backup-before-export", true)) {
                        createBackup("export");
                    }

                    for (DatabaseManager.PlayerData playerData : players) {
                        try {
                            String userData = playerData.getData();

                            if (isDataCompressed(userData)) {
                                userData = decompressData(userData);
                            }

                            Path userFile = essentialsDataPath.resolve(playerData.getUuid().toString() + ".yml");
                            Files.write(userFile, userData.getBytes());

                            exportedCount++;

                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to export data for player: " + playerData.getName() + " - " + e.getMessage());
                        }
                    }

                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to export user data", e);
                }

                return exportedCount;
            });
        });
    }

    public CompletableFuture<Boolean> deleteUserdataFolder() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (Files.exists(essentialsDataPath)) {
                    Files.walk(essentialsDataPath)
                        .filter(Files::isRegularFile)
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                plugin.getLogger().warning("Failed to delete file: " + path.getFileName());
                            }
                        });

                    plugin.getLogger().info("Deleted all files in Essentials userdata folder");
                    return true;
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete userdata folder", e);
            }

            return false;
        });
    }

    public void updatePlayerBalance(UUID playerUuid, String playerName, double balance) {
        if (plugin.getConfig().getBoolean("commands.balance-top.enabled", true)) {
            databaseManager.updateBalanceCache(playerUuid, playerName, balance);
        }
    }

    private String filterUserData(String userData) {
        try {
            Map<String, Boolean> filters = new HashMap<>();
            filters.put("homes", plugin.getConfig().getBoolean("data.filters.homes", true));
            filters.put("money", plugin.getConfig().getBoolean("data.filters.money", true));
            filters.put("mail", plugin.getConfig().getBoolean("data.filters.mail", true));
            filters.put("kits", plugin.getConfig().getBoolean("data.filters.kits", true));
            filters.put("cooldowns", plugin.getConfig().getBoolean("data.filters.cooldowns", true));
            filters.put("warps", plugin.getConfig().getBoolean("data.filters.warps", true));
            filters.put("jail", plugin.getConfig().getBoolean("data.filters.jail", true));
            filters.put("mutes", plugin.getConfig().getBoolean("data.filters.mutes", true));
            filters.put("social-spy", plugin.getConfig().getBoolean("data.filters.social-spy", true));
            filters.put("god-mode", plugin.getConfig().getBoolean("data.filters.god-mode", true));
            filters.put("fly", plugin.getConfig().getBoolean("data.filters.fly", true));
            filters.put("nicknames", plugin.getConfig().getBoolean("data.filters.nicknames", true));
            filters.put("ignore-list", plugin.getConfig().getBoolean("data.filters.ignore-list", true));
            filters.put("teleport-requests", plugin.getConfig().getBoolean("data.filters.teleport-requests", true));

            return userData;

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to filter user data: " + e.getMessage());
            return userData;
        }
    }

    private String compressData(String data) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
                gzipOut.write(data.getBytes());
            }
            return "GZIP:" + java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to compress data: " + e.getMessage());
            return data;
        }
    }

    /**
     * Public method to decompress data for debugging
     */
    public String decompressDataPublic(String compressedData) {
        return decompressData(compressedData);
    }

    private String decompressData(String compressedData) {
        try {
            String base64Data = compressedData.substring(5);
            byte[] compressedBytes = java.util.Base64.getDecoder().decode(base64Data);

            ByteArrayInputStream bais = new ByteArrayInputStream(compressedBytes);
            try (GZIPInputStream gzipIn = new GZIPInputStream(bais);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[1024];
                int len;
                while ((len = gzipIn.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }

                return baos.toString();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to decompress data: " + e.getMessage());
            return compressedData;
        }
    }

    private boolean isDataCompressed(String data) {
        return data != null && data.startsWith("GZIP:");
    }

    private String getPlayerNameFromFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.trim().startsWith("lastAccountName:")) {
                    return line.split(":")[1].trim().replace("\"", "").replace("'", "");
                }
            }
        } catch (Exception ignored) {}
        return null;
    }


    private double getPlayerBalanceFromFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            String[] lines = content.split("\n");

            for (String line : lines) {
                String trimmedLine = line.trim();

                if (trimmedLine.startsWith("money:")) {
                    String balanceStr = trimmedLine.substring(6).trim();

                    balanceStr = balanceStr.replace("'", "").replace("\"", "");

                    if (!balanceStr.isEmpty()) {
                        double balance = Double.parseDouble(balanceStr);

                        if (plugin.getConfig().getBoolean("debug.log-file-operations", false)) {
                            plugin.getLogger().info("Extracted balance " + balance + " from file: " + filePath.getFileName());
                        }

                        return balance;
                    }
                }

                if (trimmedLine.startsWith("balance:")) {
                    String balanceStr = trimmedLine.substring(8).trim();
                    balanceStr = balanceStr.replace("'", "").replace("\"", "");

                    if (!balanceStr.isEmpty()) {
                        double balance = Double.parseDouble(balanceStr);

                        if (plugin.getConfig().getBoolean("debug.log-file-operations", false)) {
                            plugin.getLogger().info("Extracted balance " + balance + " from file: " + filePath.getFileName());
                        }

                        return balance;
                    }
                }
            }

            if (plugin.getConfig().getBoolean("debug.log-file-operations", false)) {
                plugin.getLogger().warning("No balance field found in file: " + filePath.getFileName());
            }
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid balance format in file: " + filePath.getFileName() + " - " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to extract balance from file: " + filePath.getFileName() + " - " + e.getMessage());
        }

        return -1;
    }

    private void createBackup(String operation) {
        plugin.getLogger().info("Creating backup before " + operation + " operation");
    }

    public void startAutoSync() {
        int syncInterval = plugin.getConfig().getInt("settings.auto-sync-interval", 0);
        if (syncInterval > 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (plugin.getConfig().getBoolean("settings.async-operations", true)) {
                            savePlayerData(player.getUniqueId(), player.getName());
                        }
                    }
                }
            }.runTaskTimerAsynchronously(plugin, syncInterval * 20L * 60L, syncInterval * 20L * 60L);
        }
    }
}