// Keep the PlayerListener class exactly as provided in the previous "immediate apply" response.
// It correctly calls the revised EnchantmentUtil.applyEnchantment and the updated EditGUI methods.
// It includes the validateAndGetActualItem helper.
// Paste the full PlayerListener code from the previous response here.
package com.takeda.listeners;

import com.takeda.TEditPlugin;
import com.takeda.config.SettingsManager;
import com.takeda.gui.EditGUI;
import com.takeda.sessions.EditSession;
import com.takeda.sessions.EditSessionManager;
import com.takeda.util.EnchantmentUtil;
import com.takeda.util.ItemUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerListener implements Listener {

    private final TEditPlugin plugin;
    private final SettingsManager settings;
    private final EditSessionManager sessionManager;
    private final NamespacedKey enchantKeyPDC;

    public PlayerListener(@NotNull TEditPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin instance cannot be null");
        this.settings = plugin.getSettingsManager();
        this.sessionManager = plugin.getSessionManager();
        this.enchantKeyPDC = plugin.getEnchantmentPDCKey();
    }

    // --- Helper Method: Validate and Get Actual Item ---
    @Nullable
    private ItemStack validateAndGetActualItem(@NotNull Player player, @NotNull EditSession session) {
        ItemStack actualItem = player.getInventory().getItem(session.getOriginalSlot());
        ItemStack originalItemFromSession = session.getOriginalItem(); // The initial state clone

        // Check if item exists, is the same type, and (if stackable) same amount as the original
        boolean itemMismatch = actualItem == null || actualItem.getType() != originalItemFromSession.getType() ||
                (originalItemFromSession.getMaxStackSize() > 1 && actualItem.getAmount() != originalItemFromSession.getAmount());

        if (itemMismatch) {
            // Ensure the session is still active before trying to close it
            if (sessionManager.isActive(player.getUniqueId())) {
                settings.sendMessage(player, "error_original_item_changed");
                settings.playSound(player, "action_fail");
                sessionManager.closeSession(player.getUniqueId(), "Original item mismatch during action");
            }
            return null; // Indicate failure
        }
        return actualItem; // Return the actual item stack from inventory
    }


    // --- Inventory Click Handling ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID playerId = player.getUniqueId();

        Optional<EditSession> sessionOpt = sessionManager.getSession(playerId);
        if (sessionOpt.isEmpty()) return;
        EditSession session = sessionOpt.get();

        Inventory topInventory = event.getView().getTopInventory();
        Inventory clickedInventory = event.getClickedInventory();

        // --- Interaction within the T-Edit GUI ---
        if (topInventory.equals(session.getGuiInventory()) && clickedInventory != null && clickedInventory.equals(topInventory)) {
            handleGuiClick(event, player, session); // Handles cancellation internally
            return;
        }

        // --- Interaction within the Player Inventory while T-Edit GUI is Open ---
        if (topInventory.equals(session.getGuiInventory()) && clickedInventory != null && clickedInventory.equals(player.getInventory())) {
            if (event.isShiftClick()) {
                event.setCancelled(true); // Prevent Shift+Clicking items into the GUI
                return;
            }
            // Prevent moving/clicking the original item being edited
            boolean affectsOriginalSlot = event.getSlot() == session.getOriginalSlot() ||
                    (event.getAction() == InventoryAction.HOTBAR_SWAP && event.getHotbarButton() == session.getOriginalSlot());

            if (affectsOriginalSlot) {
                // Close early if they interact directly with the slot
                if (settings.isDebugEnabled()) plugin.getLogger().info("[Debug] Player " + player.getName() + " interacted with original item slot ("+session.getOriginalSlot()+") while GUI open. Closing session.");
                 if (sessionManager.isActive(playerId)) { // Check again before closing
                    settings.sendMessage(player, "error_original_item_moved");
                    settings.playSound(player, "action_fail");
                    sessionManager.closeSession(playerId, "Original item slot interacted with");
                 }
                // Closing inventory handles the cancellation implicitly.
            }
            // Allow normal interaction with other player inventory slots.
        }
    }

    private void handleGuiClick(@NotNull InventoryClickEvent event, @NotNull Player player, @NotNull EditSession session) {
        int slot = event.getSlot();
        ItemStack clickedItem = event.getCurrentItem();
        ClickType clickType = event.getClick();

        // Always cancel interactions with placeholder items
        if (clickedItem != null && clickedItem.isSimilar(settings.getPlaceholderItem())) {
            event.setCancelled(true);
            return;
        }

        // Ignore clicks on completely empty slots
        if (clickedItem == null || clickedItem.getType().isAir()) {
             // Don't cancel, allows potential interaction if needed later (though unlikely)
            return;
        }

        // --- Check if a configured GUI Element was clicked ---
        Optional<SettingsManager.GuiElementConfig> elementOpt = settings.getGuiElementBySlot(slot);
        if (elementOpt.isPresent()) {
            SettingsManager.GuiElementConfig config = elementOpt.get();

            // --- Handle click on Preview Item to Close Editor ---
            if (config.key().equals("preview_item")) {
                // Allow picking up the item (closing) with Left Click or Shift Left Click
                if (clickType.isLeftClick()) { // Covers LEFT and SHIFT_LEFT
                    event.setCancelled(true); // We handle the close manually
                    handleCloseEditor(player, session); // Just close, changes are already applied
                } else {
                    // Cancel other interactions (Right click, Middle click etc.) with the preview item
                    event.setCancelled(true);
                }
                return; // Handled preview item click
            }

            // --- Handle clicks on other Buttons ---
            event.setCancelled(true); // Cancel default behavior for buttons
            handleButtonClick(player, session, config, clickType);
            return;
        }

        // --- Check if an Enchantment Book was clicked ---
        if (clickedItem.getType() == Material.ENCHANTED_BOOK) {
            event.setCancelled(true); // Cancel default behavior for enchant books
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta != null) {
                String enchantKeyStr = meta.getPersistentDataContainer().get(enchantKeyPDC, PersistentDataType.STRING);
                if (enchantKeyStr != null) {
                    Enchantment enchantment = EnchantmentUtil.getEnchantmentByKey(enchantKeyStr);
                    if (enchantment != null) {
                        handleEnchantmentClick(player, session, enchantment, clickType); // Handles applying directly
                        return;
                    } else if (settings.isDebugEnabled()) plugin.getLogger().warning("[Debug] GUI slot " + slot + " had invalid enchant PDC key: '" + enchantKeyStr + "'");
                } else if (settings.isDebugEnabled()) plugin.getLogger().warning("[Debug] GUI slot " + slot + " Enchanted Book missing PDC key.");
            }
        }

        // If the click wasn't on a known element, book, or placeholder, cancel it to be safe.
        if (settings.isDebugEnabled()) {
             plugin.getLogger().info("[Debug] Unhandled GUI click by " + player.getName() + " on " + clickedItem.getType() + " in slot " + slot + ". Cancelling.");
        }
        event.setCancelled(true);
    }

    // --- Button Click Logic (Applies Directly) ---
    private void handleButtonClick(@NotNull Player player, @NotNull EditSession session, @NotNull SettingsManager.GuiElementConfig config, @NotNull ClickType clickType) {
        // Basic Permission Check
        if (config.permission() != null && !player.hasPermission(config.permission())) {
             settings.sendMessage(player, "error_no_permission", Placeholder.unparsed("permission", config.permission()));
             settings.playSound(player, "action_fail");
             return;
        }

        // Actions requiring chat input are handled differently (don't modify item yet)
        if (config.key().equals("rename")) {
            settings.playSound(player, "button_click");
            session.setCurrentState(EditSession.EditActionState.WAITING_FOR_RENAME);
            player.closeInventory();
            settings.sendMessage(player, "prompt_rename_enter");
            return; // Return early, modification happens after chat input
        }
        if (config.key().equals("edit_lore")) {
            settings.playSound(player, "button_click");
            session.setCurrentState(EditSession.EditActionState.WAITING_FOR_LORE_ADD);
            player.closeInventory();
            settings.sendMessage(player, "prompt_lore_enter");
            return; // Return early
        }
        if (config.key().equals("duplicate")) {
             settings.playSound(player, "button_click");
            handleDuplicateAction(player, session); // Duplicate handles its own validation/logic
            return;
        }
         if (config.key().startsWith("page_")) {
             // Play sound before potentially failing or succeeding
             settings.playSound(player, "button_click");
             handlePaginationClick(player, session, config.key());
             return;
         }


        // --- For actions modifying the item directly ---
        ItemStack actualItem = validateAndGetActualItem(player, session);
        if (actualItem == null) return; // Validation failed, session closed

        // Play sound after validation, before action attempt
        settings.playSound(player, "button_click");

        boolean success = false;
        boolean requiresEnchantUpdate = false;

        switch (config.key()) {
            case "remove_all_enchants":
                if (!player.hasPermission("tedit.enchant.base")) { // Re-check specific perm
                    settings.sendMessage(player, "error_no_permission", Placeholder.unparsed("permission", "tedit.enchant.base"));
                    settings.playSound(player, "action_fail"); return;
                }
                success = EnchantmentUtil.removeAllEnchantments(actualItem);
                if (success) {
                    settings.sendActionBar(player, "actionbar_enchants_cleared");
                    settings.playSound(player, "enchant_remove"); // Use specific sound
                    requiresEnchantUpdate = true;
                } else {
                    settings.sendActionBar(player, "actionbar_no_enchants_to_clear");
                    // No fail sound needed if nothing changed
                }
                break;

            case "repair":
                 if (!player.hasPermission("tedit.repair")) { // Re-check specific perm
                    settings.sendMessage(player, "error_no_permission", Placeholder.unparsed("permission", "tedit.repair"));
                    settings.playSound(player, "action_fail"); return;
                }
                success = ItemUtil.repairItem(actualItem);
                if (success) {
                    settings.sendActionBar(player, "actionbar_item_repaired");
                    settings.playSound(player, "action_success"); // General success
                } else {
                    settings.sendActionBar(player, "actionbar_item_not_repairable");
                    settings.playSound(player, "action_fail"); // Explicit fail sound
                }
                break;

            case "clear_lore":
                if (!player.hasPermission("tedit.lore.clear")) { // Re-check specific perm
                    settings.sendMessage(player, "error_no_permission", Placeholder.unparsed("permission", "tedit.lore.clear"));
                    settings.playSound(player, "action_fail"); return;
                }
                success = ItemUtil.clearLore(actualItem);
                if (success) {
                    settings.sendActionBar(player, "actionbar_lore_cleared");
                    settings.playSound(player, "action_success"); // General success
                } else {
                    settings.sendActionBar(player, "actionbar_no_lore_to_clear");
                    // No fail sound needed if nothing changed
                }
                break;

            // Page buttons handled above
            // Rename/Lore Add handled above
            // Duplicate handled above
            default:
                if (settings.isDebugEnabled()) plugin.getLogger().warning("[Debug] Unhandled direct-apply button key: " + config.key());
                break;
        }

        // --- Update GUI if modification occurred ---
        if (success) {
            session.setPreviewItem(actualItem.clone()); // Update preview to match actual item
            EditGUI.placePreviewItem(session); // Update the preview item slot display
            if (requiresEnchantUpdate) {
                EditGUI.populateEnchantments(session, settings); // Refresh enchantment books (also updates pagination)
            }
        }
    }

     // --- Helper for Pagination Buttons ---
    private void handlePaginationClick(@NotNull Player player, @NotNull EditSession session, @NotNull String key) {
        boolean changed = false;
        if (key.equals("page_prev") && session.getEnchantmentPage() > 0) {
            session.setEnchantmentPage(session.getEnchantmentPage() - 1);
            changed = true;
        } else if (key.equals("page_next") && session.getEnchantmentPage() < session.getTotalEnchantmentPages() - 1) {
            session.setEnchantmentPage(session.getEnchantmentPage() + 1);
            changed = true;
        }

        if (changed) {
            settings.playSound(player, "page_change");
            EditGUI.populateEnchantments(session, settings); // Refreshes books and updates pagination elements
        } else if (!key.equals("page_info")) { // Don't play fail sound for clicking info
             settings.playSound(player, "action_fail");
        }
    }


    // --- Enchantment Click Logic (Applies Directly) ---
    private void handleEnchantmentClick(@NotNull Player player, @NotNull EditSession session, @NotNull Enchantment enchantment, @NotNull ClickType clickType) {
        ItemStack actualItem = validateAndGetActualItem(player, session);
        if (actualItem == null) return; // Validation failed, session closed

        int currentLevel = actualItem.getEnchantmentLevel(enchantment); // Check level on actual item
        int maxLevel = EnchantmentUtil.getMaxLevel(enchantment, player, settings);
        int targetLevel = currentLevel;
        boolean isRemoval = false; // Flag needed to differentiate removing vs setting level 0

        switch (clickType) {
            case LEFT: targetLevel = Math.min(maxLevel, currentLevel + 1); break;
            case SHIFT_LEFT: targetLevel = maxLevel; break;
            case RIGHT: targetLevel = Math.max(0, currentLevel - 1); break;
            case SHIFT_RIGHT: targetLevel = 0; isRemoval = (currentLevel > 0); break; // Only flag removal if level was > 0
            default: return; // Ignore other clicks
        }

        // Prevent applying if level didn't change (and not removing level 0 explicitly with shift-right)
        if (targetLevel == currentLevel && !isRemoval) {
             if (currentLevel == maxLevel && clickType.isLeftClick()) settings.playSound(player, "action_fail");
             if (currentLevel == 0 && clickType.isRightClick()) settings.playSound(player, "action_fail");
            return;
        }


        // --- Apply directly to the actual item ---
        if (EnchantmentUtil.applyEnchantment(actualItem, enchantment, targetLevel, player, settings)) {
             // --- Success Feedback ---
             if (targetLevel == 0) { // Check targetLevel to confirm removal/set to 0
                 settings.playSound(player, "enchant_remove");
                 settings.sendActionBar(player, "actionbar_enchant_removed", Placeholder.component("enchantment", EnchantmentUtil.getFriendlyName(enchantment)));
             } else if (targetLevel > currentLevel) {
                 settings.playSound(player, "enchant_level_up");
                 settings.sendActionBar(player, "actionbar_enchant_set", Placeholder.component("enchantment", EnchantmentUtil.getFriendlyName(enchantment)), Placeholder.parsed("level", String.valueOf(targetLevel)));
             } else if (targetLevel < currentLevel) { // Must be targetLevel >= 0 here
                 settings.playSound(player, "enchant_level_down");
                 settings.sendActionBar(player, "actionbar_enchant_set", Placeholder.component("enchantment", EnchantmentUtil.getFriendlyName(enchantment)), Placeholder.parsed("level", String.valueOf(targetLevel)));
             }

             // --- Update GUI Visuals ---
             session.setPreviewItem(actualItem.clone()); // Update preview to match actual item state
             EditGUI.placePreviewItem(session);
             EditGUI.updateEnchantmentBook(session, enchantment); // Update the specific book

        } else {
            // --- Failure Feedback ---
            settings.playSound(player, "action_fail");
            boolean canBypassConflict = settings.isAllowBypassConflicts() && player.hasPermission("tedit.enchant.bypassconflict");
             // Check for conflict only when adding (targetLevel > 0)
            if (targetLevel > 0 && !canBypassConflict && EnchantmentUtil.conflictsWithExisting(enchantment, actualItem)) {
                 Enchantment conflictingEnchant = actualItem.getEnchantments().keySet().stream()
                         .filter(enchantment::conflictsWith)
                         .findFirst().orElse(null);
                 if (conflictingEnchant != null) {
                     settings.sendActionBar(player, "actionbar_error_conflict", Placeholder.component("enchantment", EnchantmentUtil.getFriendlyName(enchantment)), Placeholder.component("conflicting", EnchantmentUtil.getFriendlyName(conflictingEnchant)));
                 } else {
                     // Fallback if conflict detected but specific enchant not found (unlikely)
                     settings.sendActionBar(player, "actionbar_error_enchant_failed");
                 }
             } else {
                 // Generic failure if not a conflict
                 settings.sendActionBar(player, "actionbar_error_enchant_failed");
             }
             if(settings.isDebugEnabled()) plugin.getLogger().warning("EnchantmentUtil.applyEnchantment failed for " + enchantment.getKey() + " L" + targetLevel + " by " + player.getName() + " on actual item.");
        }
    }

    // --- Specific Action Handlers ---

    /** Handles closing the GUI when the preview item is clicked. Assumes changes are already applied. */
    private void handleCloseEditor(@NotNull Player player, @NotNull EditSession session) {
        // Validate item one last time before closing to catch any last-moment changes
        if (validateAndGetActualItem(player, session) == null) {
            // Validation failed, message/sound/close handled by validate method
            return;
        }

        // No need to apply meta, changes were instant
        settings.sendMessage(player, "success_editor_closed"); // Use a distinct message
        settings.playSound(player, "confirm_success"); // Use confirm sound for this close method

        sessionManager.removeSession(player.getUniqueId());
        player.closeInventory();
    }

    private void handleDuplicateAction(@NotNull Player player, @NotNull EditSession session) {
        if (!player.hasPermission("tedit.duplicate")) {
            settings.sendMessage(player, "error_no_permission", Placeholder.unparsed("permission", "tedit.duplicate"));
            settings.playSound(player, "action_fail");
            return;
        }

        // Validate before duplicating - make sure item still exists
        ItemStack actualItem = validateAndGetActualItem(player, session);
        if (actualItem == null) return;

        if (player.getInventory().firstEmpty() == -1) {
            settings.sendMessage(player, "error_inventory_full");
            settings.playSound(player, "action_fail");
            return;
        }

        player.getInventory().addItem(actualItem.clone()); // Clone the validated actual item
        settings.sendMessage(player, "success_item_duplicated", Placeholder.component("item_name", ItemUtil.getItemNameComponent(actualItem)));
        settings.playSound(player, "duplicate_success");
    }

    // --- Chat Input Handling (Applies Directly) ---
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        sessionManager.getSession(playerId).ifPresent(session -> {
            EditSession.EditActionState initialState = session.getCurrentState();
            if (initialState == EditSession.EditActionState.WAITING_FOR_RENAME || initialState == EditSession.EditActionState.WAITING_FOR_LORE_ADD) {
                event.setCancelled(true);
                // Use the raw chat string for MiniMessage parsing (fixes formatting issue)
                String rawInput = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.originalMessage());

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // Re-get session and validate state *again* on main thread
                        sessionManager.getSession(playerId).ifPresent(currentSession -> {
                            if (currentSession.getCurrentState() == initialState) {
                                // --- Validate Item Before Applying Chat Input ---
                                ItemStack actualItem = validateAndGetActualItem(player, currentSession);
                                if (actualItem == null) {
                                    // Session was closed by validation helper, just exit runnable
                                    return;
                                }

                                try {
                                    Component parsedInput = settings.miniMessage().deserialize(rawInput);

                                    // --- Apply directly to actual item ---
                                    if (initialState == EditSession.EditActionState.WAITING_FOR_RENAME) {
                                        ItemUtil.renameItem(actualItem, parsedInput);
                                        settings.sendMessage(player, "success_item_renamed");
                                    } else { // WAITING_FOR_LORE_ADD
                                        ItemUtil.addLoreLine(actualItem, parsedInput);
                                        settings.sendMessage(player, "success_lore_added");
                                    }

                                    // --- Update session/GUI state ---
                                    currentSession.setPreviewItem(actualItem.clone()); // Update preview to match
                                    currentSession.setCurrentState(EditSession.EditActionState.VIEWING);
                                    settings.playSound(player, "action_success");

                                    // --- Reopen the GUI ---
                                    // Use the *original* item data (unchanged definitionally) but the GUI will reflect the updated actual item
                                    EditGUI.createAndOpen(player, currentSession.getOriginalItem(), currentSession.getOriginalSlot(), plugin);

                                } catch (Exception e) {
                                    plugin.getLogger().log(Level.WARNING, "Error processing T-Edit chat input (" + initialState + ") for " + player.getName() + ": " + e.getMessage());
                                    settings.sendMessage(player, "error_input_processing");
                                    settings.playSound(player, "action_fail");
                                    currentSession.setCurrentState(EditSession.EditActionState.VIEWING); // Reset state
                                    // Attempt to reopen GUI even after error
                                    try {
                                         // Need original item (unchanged by this error) and slot
                                        ItemStack originalForReopen = currentSession.getOriginalItem();
                                        int slotForReopen = currentSession.getOriginalSlot();
                                        // Create new session or just open inv? createAndOpen handles replacing existing session.
                                        EditGUI.createAndOpen(player, originalForReopen, slotForReopen, plugin);
                                    } catch (Exception reopenEx) {
                                        plugin.getLogger().log(Level.SEVERE, "Failed to reopen GUI for " + player.getName() + " after chat input error.", reopenEx);
                                        // Ensure session is closed if reopen fails
                                        if(sessionManager.isActive(playerId)) {
                                            sessionManager.closeSession(playerId, "GUI reopen failed after chat input error");
                                        }
                                    }
                                }
                            } else if (settings.isDebugEnabled()) {
                                plugin.getLogger().info("[Debug] T-Edit session state for " + player.getName() + " changed (" + currentSession.getCurrentState() + ") before chat input ("+initialState+") could be processed. Input ignored.");
                            }
                        });
                    }
                }.runTask(plugin);
            }
        });
    }


    // --- Session Integrity Listeners ---

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID playerId = player.getUniqueId();

        sessionManager.getSession(playerId).ifPresent(session -> {
            // Closed via ESC or other external means while VIEWING
            // (Closing via item click or internal error already removed the session)
            if (event.getInventory().equals(session.getGuiInventory()) && session.getCurrentState() == EditSession.EditActionState.VIEWING) {
                if (settings.isDebugEnabled()) {
                    plugin.getLogger().info("[Debug] T-Edit GUI closed unexpectedly by " + player.getName() + ". Removing session data (changes saved progressively).");
                }
                sessionManager.removeSession(playerId); // Just remove data
                settings.sendMessage(player, "info_editor_closed_esc"); // Use specific message for ESC close
                settings.playSound(player, "cancel"); // Use cancel sound for ESC close
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // (Keep this method as is)
        UUID playerId = event.getPlayer().getUniqueId();
        if (sessionManager.isActive(playerId)) {
            if (settings.isDebugEnabled()) plugin.getLogger().info("[Debug] Player " + event.getPlayer().getName() + " quit with active T-Edit session. Removing session data.");
            sessionManager.removeSession(playerId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerItemHeldChange(PlayerItemHeldEvent event) {
        // (Keep this method as is, includes isActive check)
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        sessionManager.getSession(playerId).ifPresent(session -> {
            if (event.getPreviousSlot() == session.getOriginalSlot()) {
                 if (settings.isDebugEnabled()) plugin.getLogger().info("[Debug] Player " + player.getName() + " switched held item away from original slot (" + session.getOriginalSlot() + "). Closing T-Edit session.");
                 if (sessionManager.isActive(playerId)) {
                    settings.sendMessage(player, "error_original_item_moved");
                    settings.playSound(player, "action_fail");
                    sessionManager.closeSession(playerId, "Player changed held item slot");
                 }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        // (Keep this method as is, includes isActive check)
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        sessionManager.getSession(playerId).ifPresent(session -> {
            PlayerInventory inv = player.getInventory();
            if (inv.getHeldItemSlot() == session.getOriginalSlot()) {
                if (event.getItemDrop().getItemStack().getType() == session.getOriginalItem().getType()) {
                    if (settings.isDebugEnabled()) plugin.getLogger().info("[Debug] Player " + player.getName() + " dropped the item from the original slot (" + session.getOriginalSlot() + "). Closing T-Edit session.");
                    if (sessionManager.isActive(playerId)) {
                        settings.sendMessage(player, "error_original_item_moved");
                        settings.playSound(player, "action_fail");
                        sessionManager.closeSession(playerId, "Player dropped original item");
                    }
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // (Keep this method as is)
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();
        if (sessionManager.isActive(playerId)) {
            if (settings.isDebugEnabled()) plugin.getLogger().info("[Debug] Player " + player.getName() + " died with active T-Edit session. Closing session.");
            sessionManager.closeSession(playerId, "Player died");
        }
    }
}