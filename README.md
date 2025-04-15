# T-Edit ✨

<div align="center">
  
![T-Edit Banner](docs/images/banner.png)

*The elegant item editor for Minecraft servers*

</div>

## ✨ Overview

T-Edit is a modern, feature-rich GUI-based item editor that allows server administrators and players to modify items with ease and elegance. With a sleek purple-themed interface and intuitive controls, T-Edit brings the power of advanced item manipulation into an accessible package.

![T-Edit Interface](docs/images/interface.png)

## 🌟 Features

- **Beautiful GUI Interface** - Clean, modern design with consistent purple theme
- **Comprehensive Item Editing** - Edit names, lore, enchantments, and more
- **Advanced Enchantment System** - Add, remove, and adjust enchantment levels with intuitive controls
- **Permission-Based Access** - Granular permission system for fine-tuned control
- **Fully Configurable** - Customize every aspect of the plugin to suit your server
- **MiniMessage Support** - Create stunning text with gradient colors and formatting
- **Paginated Enchantments** - Browse through all possible enchantments with ease

## 📥 Installation

1. Download the latest version of T-Edit from [GitHub Releases](https://github.com/your-username/T-Edit/releases) or [Spigot](https://www.spigotmc.org/resources/)
2. Place the JAR file in your server's `plugins` folder
3. Restart your server or use a plugin manager to load the plugin
4. Edit the configuration files in the `plugins/T-Edit` directory if desired
5. Use the `/edit` command to start editing items!

## 🛠️ Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/edit` | Opens the T-Edit GUI for the item in your main hand | `tedit.use` |
| `/tedit reload` | Reloads all T-Edit configuration files | `tedit.reload` |

## 🔒 Permissions

T-Edit uses a comprehensive permission system to control access to different features:

### User Permissions
- `tedit.use` - Allows using the `/edit` command to open the GUI
- `tedit.rename` - Allows renaming items
- `tedit.lore.edit` - Allows adding lore to items
- `tedit.lore.clear` - Allows clearing all lore lines
- `tedit.repair` - Allows repairing items
- `tedit.duplicate` - Allows duplicating items

### Enchantment Permissions
- `tedit.enchant.base` - Allows basic enchanting actions
- `tedit.enchant.bypasslevel` - Allows enchanting above vanilla level caps
- `tedit.enchant.bypassconflict` - Allows applying conflicting enchantments
- `tedit.enchant.treasure` - Allows applying treasure enchantments
- `tedit.enchant.curses` - Allows applying curse enchantments

### Admin Permissions
- `tedit.admin` - Grants access to administrative commands
- `tedit.reload` - Allows reloading T-Edit configuration

### Permission Groups
- `tedit.*` - Grants all T-Edit permissions

## ⚙️ Configuration

T-Edit offers extensive configuration options across multiple files:

### config.yml
The main configuration file for core settings:
```yaml
# Enable detailed console logging for debugging purposes
debug:
  enabled: false

# Enchantment Settings
enchantments:
  allow_bypass_level_caps: false
  allow_bypass_conflicts: false
  allow_treasure: true
  allow_curses: true

# Item Blacklist
item-blacklist:
  - "minecraft:barrier"
  - "minecraft:command_block"
  # Add any items you want to block from editing
```

### gui.yml
Customize the GUI layout and appearance:
```yaml
title: "<gradient:#AA00FF:#DD55FF><bold>T-Edit Item Editor</bold></gradient>"
size: 54

# Customize buttons, slots, and more
```

### messages.yml
Customize all player-facing messages with MiniMessage format:
```yaml
prefix: "<gradient:#AA00FF:#DD55FF>T-Edit <dark_gray>» </dark_gray>"
# Customize error messages, prompts, and action feedback
```

### sounds.yml
Customize sound effects for different actions:
```yaml
enabled: true
sounds:
  gui_open: BLOCK_CHEST_OPEN
  # Customize sounds for all actions
```

## 📷 Screenshots

![Item Renaming](docs/images/rename.png)
*Item renaming with MiniMessage support*

![Enchantment Management](docs/images/enchantments.png)
*Intuitive enchantment management interface*

![Lore Editing](docs/images/lore.png)
*Adding and editing lore with ease*

## 💡 Tips & Tricks

- **MiniMessage Format**: When renaming items or adding lore, you can use MiniMessage format for rich formatting. Try `<gradient:#AA00FF:#DD55FF>My Cool Sword</gradient>` for a beautiful gradient effect!
- **Quick Navigation**: Use the pagination buttons to quickly browse through all available enchantments.
- **Permission Setup**: Consider using a permissions plugin to create permission groups for different player ranks.

## ❓ Troubleshooting

**Issue**: Plugin commands aren't working.  
**Solution**: Ensure the user has the proper permissions and that the plugin is properly installed.

**Issue**: GUI doesn't open when using `/edit`.  
**Solution**: Make sure you're holding an item in your main hand that isn't blacklisted.

**Issue**: Enchantments aren't showing up.  
**Solution**: Check that the item type can be enchanted and that the user has the necessary permissions.

## 🤝 Contributing

Contributions are welcome! If you'd like to contribute to T-Edit:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 License

T-Edit is released under the MIT License. See the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Special thanks to the Bukkit/Spigot/Paper community
- Thanks to all contributors and testers who helped make this plugin possible
- Built with ❤️ by Takeda

---

<div align="center">
  
[![Discord](https://img.shields.io/badge/Discord-Join%20our%20community-7289DA?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/your-discord)
[![GitHub Sponsors](https://img.shields.io/badge/Sponsor-Support%20the%20project-EA4AAA?style=for-the-badge&logo=github-sponsors&logoColor=white)](https://github.com/sponsors/your-username)

</div> 