package dk.noxitech.essentialssql.listeners;

import dk.noxitech.essentialssql.Main;
import dk.noxitech.essentialssql.database.DatabaseManager;
import dk.noxitech.essentialssql.manager.UserDataManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.CompletableFuture;

public class PlayerDataListener implements Listener {

    private final Main plugin;
    private final UserDataManager userDataManager;
    private final DatabaseManager databaseManager;

    public PlayerDataListener(Main plugin, UserDataManager userDataManager) {
        this.plugin = plugin;
        this.userDataManager = userDataManager;
        this.databaseManager = plugin.getDatabaseManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("settings.load-on-join", true)) {
            return;
        }

        Player player = event.getPlayer();

        String loadingMessage = plugin.getConfig().getString("messages.data-loading", "&aLoading your data...");
        if (!loadingMessage.isEmpty()) {
            player.sendMessage(plugin.colorize(loadingMessage));
        }

        if (plugin.getConfig().getBoolean("settings.async-operations", true)) {
            CompletableFuture<Boolean> loadFuture = userDataManager.loadPlayerData(player.getUniqueId(), player.getName());

            loadFuture.whenComplete((success, throwable) -> {
                if (throwable != null) {
                    plugin.getLogger().severe("Error loading data for player " + player.getName() + ": " + throwable.getMessage());

                    String errorMessage = plugin.getConfig().getString("messages.data-load-failed", "&cFailed to load your data!");
                    if (!errorMessage.isEmpty()) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (player.isOnline()) {
                                    player.sendMessage(plugin.colorize(errorMessage));
                                }
                            }
                        }.runTask(plugin);
                    }
                    return;
                }

                if (success) {
                    plugin.getLogger().info("Successfully loaded data for player " + player.getName());

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            try {
                                reloadEssentialsUserData(player);

                                loadPlayerBalanceFromDatabase(player);
                            } catch (Exception e) {
                                plugin.getLogger().warning("Failed to reload Essentials data for " + player.getName() + ": " + e.getMessage());
                            }
                        }
                    }.runTaskLater(plugin, 40L);

                } else {
                    plugin.getLogger().info("No existing data found for new player " + player.getName());
                }
            });

        } else {
            try {
                boolean success = userDataManager.loadPlayerData(player.getUniqueId(), player.getName()).join();
                if (success) {
                    plugin.getLogger().info("Successfully loaded data for player " + player.getName());

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            try {
                                reloadEssentialsUserData(player);

                                loadPlayerBalanceFromDatabase(player);
                            } catch (Exception e) {
                                plugin.getLogger().warning("Failed to reload Essentials data for " + player.getName() + ": " + e.getMessage());
                            }
                        }
                    }.runTaskLater(plugin, 40L);

                } else {
                    plugin.getLogger().info("No existing data found for new player " + player.getName());
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error loading data for player " + player.getName() + ": " + e.getMessage());

                String errorMessage = plugin.getConfig().getString("messages.data-load-failed", "&cFailed to load your data!");
                if (!errorMessage.isEmpty()) {
                    player.sendMessage(plugin.colorize(errorMessage));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!plugin.getConfig().getBoolean("settings.save-on-quit", true)) {
            plugin.getLogger().info("[DEBUG] Save on quit disabled - skipping save for " + event.getPlayer().getName());
            return;
        }

        Player player = event.getPlayer();
        plugin.getLogger().info("[DEBUG] Player quit event triggered for " + player.getName() + " - starting data save");

        if (plugin.getConfig().getBoolean("settings.async-operations", true)) {
            plugin.getLogger().info("[DEBUG] Starting async save for " + player.getName());
            CompletableFuture<Boolean> saveFuture = userDataManager.savePlayerData(player.getUniqueId(), player.getName());

            saveFuture.whenComplete((success, throwable) -> {
                if (throwable != null) {
                    plugin.getLogger().severe("Error saving data for player " + player.getName() + ": " + throwable.getMessage());
                    return;
                }

                if (success) {
                    plugin.getLogger().info("Successfully saved data for player " + player.getName());

                    updatePlayerBalanceCache(player);
                } else {
                    plugin.getLogger().warning("Failed to save data for player " + player.getName());
                }
            });

        } else {
            try {
                boolean success = userDataManager.savePlayerData(player.getUniqueId(), player.getName()).join();
                if (success) {
                    plugin.getLogger().info("Successfully saved data for player " + player.getName());

                    savePlayerBalanceToDatabase(player);

                } else {
                    plugin.getLogger().warning("Failed to save data for player " + player.getName());
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error saving data for player " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    private void reloadEssentialsUserData(Player player) {
        try {
            org.bukkit.plugin.Plugin essentialsPlugin = plugin.getServer().getPluginManager().getPlugin("Essentials");
            if (essentialsPlugin != null && essentialsPlugin.isEnabled()) {
                if (plugin.getConfig().getBoolean("debug.log-file-operations", false)) {
                    plugin.getLogger().info("Reloading Essentials data for player " + player.getName());
                }
            } else {
                plugin.getLogger().warning("Essentials plugin not found or not enabled!");
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to reload Essentials user data: " + e.getMessage());
        }
    }

    private void updatePlayerBalanceCache(Player player) {
        try {
            org.bukkit.plugin.Plugin essentialsPlugin = plugin.getServer().getPluginManager().getPlugin("Essentials");
            if (essentialsPlugin != null && essentialsPlugin.isEnabled()) {
                double balance = 0.0;

                if (plugin.getServer().getPluginManager().getPlugin("Vault") != null) {
                    Economy economy = plugin.getEconomy();
                    if (economy != null) {
                        balance = economy.getBalance(player);
                    }
                }

                userDataManager.updatePlayerBalance(player.getUniqueId(), player.getName(), balance);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update balance cache: " + e.getMessage());
        }
    }

    private void syncPlayerBalance(Player player) {
        try {
            double balance = 0.0;

            if (plugin.getServer().getPluginManager().getPlugin("Vault") != null) {
                Economy economy = plugin.getEconomy();
                if (economy != null) {
                    balance = economy.getBalance(player);
                    userDataManager.updatePlayerBalance(player.getUniqueId(), player.getName(), balance);

                    if (plugin.getConfig().getBoolean("debug.log-database-operations", false)) {
                        plugin.getLogger().info("Synced balance " + balance + " for player " + player.getName());
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to sync balance for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void loadPlayerBalanceFromDatabase(Player player) {
        try {
            databaseManager.getTopBalances(1000).whenComplete((balances, throwable) -> {
                if (throwable != null) {
                    plugin.getLogger().warning("Failed to load balance for " + player.getName() + ": " + throwable.getMessage());
                    return;
                }

                for (DatabaseManager.BalanceEntry entry : balances) {
                    if (entry.getUuid().equals(player.getUniqueId())) {
                        if (plugin.getServer().getPluginManager().getPlugin("Vault") != null) {
                            Economy economy = plugin.getEconomy();
                            if (economy != null) {
                                double currentBalance = economy.getBalance(player);
                                double databaseBalance = entry.getBalance();

                                if (Math.abs(currentBalance - databaseBalance) > 0.01) {
                                    economy.withdrawPlayer(player, currentBalance);
                                    economy.depositPlayer(player, databaseBalance);

                                    if (plugin.getConfig().getBoolean("debug.log-database-operations", false)) {
                                        plugin.getLogger().info("Loaded balance " + databaseBalance + " from database for player " + player.getName());
                                    }
                                }
                            }
                        }
                        return;
                    }
                }

                if (plugin.getConfig().getBoolean("debug.log-database-operations", false)) {
                    plugin.getLogger().info("No balance found in database for player " + player.getName() + ", keeping current balance");
                }
                syncPlayerBalance(player);
            });
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load balance from database for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void savePlayerBalanceToDatabase(Player player) {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("Vault") != null) {
                Economy economy = plugin.getEconomy();
                if (economy != null) {
                    double balance = economy.getBalance(player);
                    userDataManager.updatePlayerBalance(player.getUniqueId(), player.getName(), balance);

                    if (plugin.getConfig().getBoolean("debug.log-database-operations", false)) {
                        plugin.getLogger().info("Saved balance " + balance + " to database for player " + player.getName());
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save balance to database for " + player.getName() + ": " + e.getMessage());
        }
    }
}