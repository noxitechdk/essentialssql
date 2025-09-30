package dk.noxitech.essentialssql.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dk.noxitech.essentialssql.Main;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class DatabaseManager {
    
    private final Main plugin;
    private HikariDataSource dataSource;
    private final String tablePrefix = "essentials_";
    
    public DatabaseManager(Main plugin) {
        this.plugin = plugin;
    }
    
    public boolean initialize() {
        try {
            setupDataSource();
            createTables();
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private void setupDataSource() {
        FileConfiguration config = plugin.getConfig();
        
        HikariConfig hikariConfig = new HikariConfig();
        
        String host = config.getString("database.host", "localhost");
        int port = config.getInt("database.port", 3306);
        String database = config.getString("database.database", "essentials");
        String username = config.getString("database.username", "root");
        String password = config.getString("database.password", "password");
        
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf8", 
                                     host, port, database);
        
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

        hikariConfig.setMinimumIdle(config.getInt("database.pool.minimum-idle", 2));
        hikariConfig.setMaximumPoolSize(config.getInt("database.pool.maximum-pool-size", 10));
        hikariConfig.setConnectionTimeout(config.getLong("database.pool.connection-timeout", 30000));
        hikariConfig.setIdleTimeout(config.getLong("database.pool.idle-timeout", 600000));
        hikariConfig.setMaxLifetime(config.getLong("database.pool.max-lifetime", 1800000));

        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
        hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
        hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
        hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
        hikariConfig.addDataSourceProperty("maintainTimeStats", "false");
        
        this.dataSource = new HikariDataSource(hikariConfig);
    }
    
    private void createTables() throws SQLException {
        String userDataTable = String.format("""
            CREATE TABLE IF NOT EXISTS %suser_data (
                id INT AUTO_INCREMENT PRIMARY KEY,
                player_uuid VARCHAR(36) NOT NULL UNIQUE,
                player_name VARCHAR(16) NOT NULL,
                data LONGTEXT NOT NULL,
                last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_player_uuid (player_uuid),
                INDEX idx_player_name (player_name),
                INDEX idx_last_login (last_login)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """, tablePrefix);
            
        String balanceTopTable = String.format("""
            CREATE TABLE IF NOT EXISTS %sbalance_cache (
                player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                player_name VARCHAR(16) NOT NULL,
                balance DECIMAL(20,2) NOT NULL,
                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_balance (balance DESC),
                INDEX idx_player_name (player_name)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """, tablePrefix);
            
        String shopDataTable = String.format("""
            CREATE TABLE IF NOT EXISTS %sshop_data (
                id INT AUTO_INCREMENT PRIMARY KEY,
                player_uuid VARCHAR(36) NOT NULL,
                plugin_name VARCHAR(32) NOT NULL,
                shop_data TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_player_uuid (player_uuid),
                INDEX idx_plugin_name (plugin_name)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """, tablePrefix);
        
        try (Connection conn = getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(userDataTable);
                stmt.execute(balanceTopTable);
                stmt.execute(shopDataTable);
            }
        }
        
        plugin.getLogger().info("Database tables created/verified successfully");
    }
    
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("DataSource is not initialized or has been closed");
        }
        return dataSource.getConnection();
    }
    
    public CompletableFuture<Boolean> saveUserData(UUID playerUuid, String playerName, String data) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = String.format("""
                INSERT INTO %suser_data (player_uuid, player_name, data) 
                VALUES (?, ?, ?) 
                ON DUPLICATE KEY UPDATE 
                player_name = VALUES(player_name), 
                data = VALUES(data), 
                last_login = CURRENT_TIMESTAMP
                """, tablePrefix);
                
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, playerName);
                stmt.setString(3, data);
                
                int rowsAffected = stmt.executeUpdate();
                
                if (plugin.getConfig().getBoolean("debug.log-database-operations", false)) {
                    plugin.getLogger().info(String.format("Saved data for player %s (%s), rows affected: %d", 
                                                        playerName, playerUuid, rowsAffected));
                }
                
                return rowsAffected > 0;
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, 
                    String.format("Failed to save data for player %s (%s)", playerName, playerUuid), e);
                return false;
            }
        });
    }
    
    public CompletableFuture<String> getUserData(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = String.format("SELECT data FROM %suser_data WHERE player_uuid = ?", tablePrefix);
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, playerUuid.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String data = rs.getString("data");
                        
                        if (plugin.getConfig().getBoolean("debug.log-database-operations", false)) {
                            plugin.getLogger().info(String.format("Loaded data for player %s", playerUuid));
                        }
                        
                        return data;
                    }
                }
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, 
                    String.format("Failed to load data for player %s", playerUuid), e);
            }
            
            return null;
        });
    }
    
    public CompletableFuture<Boolean> deleteUserData(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = String.format("DELETE FROM %suser_data WHERE player_uuid = ?", tablePrefix);
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, playerUuid.toString());
                int rowsAffected = stmt.executeUpdate();
                
                return rowsAffected > 0;
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, 
                    String.format("Failed to delete data for player %s", playerUuid), e);
                return false;
            }
        });
    }
    
    public CompletableFuture<List<PlayerData>> getAllUserData() {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerData> players = new ArrayList<>();
            String sql = String.format("SELECT player_uuid, player_name, data, last_login FROM %suser_data", tablePrefix);
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    PlayerData playerData = new PlayerData(
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("player_name"),
                        rs.getString("data"),
                        rs.getTimestamp("last_login")
                    );
                    players.add(playerData);
                }
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load all user data", e);
            }
            
            return players;
        });
    }
    
    public CompletableFuture<Integer> cleanupInactiveUsers(int daysInactive) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = String.format("""
                DELETE FROM %suser_data 
                WHERE last_login < DATE_SUB(NOW(), INTERVAL ? DAY)
                """, tablePrefix);
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setInt(1, daysInactive);
                int rowsAffected = stmt.executeUpdate();
                
                plugin.getLogger().info(String.format("Cleanup completed: removed %d inactive players", rowsAffected));
                return rowsAffected;
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to cleanup inactive users", e);
                return 0;
            }
        });
    }
    
    public CompletableFuture<Void> updateBalanceCache(UUID playerUuid, String playerName, double balance) {
        return CompletableFuture.runAsync(() -> {
            String sql = String.format("""
                INSERT INTO %sbalance_cache (player_uuid, player_name, balance) 
                VALUES (?, ?, ?) 
                ON DUPLICATE KEY UPDATE 
                player_name = VALUES(player_name), 
                balance = VALUES(balance),
                last_updated = CURRENT_TIMESTAMP
                """, tablePrefix);
                
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, playerName);
                stmt.setDouble(3, balance);
                
                stmt.executeUpdate();
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, 
                    String.format("Failed to update balance cache for player %s", playerName), e);
            }
        });
    }
    
    public CompletableFuture<List<BalanceEntry>> getTopBalances(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<BalanceEntry> balances = new ArrayList<>();
            String sql = String.format("""
                SELECT player_uuid, player_name, balance 
                FROM %sbalance_cache 
                ORDER BY balance DESC 
                LIMIT ?
                """, tablePrefix);
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setInt(1, limit);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        BalanceEntry entry = new BalanceEntry(
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("player_name"),
                            rs.getDouble("balance")
                        );
                        balances.add(entry);
                    }
                }
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get top balances", e);
            }
            
            return balances;
        });
    }
    
    public boolean isConnected() {
        if (dataSource == null || dataSource.isClosed()) {
            return false;
        }
        
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
    
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed");
        }
    }

    public static class PlayerData {
        private final UUID uuid;
        private final String name;
        private final String data;
        private final Timestamp lastLogin;
        
        public PlayerData(UUID uuid, String name, String data, Timestamp lastLogin) {
            this.uuid = uuid;
            this.name = name;
            this.data = data;
            this.lastLogin = lastLogin;
        }
        
        public UUID getUuid() { return uuid; }
        public String getName() { return name; }
        public String getData() { return data; }
        public Timestamp getLastLogin() { return lastLogin; }
    }
    
    public static class BalanceEntry {
        private final UUID uuid;
        private final String name;
        private final double balance;
        
        public BalanceEntry(UUID uuid, String name, double balance) {
            this.uuid = uuid;
            this.name = name;
            this.balance = balance;
        }
        
        public UUID getUuid() { return uuid; }
        public String getName() { return name; }
        public double getBalance() { return balance; }
    }
}