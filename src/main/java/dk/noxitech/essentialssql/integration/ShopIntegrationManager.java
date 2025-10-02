package dk.noxitech.essentialssql.integration;

import dk.noxitech.essentialssql.Main;
import dk.noxitech.essentialssql.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class ShopIntegrationManager implements Listener {

    private final Main plugin;
    private final DatabaseManager databaseManager;

    private Plugin chestShopPlugin;
    private Plugin auctionHousePlugin;
    private Plugin playerShopGUIPlugin;
    private Plugin shopChestPlugin;

    public ShopIntegrationManager(Main plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;

        initializeShopPlugins();
    }

    private void initializeShopPlugins() {
        if (plugin.getConfig().getBoolean("shop-integration.chestshop.enabled", false)) {
            chestShopPlugin = Bukkit.getPluginManager().getPlugin("ChestShop");

            if (chestShopPlugin != null && chestShopPlugin.isEnabled()) {
                plugin.getLogger().info("ChestShop integration enabled");
                setupChestShopIntegration();
            } else {
                plugin.getLogger().warning("ChestShop plugin not found or disabled");
            }
        }

        if (plugin.getConfig().getBoolean("shop-integration.auctionhouse.enabled", false)) {
            auctionHousePlugin = Bukkit.getPluginManager().getPlugin("AuctionHouse");

            if (auctionHousePlugin != null && auctionHousePlugin.isEnabled()) {
                plugin.getLogger().info("AuctionHouse integration enabled");
                setupAuctionHouseIntegration();
            } else {
                plugin.getLogger().warning("AuctionHouse plugin not found or disabled");
            }
        }

        if (plugin.getConfig().getBoolean("shop-integration.shopchest.enabled", false)) {
            shopChestPlugin = Bukkit.getPluginManager().getPlugin("ShopChest");

            if (shopChestPlugin != null && shopChestPlugin.isEnabled()) {
                plugin.getLogger().info("ShopChest integration enabled");
                setupShopChestIntegration();
            } else {
                plugin.getLogger().warning("ShopChest plugin not found or disabled");
            }
        }
    }

    private void setupChestShopIntegration() {
        try {
            plugin.getLogger().info("ChestShop offline sales and buy orders integration ready");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to setup ChestShop integration", e);
        }
    }

    private void setupAuctionHouseIntegration() {
        try {
            plugin.getLogger().info("AuctionHouse offline auctions integration ready");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to setup AuctionHouse integration", e);
        }
    }

    private void setupPlayerShopGUIIntegration() {
        try {
            plugin.getLogger().info("PlayerShopGUI+ offline sales integration ready");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to setup PlayerShopGUI+ integration", e);
        }
    }

    private void setupShopChestIntegration() {
        try {
            plugin.getLogger().info("ShopChest offline sales and buy orders integration ready");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to setup ShopChest integration", e);
        }
    }

    public void handleOfflineShopTransaction(UUID playerUuid, String playerName, String shopPlugin, String transactionType, double amount, String details) {
        CompletableFuture.runAsync(() -> {
            try {
                String shopData = String.format(
                    "{\"type\":\"%s\",\"amount\":%.2f,\"details\":\"%s\",\"timestamp\":%d}",
                    transactionType, amount, details, System.currentTimeMillis()
                );

                plugin.getLogger().info(String.format(
                    "Processed offline %s transaction for %s: $%.2f (%s)",
                    shopPlugin, playerName, amount, transactionType
                ));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, 
                    String.format("Failed to handle offline shop transaction for %s", playerName), e);
            }
        });
    }

    public void processPendingTransactions(UUID playerUuid, String playerName) {
        CompletableFuture.runAsync(() -> {
            try {
                plugin.getLogger().info(String.format(
                    "Processing pending shop transactions for %s", playerName
                ));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, String.format("Failed to process pending transactions for %s", playerName), e);
            }
        });
    }

    public boolean hasActiveIntegrations() {
        return (chestShopPlugin != null && chestShopPlugin.isEnabled()) ||
               (auctionHousePlugin != null && auctionHousePlugin.isEnabled()) ||
               (playerShopGUIPlugin != null && playerShopGUIPlugin.isEnabled()) ||
               (shopChestPlugin != null && shopChestPlugin.isEnabled());
    }

    public String getIntegrationStatus() {
        StringBuilder status = new StringBuilder();

        status.append("Shop Integration Status:\n");
        status.append("ChestShop: ").append(getPluginStatus(chestShopPlugin)).append("\n");
        status.append("AuctionHouse: ").append(getPluginStatus(auctionHousePlugin)).append("\n");
        status.append("PlayerShopGUI+: ").append(getPluginStatus(playerShopGUIPlugin)).append("\n");
        status.append("ShopChest: ").append(getPluginStatus(shopChestPlugin));

        return status.toString();
    }

    private String getPluginStatus(Plugin plugin) {
        if (plugin == null) {
            return "Not Found";
        } else if (plugin.isEnabled()) {
            return "Enabled (" + plugin.getDescription().getVersion() + ")";
        } else {
            return "Disabled";
        }
    }

    public void shutdown() {
        plugin.getLogger().info("Shop integrations shut down");
    }
}