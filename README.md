# EssentialsSQL

A powerful MySQL storage plugin for EssentialsX that enables cross-server synchronization of user data and balances.

## 📋 Features

### Core Functionality
- **MySQL Storage** - Store all EssentialsX userdata in MySQL database
- **Cross-Server Sync** - Synchronize player data across multiple servers
- **Online/Offline UUID Support** - Compatible with Minecraft 1.7.10+
- **Balance Synchronization** - Real-time balance sync between servers
- **Data Compression** - Optional GZIP compression to reduce storage size
- **Automatic Cleanup** - Remove inactive players from database

### Data Management
- **Import/Export** - Convert between userdata files and database
- **Data Filtering** - Configure which data types to save/load
- **Backup System** - Automatic backups before major operations
- **Balance Top** - Database-powered balance leaderboard
- **Manual Sync** - Admin commands for manual data synchronization

### Shop Plugin Integration
- **QuickShop** - Offline sales support
- **ChestShop** - Offline sales and buy orders
- **AuctionHouse** - Offline auction support
- **PlayerShopGUI+** - Offline sales
- **ShopChest** - Offline sales and buy orders

### Performance & Safety
- **Async Operations** - Non-blocking database operations
- **Connection Pooling** - Optimized database connections
- **Error Handling** - Graceful error recovery
- **Debug Logging** - Comprehensive logging system

## 🚀 Quick Start

### Prerequisites
- **EssentialsX** plugin installed
- **MySQL database** server
- **Vault** plugin (for economy features)
- **Java 21** or higher

### Installation

1. **Download** the plugin JAR file
2. **Place** it in your `plugins/` folder
3. **Start** the server to generate config files
4. **Configure** the database connection in `config.yml`
5. **Restart** the server

### Basic Configuration

Edit `plugins/EssentialsSQL/config.yml`:

```yaml
# Database Configuration
database:
  enabled: true
  type: "MySQL"  # Currently only MySQL is supported
  host: "localhost"
  port: 3306
  database: "essentials"
  username: "root"
  password: "password"
  # Connection pool settings
  pool:
    minimum-idle: 2
    maximum-pool-size: 10
    connection-timeout: 30000
    idle-timeout: 600000
    max-lifetime: 1800000

# General Settings
settings:
  # Enable UUID support (required for 1.7.10+)
  uuid-support: true

  # Save data when player leaves the server
  save-on-quit: true

  # Load data when player joins the server
  load-on-join: true

  # Delete local userdata file after saving to database
  delete-local-after-save: true

  # Delete entire userdata folder on server shutdown/restart
  delete-userdata-folder-on-shutdown: false

  # Save player data to database in async to prevent lag
  async-operations: true

  # Sync interval in minutes (0 = disabled)
  auto-sync-interval: 0

# Data Management
data:
  # Filter what data to save/load (set to false to exclude)
  filters:
    homes: true
    money: true
    mail: true
    kits: true
    cooldowns: true
    warps: true
    jail: true
    mutes: true
    social-spy: true
    god-mode: true
    fly: true
    nicknames: true
    ignore-list: true
    teleport-requests: true

  # Compress data before storing in database
  compress-data: true

  # Maximum data size per player in KB (0 = unlimited)
  max-data-size: 1024

# Cleanup Settings
cleanup:
  # Remove inactive users from database
  enabled: false

  # Days of inactivity before removal
  inactive-days: 365

  # Run cleanup on server start
  cleanup-on-start: false

  # Cleanup interval in hours (0 = disabled)
  cleanup-interval: 24

# Shop Plugin Integration
shop-integration:
  # ChestShop support
  chestshop:
    enabled: false
    offline-sales: true
    offline-buy-orders: true

  # AuctionHouse support
  auctionhouse:
    enabled: false
    offline-auctions: true

  # PlayerShopGUI+ support
  playershopgui:
    enabled: false
    offline-sales: true

  # ShopChest support
  shopchest:
    enabled: false
    offline-sales: true
    offline-buy-orders: true

# Command Settings
commands:
  # Enable balance top command from database
  balance-top:
    enabled: true
    cache-duration: 300  # seconds

  # Import/Export commands
  import-export:
    enabled: true
    backup-before-import: true
    backup-before-export: true

# Debug and Logging
debug:
  enabled: false
  log-database-operations: false
  log-file-operations: false
  performance-monitoring: false

# Messages
messages:
  prefix: "&7[&bEssentialsSQL&7]&r "
  data-loading: "&aLoading your data..."
  data-saving: "&aSaving your data..."
  data-load-failed: "&cFailed to load your data!"
  data-save-failed: "&cFailed to save your data!"
  import-success: "&aSuccessfully imported {count} players!"
  export-success: "&aSuccessfully exported {count} players!"
  cleanup-success: "&aCleanup completed! Removed {count} inactive players."
  no-permission: "&cYou don't have permission to use this command!"
```

## 📖 Commands

### Admin Commands
- `/esql import` - Import all userdata files to database
- `/esql export` - Export all database data to userdata files
- `/esql cleanup [days]` - Remove inactive players from database
- `/esql sync <player>` - Manually sync player data
- `/esql status` - Show plugin status
- `/esql reload` - Reload configuration
- `/esql delete userdata` - Delete entire userdata folder

### User Commands
- `/esql baltop [limit]` - Show balance top from database

## 🔧 Configuration

### Database Settings
```yaml
database:
  host: "localhost"              # MySQL server host
  port: 3306                     # MySQL server port
  database: "essentials"         # Database name
  username: "root"               # Database username
  password: "password"           # Database password
  pool:
    minimum-idle: 2              # Minimum idle connections
    maximum-pool-size: 10        # Maximum connections
    connection-timeout: 30000    # Connection timeout (ms)
```

### Data Management
```yaml
data:
  compress-data: true            # Enable GZIP compression
  max-data-size: 1024           # Max data size per player (KB)
  filters:
    homes: true                  # Include homes data
    money: true                  # Include balance data
    mail: true                   # Include mail data
    # ... more filters
```

### Cleanup Configuration
```yaml
cleanup:
  enabled: true                  # Enable automatic cleanup
  inactive-days: 365            # Days before removal
  cleanup-on-start: false       # Run cleanup on server start
  cleanup-interval: 24          # Cleanup interval (hours)
```

## 🔐 Permissions

### Admin Permissions
- `essentialssql.admin.*` - All admin commands
- `essentialssql.admin.import` - Import userdata
- `essentialssql.admin.export` - Export database
- `essentialssql.admin.cleanup` - Run cleanup
- `essentialssql.admin.sync` - Manual sync
- `essentialssql.admin.status` - View status
- `essentialssql.admin.reload` - Reload config
- `essentialssql.admin.delete` - Delete userdata

### User Permissions
- `essentialssql.baltop` - View balance top

## 🌐 Cross-Server Setup

### Server 1 Configuration
```yaml
database:
  host: "shared-mysql-server.com"
  database: "network_essentials"
  # ... connection details

settings:
  save-on-quit: true
  load-on-join: true
```

### Server 2 Configuration
```yaml
# Same database configuration as Server 1
database:
  host: "shared-mysql-server.com"
  database: "network_essentials"
  # ... same connection details
```

When players switch servers, their data (including balance) will automatically sync!

## 🛠️ Advanced Features

### Shop Integration
Enable offline sales for various shop plugins:

```yaml
shop-integration:
  quickshop:
    enabled: true
    offline-sales: true
  chestshop:
    enabled: true
    offline-sales: true
    offline-buy-orders: true
```

### Debug Mode
Enable detailed logging for troubleshooting:

```yaml
debug:
  enabled: true
  log-database-operations: true
  log-file-operations: true
```

## 📊 Database Schema

The plugin creates three main tables:

- **`essentials_user_data`** - Stores compressed player data
- **`essentials_balance_cache`** - Cached balances for quick baltop
- **`essentials_shop_data`** - Shop plugin integration data

## 🔧 Troubleshooting

### Common Issues

**Database Connection Failed**
- Check database credentials in config.yml
- Ensure MySQL server is running
- Verify network connectivity

**Balance Not Syncing**
- Ensure Vault plugin is installed
- Check economy plugin compatibility
- Enable debug logging to see sync operations

**Import/Export Fails**
- Check file permissions on userdata folder
- Ensure sufficient disk space
- Review console for error messages

### Debug Steps
1. Enable debug logging in config.yml
2. Check console for detailed error messages
3. Use `/esql status` to verify plugin state
4. Test database connection manually

## 📝 Support

- **GitHub Issues** - Report bugs and feature requests
- **Discord** - Community support and discussion
- **Documentation** - Comprehensive setup guides

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🙏 Credits

- **EssentialsX Team** - For the amazing Essentials plugin
- **Vault Developers** - For the economy API
- **Community Contributors** - For bug reports and suggestions

---

**Made with ❤️ for the Minecraft community**