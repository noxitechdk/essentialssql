package dk.noxitech.essentialssql.listeners;

import dk.noxitech.essentialssql.Main;
import dk.noxitech.essentialssql.manager.UserDataManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.List;

public class EconomyListener implements Listener {

    private final Main plugin;
    private final UserDataManager userDataManager;

    private final List<String> economyCommands = Arrays.asList(
        "pay", "money", "balance", "bal", "eco", "economy", 
        "give", "take", "set", "essentials:pay", "essentials:eco",
        "essentials:balance", "essentials:money"
    );

    public EconomyListener(Main plugin, UserDataManager userDataManager) {
        this.plugin = plugin;
        this.userDataManager = userDataManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEconomyCommand(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().toLowerCase();

        boolean isEconomyCommand = false;
        for (String ecoCmd : economyCommands) {
            if (command.startsWith("/" + ecoCmd)) {
                isEconomyCommand = true;
                break;
            }
        }

        if (!isEconomyCommand) {
            return;
        }

        Player player = event.getPlayer();

        new BukkitRunnable() {
            @Override
            public void run() {
                syncPlayerBalance(player);

                syncTargetPlayerBalance(command, player);
            }
        }.runTaskLater(plugin, 2L);
    }

    private void syncPlayerBalance(Player player) {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("Vault") != null) {
                Economy economy = plugin.getEconomy();
                if (economy != null) {
                    double balance = economy.getBalance(player);
                    userDataManager.updatePlayerBalance(player.getUniqueId(), player.getName(), balance);

                    if (plugin.getConfig().getBoolean("debug.log-database-operations", false)) {
                        plugin.getLogger().info("Economy command: Synced balance " + balance + " for player " + player.getName());
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to sync balance after economy command for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void syncTargetPlayerBalance(String command, Player sender) {
        try {
            String[] parts = command.split(" ");

            if (parts.length >= 3) {
                String targetPlayerName = null;

                if (command.contains("pay ")) {
                    targetPlayerName = parts[1];
                } else if (command.contains("eco ") && parts.length >= 4) {
                    if (parts[1].equals("give") || parts[1].equals("take") || parts[1].equals("set")) {
                        targetPlayerName = parts[2];
                    }
                } else if (command.contains("essentials:pay ")) {
                    targetPlayerName = parts[1];
                } else if (command.contains("essentials:eco ") && parts.length >= 4) {
                    if (parts[1].equals("give") || parts[1].equals("take") || parts[1].equals("set")) {
                        targetPlayerName = parts[2];
                    }
                }

                if (targetPlayerName != null) {
                    Player targetPlayer = plugin.getServer().getPlayer(targetPlayerName);
                    if (targetPlayer != null && targetPlayer.isOnline()) {
                        if (plugin.getServer().getPluginManager().getPlugin("Vault") != null) {
                            Economy economy = plugin.getEconomy();
                            if (economy != null) {
                                double balance = economy.getBalance(targetPlayer);
                                userDataManager.updatePlayerBalance(targetPlayer.getUniqueId(), targetPlayer.getName(), balance);

                                if (plugin.getConfig().getBoolean("debug.log-database-operations", false)) {
                                    plugin.getLogger().info("Economy command: Synced target balance " + balance + " for player " + targetPlayer.getName());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to sync target player balance: " + e.getMessage());
        }
    }
}