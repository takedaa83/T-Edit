package com.takeda.commands;

import com.takeda.TEditPlugin;
import com.takeda.config.SettingsManager;
import com.takeda.gui.EditGUI;
import com.takeda.sessions.EditSessionManager;
import com.takeda.util.EnchantmentUtil; // Added
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Handles the /edit command execution, performing initial checks and opening the GUI.
 */
public class EditCommand implements CommandExecutor {

    private final TEditPlugin plugin;
    private final SettingsManager settings;
    private final EditSessionManager sessionManager;

    public EditCommand(@NotNull TEditPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin instance cannot be null");
        // Managers are guaranteed non-null by plugin enable sequence if successful
        this.settings = plugin.getSettingsManager();
        this.sessionManager = plugin.getSessionManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Component message = settings.getMessageComponent("error_player_only");
            plugin.adventure().sender(sender).sendMessage(message);
            return true;
        }

        // Base permission 'tedit.use' is handled by Bukkit based on plugin.yml

        if (sessionManager.isActive(player.getUniqueId())) {
            settings.sendMessage(player, "error_already_editing");
            settings.playSound(player, "action_fail");
            return true;
        }

        PlayerInventory playerInv = player.getInventory();
        ItemStack itemInHand = playerInv.getItemInMainHand();
        int heldItemSlot = playerInv.getHeldItemSlot();

        if (itemInHand.getType().isAir()) {
            settings.sendMessage(player, "error_no_item_in_hand");
            settings.playSound(player, "action_fail");
            return true;
        }

        // --- Blacklist Check ---
        if (settings.isItemBlacklisted(itemInHand.getType())) {
            settings.sendMessage(player, "error_item_blacklisted",
                    Placeholder.unparsed("item", itemInHand.getType().getKey().toString()));
            settings.playSound(player, "action_fail");
            return true;
        }

        // --- Item Type Check (For Enchanting Restrictions) ---
        // Although enchantments are filtered *later*, we can prevent opening the GUI entirely
        // if the item type fundamentally isn't one we want editable (especially for enchants).
        // This check might be redundant if the only goal is to restrict *enchantments*,
        // but can be useful if you want to prevent editing names/lore on certain items too.
        // Let's keep it simple for now and rely on the enchantment filtering in the GUI/Util.
        /*
        if (!EnchantmentUtil.isEnchantableType(itemInHand)) { // Example check
            settings.sendMessage(player, "error_item_type_not_editable"); // Add this message key
            settings.playSound(player, "action_fail");
            return true;
        }
        */

        // --- Proceed to open GUI ---
        try {
            // EditGUI static method handles session creation and opening
            EditGUI.createAndOpen(player, itemInHand, heldItemSlot, plugin);
            settings.playSound(player, "gui_open"); // Play sound on successful command execution
        } catch (Exception e) {
            // Log detailed error for admins
            plugin.getLogger().log(Level.SEVERE, "Failed to create and open T-Edit GUI for " + player.getName(), e);
            // Inform player of generic failure
            settings.sendMessage(player, "error_gui_open_failed");
            settings.playSound(player, "action_fail");
            // Attempt cleanup in case session was partially created
            sessionManager.closeSession(player.getUniqueId(), "GUI creation failed in command");
        }
        return true;
    }
}