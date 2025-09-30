package dk.noxitech.essentialssql;

import dk.noxitech.essentialssql.commands.EssentialsSQLCommand;
import dk.noxitech.essentialssql.database.DatabaseManager;
import dk.noxitech.essentialssql.integration.ShopIntegrationManager;
import dk.noxitech.essentialssql.listeners.EconomyListener;
import dk.noxitech.essentialssql.listeners.PlayerDataListener;
import dk.noxitech.essentialssql.manager.UserDataManager;
import dk.noxitech.essentialssql.utils.CleanupManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class Main extends JavaPlugin {

    private DatabaseManager databaseManager;
    private UserDataManager userDataManager;
    private CleanupManager cleanupManager;
    private ShopIntegrationManager shopIntegrationManager;

    private Economy economy;
    
    @Override
    public void onEnable() {
        printStartupBanner();
        saveDefaultConfig();

        if (!initializeDatabase()) {
            getLogger().severe("Failed to initialize database! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        initializeComponents();

        registerCommandsAndListeners();

        setupExternalIntegrations();

        startBackgroundTasks();
        
        getLogger().info("EssentialsSQL has been enabled successfully!");

        if (getServer().getPluginManager().getPlugin("Essentials") == null) {
            getLogger().warning("Essentials plugin not found! This plugin requires Essentials to function properly.");
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down EssentialsSQL...");

        if (cleanupManager != null) {
            cleanupManager.shutdown();
        }

        if (shopIntegrationManager != null) {
            shopIntegrationManager.shutdown();
        }

        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        
        getLogger().info("EssentialsSQL has been disabled!");
    }

    private void printStartupBanner() {
        getLogger().info("=================================");
        getLogger().info("    EssentialsSQL v" + getDescription().getVersion());
        getLogger().info("    MySQL storage for Essentials");
        getLogger().info("=================================");
    }

    private boolean initializeDatabase() {
        if (!getConfig().getBoolean("database.enabled", true)) {
            getLogger().warning("Database is disabled in configuration!");
            return false;
        }
        
        try {
            databaseManager = new DatabaseManager(this);
            return databaseManager.initialize();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize database", e);
            return false;
        }
    }

    private void initializeComponents() {
        getLogger().info("Initializing plugin components...");

        userDataManager = new UserDataManager(this, databaseManager);

        cleanupManager = new CleanupManager(this, databaseManager, userDataManager);
        cleanupManager.initialize();

        shopIntegrationManager = new ShopIntegrationManager(this, databaseManager);
        
        getLogger().info("All components initialized successfully!");
    }

    private void registerCommandsAndListeners() {
        getLogger().info("Registering commands and listeners...");

        EssentialsSQLCommand commandExecutor = new EssentialsSQLCommand(this, userDataManager, databaseManager);
        getCommand("essentialssql").setExecutor(commandExecutor);
        getCommand("essentialssql").setTabCompleter(commandExecutor);

        getServer().getPluginManager().registerEvents(new PlayerDataListener(this, userDataManager), this);
        getServer().getPluginManager().registerEvents(new EconomyListener(this, userDataManager), this);
        getServer().getPluginManager().registerEvents(shopIntegrationManager, this);
        
        getLogger().info("Commands and listeners registered!");
    }

    private void setupExternalIntegrations() {
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
                getLogger().info("Vault economy integration enabled");
            } else {
                getLogger().warning("Vault found but no economy provider detected");
            }
        } else {
            getLogger().info("Vault not found - balance top may not work properly");
        }
    }

    private void startBackgroundTasks() {
        getLogger().info("Starting background tasks...");

        userDataManager.startAutoSync();

        getLogger().info("Background tasks started!");
    }

    public String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public Economy getEconomy() {
        return economy;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public UserDataManager getUserDataManager() {
        return userDataManager;
    }

    public CleanupManager getCleanupManager() {
        return cleanupManager;
    }

    public ShopIntegrationManager getShopIntegrationManager() {
        return shopIntegrationManager;
    }

    public boolean isInitialized() {
        return databaseManager != null && 
               userDataManager != null && 
               cleanupManager != null && 
               shopIntegrationManager != null;
    }

    public String getPluginStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("=== EssentialsSQL Statistics ===\n");
        stats.append("Database Connected: ").append(databaseManager != null && databaseManager.isConnected()).append("\n");
        stats.append("Shop Integrations: ").append(shopIntegrationManager != null && shopIntegrationManager.hasActiveIntegrations()).append("\n");
        stats.append("Economy Provider: ").append(economy != null ? economy.getName() : "None").append("\n");
        stats.append("Plugin Version: ").append(getDescription().getVersion()).append("\n");
        stats.append("Bukkit Version: ").append(getServer().getBukkitVersion()).append("\n");
        
        return stats.toString();
    }
}
