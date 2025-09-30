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
  type: "MySQL"
  host: "localhost"
  port: 3306
  database: "essentials"
  username: "your_username"
  password: "your_password"

# Enable basic features
settings:
  save-on-quit: true
  load-on-join: true
  async-operations: true
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