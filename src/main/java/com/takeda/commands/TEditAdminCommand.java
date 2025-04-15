package com.takeda.commands;

import com.takeda.TEditPlugin;
import com.takeda.config.SettingsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Handles administrative commands for T-Edit, like reloading configurations.
 */
public class TEditAdminCommand implements CommandExecutor, TabCompleter {

    private final TEditPlugin plugin;
    private final SettingsManager settings;

    private static final List<String> SUBCOMMANDS = List.of("reload");

    public TEditAdminCommand(@NotNull TEditPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin instance cannot be null");
        this.settings = plugin.getSettingsManager(); // Assumes settings manager is already loaded
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /" + label + " <subcommand>", NamedTextColor.RED));
            sender.sendMessage(Component.text("Available: " + String.join(", ", SUBCOMMANDS), NamedTextColor.GRAY));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                return handleReload(sender);
            default:
                sender.sendMessage(Component.text("Unknown subcommand: " + subCommand, NamedTextColor.RED));
                return true;
        }
    }

    private boolean handleReload(@NotNull CommandSender sender) {
        if (!sender.hasPermission("tedit.reload")) {
            // Use the configured message format if available, otherwise fallback
            try {
                sender.sendMessage(settings.getMessageComponent("error_no_permission", net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("permission", "tedit.reload")));
            } catch (Exception e) { // Catch potential issues if settings manager isn't fully ready (unlikely here)
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            }
            return true;
        }

        sender.sendMessage(Component.text("Reloading T-Edit configurations...", NamedTextColor.YELLOW));
        plugin.getLogger().info("Reloading T-Edit configurations requested by " + sender.getName() + "...");

        boolean success = settings.load(); // Call the main load method which reloads all files

        if (success) {
            sender.sendMessage(Component.text("T-Edit configurations reloaded successfully.", NamedTextColor.GREEN));
            plugin.getLogger().info("T-Edit configurations reloaded successfully.");
            // Inform players with active sessions? Might be disruptive. Best to let them finish or re-open.
            // Example: plugin.getSessionManager().closeAllSessions("Configuration reload");
        } else {
            sender.sendMessage(Component.text("Failed to reload T-Edit configurations. Check console for errors.", NamedTextColor.RED));
            plugin.getLogger().severe("Failed to reload T-Edit configurations. Previous errors may indicate the cause.");
        }
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .filter(sub -> sender.hasPermission("tedit." + sub)) // Basic permission check for tab complete
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}