package dk.noxitech.essentialssql.commands;

import dk.noxitech.essentialssql.Main;
import dk.noxitech.essentialssql.database.DatabaseManager;
import dk.noxitech.essentialssql.manager.UserDataManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EssentialsSQLCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final UserDataManager userDataManager;
    private final DatabaseManager databaseManager;
    private final DecimalFormat balanceFormat = new DecimalFormat("#,##0.00");

    public EssentialsSQLCommand(Main plugin, UserDataManager userDataManager, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.userDataManager = userDataManager;
        this.databaseManager = databaseManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "import":
                handleImport(sender, args);
                break;
            case "export":
                handleExport(sender, args);
                break;
            case "forcesave":
                handleForceSave(sender, args);
                break;
            case "viewdata":
                handleViewData(sender, args);
                break;
            case "baltop":
            case "balancetop":
                handleBalanceTop(sender, args);
                break;
            case "cleanup":
                handleCleanup(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "status":
                handleStatus(sender);
                break;
            case "sync":
                handleSync(sender, args);
                break;
            case "delete":
                handleDelete(sender, args);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleImport(CommandSender sender, String[] args) {
        if (!sender.hasPermission("essentialssql.admin.import")) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission", "&cYou don't have permission to use this command!")));
            return;
        }

        sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &aStarting import of all userdata files..."));

        CompletableFuture<Integer> importFuture = userDataManager.importAllUserData();

        importFuture.whenComplete((count, throwable) -> {
            if (throwable != null) {
                sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cImport failed: " + throwable.getMessage()));
                return;
            }

            String message = plugin.getConfig().getString("messages.import-success", "&aSuccessfully imported {count} players!")
                .replace("{count}", String.valueOf(count));
            sender.sendMessage(plugin.colorize(message));
        });
    }

    private void handleExport(CommandSender sender, String[] args) {
        if (!sender.hasPermission("essentialssql.admin.export")) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission", "&cYou don't have permission to use this command!")));
            return;
        }

        sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &aStarting export of all database data..."));

        CompletableFuture<Integer> exportFuture = userDataManager.exportAllUserData();

        exportFuture.whenComplete((count, throwable) -> {
            if (throwable != null) {
                sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cExport failed: " + throwable.getMessage()));
                return;
            }

            String message = plugin.getConfig().getString("messages.export-success", "&aSuccessfully exported {count} players!")
                .replace("{count}", String.valueOf(count));
            sender.sendMessage(plugin.colorize(message));
        });
    }

    private void handleBalanceTop(CommandSender sender, String[] args) {
        if (!plugin.getConfig().getBoolean("commands.balance-top.enabled", true)) {
            sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cBalance top is disabled in the configuration!"));
            return;
        }

        if (!sender.hasPermission("essentialssql.baltop")) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission", "&cYou don't have permission to use this command!")));
            return;
        }

        int limit = 10;
        if (args.length > 1) {
            try {
                limit = Integer.parseInt(args[1]);
                if (limit < 1 || limit > 50) {
                    sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cLimit must be between 1 and 50!"));
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cInvalid number format!"));
                return;
            }
        }

        sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &aLoading balance top..."));

        CompletableFuture<List<DatabaseManager.BalanceEntry>> balanceFuture = databaseManager.getTopBalances(limit);

        balanceFuture.whenComplete((balances, throwable) -> {
            if (throwable != null) {
                sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cFailed to load balance top: " + throwable.getMessage()));
                return;
            }

            if (balances.isEmpty()) {
                sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cNo balance data found!"));
                return;
            }

            sender.sendMessage(plugin.colorize("&6&l=== &eBalance Top &6&l==="));

            for (int i = 0; i < balances.size(); i++) {
                DatabaseManager.BalanceEntry entry = balances.get(i);
                String position = String.valueOf(i + 1);
                String balance = balanceFormat.format(entry.getBalance());

                sender.sendMessage(plugin.colorize(String.format("&6%s. &f%s &7- &a$%s", 
                    position, entry.getName(), balance)));
            }
        });
    }

    private void handleCleanup(CommandSender sender, String[] args) {
        if (!sender.hasPermission("essentialssql.admin.cleanup")) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission", "&cYou don't have permission to use this command!")));
            return;
        }

        int days = plugin.getConfig().getInt("cleanup.inactive-days", 365);
        if (args.length > 1) {
            try {
                days = Integer.parseInt(args[1]);
                if (days < 1) {
                    sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cDays must be greater than 0!"));
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cInvalid number format!"));
                return;
            }
        }

        sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &aStarting cleanup of players inactive for " + days + " days..."));

        CompletableFuture<Integer> cleanupFuture = databaseManager.cleanupInactiveUsers(days);

        cleanupFuture.whenComplete((count, throwable) -> {
            if (throwable != null) {
                sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cCleanup failed: " + throwable.getMessage()));
                return;
            }

            String message = plugin.getConfig().getString("messages.cleanup-success", "&aCleanup completed! Removed {count} inactive players.")
                .replace("{count}", String.valueOf(count));
            sender.sendMessage(plugin.colorize(message));
        });
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("essentialssql.admin.reload")) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission", "&cYou don't have permission to use this command!")));
            return;
        }

        try {
            plugin.reloadConfig();
            sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &aConfiguration reloaded successfully!"));
        } catch (Exception e) {
            sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cFailed to reload configuration: " + e.getMessage()));
        }
    }

    private void handleStatus(CommandSender sender) {
        if (!sender.hasPermission("essentialssql.admin.status")) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission", "&cYou don't have permission to use this command!")));
            return;
        }

        sender.sendMessage(plugin.colorize("&6&l=== &eEssentialsSQL Status &6&l==="));

        boolean dbConnected = databaseManager.isConnected();
        sender.sendMessage(plugin.colorize("&7Database: " + (dbConnected ? "&aConnected" : "&cDisconnected")));

        sender.sendMessage(plugin.colorize("&7Save on quit: " + (plugin.getConfig().getBoolean("settings.save-on-quit") ? "&aEnabled" : "&cDisabled")));
        sender.sendMessage(plugin.colorize("&7Load on join: " + (plugin.getConfig().getBoolean("settings.load-on-join") ? "&aEnabled" : "&cDisabled")));
        sender.sendMessage(plugin.colorize("&7Async operations: " + (plugin.getConfig().getBoolean("settings.async-operations") ? "&aEnabled" : "&cDisabled")));
        sender.sendMessage(plugin.colorize("&7Data compression: " + (plugin.getConfig().getBoolean("data.compress-data") ? "&aEnabled" : "&cDisabled")));
        sender.sendMessage(plugin.colorize("&7Cleanup enabled: " + (plugin.getConfig().getBoolean("cleanup.enabled") ? "&aEnabled" : "&cDisabled")));

        boolean essentialsFound = plugin.getServer().getPluginManager().getPlugin("Essentials") != null;
        sender.sendMessage(plugin.colorize("&7Essentials: " + (essentialsFound ? "&aFound" : "&cNot found")));
    }

    private void handleSync(CommandSender sender, String[] args) {
        if (!sender.hasPermission("essentialssql.admin.sync")) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission", "&cYou don't have permission to use this command!")));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cUsage: /esql sync <player>"));
            return;
        }

        String playerName = args[1];
        Player target = plugin.getServer().getPlayer(playerName);

        if (target == null) {
            sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cPlayer not found!"));
            return;
        }

        sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &aSyncing data for player " + target.getName() + "..."));

        CompletableFuture<Boolean> syncFuture = userDataManager.savePlayerData(target.getUniqueId(), target.getName());

        syncFuture.whenComplete((success, throwable) -> {
            if (throwable != null) {
                sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cSync failed: " + throwable.getMessage()));
                return;
            }

            if (success) {
                sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &aSuccessfully synced data for " + target.getName()));
            } else {
                sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cFailed to sync data for " + target.getName()));
            }
        });
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("essentialssql.admin.delete")) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission", "&cYou don't have permission to use this command!")));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cUsage: /esql delete <userdata|cache>"));
            return;
        }

        String type = args[1].toLowerCase();

        if ("userdata".equals(type)) {
            sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &aDeleting entire userdata folder..."));

            CompletableFuture<Boolean> deleteFuture = userDataManager.deleteUserdataFolder();

            deleteFuture.whenComplete((success, throwable) -> {
                if (throwable != null) {
                    sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cFailed to delete userdata folder: " + throwable.getMessage()));
                    return;
                }

                if (success) {
                    sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &aUserdata folder deleted successfully!"));
                } else {
                    sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cFailed to delete userdata folder!"));
                }
            });

        } else {
            sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cInvalid option! Use: userdata"));
        }
    }

    private void handleForceSave(CommandSender sender, String[] args) {
        if (!sender.hasPermission("essentialssql.admin.sync")) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission", "&cYou don't have permission to use this command!")));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cUsage: /esql forcesave <player>"));
            return;
        }

        String playerName = args[1];
        Player target = plugin.getServer().getPlayer(playerName);

        if (target == null) {
            sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cPlayer not found!"));
            return;
        }

        sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &aForce saving data for player " + target.getName() + "..."));

        CompletableFuture<Boolean> saveFuture = userDataManager.savePlayerData(target.getUniqueId(), target.getName());

        saveFuture.whenComplete((success, throwable) -> {
            if (throwable != null) {
                sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cForce save failed: " + throwable.getMessage()));
                return;
            }

            if (success) {
                sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &aSuccessfully force saved data for " + target.getName()));
            } else {
                sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cFailed to force save data for " + target.getName()));
            }
        });
    }

    private void handleViewData(CommandSender sender, String[] args) {
        if (!sender.hasPermission("essentialssql.admin.status")) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission", "&cYou don't have permission to use this command!")));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cUsage: /esql viewdata <player>"));
            return;
        }

        String playerName = args[1];
        sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &aLooking up data for player " + playerName + "..."));

        CompletableFuture<Void> viewFuture = databaseManager.getAllUserData().thenAccept(players -> {
            DatabaseManager.PlayerData foundPlayer = null;
            for (DatabaseManager.PlayerData playerData : players) {
                if (playerData.getName().equalsIgnoreCase(playerName)) {
                    foundPlayer = playerData;
                    break;
                }
            }

            if (foundPlayer == null) {
                sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cNo data found for player " + playerName));
                return;
            }

            String data = foundPlayer.getData();
            sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &eData for " + foundPlayer.getName() + ":"));
            sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &7UUID: " + foundPlayer.getUuid()));
            sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &7Data Size: " + data.length() + " bytes"));

            if (data.startsWith("GZIP:")) {
                sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &7Compressed: Yes"));
                try {
                    String decompressed = userDataManager.decompressDataPublic(data);
                    sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &7Decompressed Size: " + decompressed.length() + " bytes"));

                    if (decompressed.contains("homes:")) {
                        sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &a✓ Contains HOMES data"));

                        String[] lines = decompressed.split("\n");
                        boolean inHomes = false;
                        int homeCount = 0;
                        StringBuilder homesInfo = new StringBuilder();

                        for (String line : lines) {
                            if (line.trim().startsWith("homes:")) {
                                inHomes = true;
                                homesInfo.append("&7Homes found:\n");
                                continue;
                            }

                            if (inHomes) {
                                if (line.startsWith("  ") && !line.startsWith("    ")) {
                                    String homeName = line.trim().replace(":", "");
                                    if (!homeName.isEmpty()) {
                                        homeCount++;
                                        homesInfo.append("&7- &e").append(homeName).append("\n");
                                    }
                                } else if (!line.startsWith(" ")) {
                                    break;
                                }
                            }
                        }

                        sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &a✓ Found " + homeCount + " homes"));
                        if (homeCount > 0) {
                            for (String line : homesInfo.toString().split("\n")) {
                                if (!line.trim().isEmpty()) {
                                    sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r " + line));
                                }
                            }
                        }
                    } else {
                        sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &c✗ No HOMES data found"));
                    }

                    String preview = decompressed.length() > 300 ? decompressed.substring(0, 300) + "..." : decompressed;
                    sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &7Data Preview:"));
                    sender.sendMessage(plugin.colorize("&7" + preview.replace("\n", "\\n")));
                } catch (Exception e) {
                    sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cFailed to decompress data: " + e.getMessage()));
                }
            } else {
                sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &7Compressed: No"));

                if (data.contains("homes:")) {
                    sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &a✓ Contains HOMES data"));
                } else {
                    sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &c✗ No HOMES data found"));
                }

                String preview = data.length() > 300 ? data.substring(0, 300) + "..." : data;
                sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &7Data Preview:"));
                sender.sendMessage(plugin.colorize("&7" + preview.replace("\n", "\\n")));
            }
        });

        viewFuture.exceptionally(throwable -> {
            sender.sendMessage(plugin.colorize("&7[&bEssentialsSQL&7]&r &cFailed to view data: " + throwable.getMessage()));
            return null;
        });
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.colorize("&6&l=== &eEssentialsSQL Commands &6&l==="));

        if (sender.hasPermission("essentialssql.admin.import")) {
            sender.sendMessage(plugin.colorize("&e/esql import &7- Import all userdata files to database"));
        }

        if (sender.hasPermission("essentialssql.admin.export")) {
            sender.sendMessage(plugin.colorize("&e/esql export &7- Export all database data to userdata files"));
        }

        if (sender.hasPermission("essentialssql.baltop")) {
            sender.sendMessage(plugin.colorize("&e/esql baltop [limit] &7- Show balance top from database"));
        }

        if (sender.hasPermission("essentialssql.admin.cleanup")) {
            sender.sendMessage(plugin.colorize("&e/esql cleanup [days] &7- Remove inactive players from database"));
        }

        if (sender.hasPermission("essentialssql.admin.sync")) {
            sender.sendMessage(plugin.colorize("&e/esql sync <player> &7- Manually sync player data"));
            sender.sendMessage(plugin.colorize("&e/esql forcesave <player> &7- Force save player data to database"));
        }

        if (sender.hasPermission("essentialssql.admin.delete")) {
            sender.sendMessage(plugin.colorize("&e/esql delete userdata &7- Delete entire userdata folder"));
        }

        if (sender.hasPermission("essentialssql.admin.status")) {
            sender.sendMessage(plugin.colorize("&e/esql status &7- Show plugin status"));
            sender.sendMessage(plugin.colorize("&e/esql viewdata <player> &7- View player's stored data"));
        }

        if (sender.hasPermission("essentialssql.admin.reload")) {
            sender.sendMessage(plugin.colorize("&e/esql reload &7- Reload plugin configuration"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("import", "export", "baltop", "cleanup", "reload", "status", "sync", "delete", "forcesave", "viewdata");

            for (String subCommand : subCommands) {
                if (subCommand.startsWith(args[0].toLowerCase())) {
                    String permission = "essentialssql.admin." + subCommand;
                    if ("baltop".equals(subCommand)) {
                        permission = "essentialssql.baltop";
                    }

                    if (sender.hasPermission(permission)) {
                        completions.add(subCommand);
                    }
                }
            }
        } else if (args.length == 2) {
            if ("sync".equalsIgnoreCase(args[0]) || "forcesave".equalsIgnoreCase(args[0]) || "viewdata".equalsIgnoreCase(args[0])) {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            } else if ("delete".equalsIgnoreCase(args[0])) {
                if ("userdata".startsWith(args[1].toLowerCase())) {
                    completions.add("userdata");
                }
            }
        }

        return completions;
    }
}