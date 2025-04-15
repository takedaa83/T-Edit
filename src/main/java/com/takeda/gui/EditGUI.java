package com.takeda.gui;

import com.takeda.TEditPlugin;
import com.takeda.config.SettingsManager;
import com.takeda.sessions.EditSession;
import com.takeda.sessions.EditSessionManager;
import com.takeda.util.EnchantmentUtil;
import com.takeda.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage; // Ensure MiniMessage is imported
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EditGUI {

    private EditGUI() {} // Static class
    private static final Pattern MESSAGE_KEY_PATTERN = Pattern.compile("^<key:([\\w_.-]+)>$");
    private static final MiniMessage mm = MiniMessage.miniMessage(); // Cache MiniMessage instance

    /** Creates the GUI, populates it, and opens it for the player. */
    public static void createAndOpen(@NotNull Player player, @NotNull ItemStack itemToEdit, int itemSlot, @NotNull TEditPlugin plugin) throws IllegalStateException {
        SettingsManager settings = plugin.getSettingsManager();
        EditSessionManager sessionManager = plugin.getSessionManager();

        Inventory gui = Bukkit.createInventory(null, settings.getGuiSize(), settings.getGuiTitleComponent());
        EditSession session = sessionManager.createSession(player, itemToEdit, itemSlot, gui);

        try {
            populateBaseLayout(session, settings);
            placePreviewItem(session);
            populateEnchantments(session, settings);
            updatePaginationElements(session, settings); // Called by populateEnchantments too, but safe to call again

            player.openInventory(gui);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Critical error populating T-Edit GUI for " + player.getName(), e);
            sessionManager.closeSession(player.getUniqueId(), "GUI Population Error");
            settings.sendMessage(player, "error_gui_open_failed");
            settings.playSound(player, "action_fail");
        }
    }

    /** Fills the GUI with placeholder items in specific areas and static configured elements. */
    private static void populateBaseLayout(@NotNull EditSession session, @NotNull SettingsManager settings) {
        Inventory gui = session.getGuiInventory();
        ItemStack placeholder = settings.getPlaceholderItem();
        List<Integer> enchantSlotsList = settings.getEnchantmentSlots(); // Get configured enchant slots

        // Combine static element slots and enchantment slots to know what to skip
        Set<Integer> occupiedSlots = settings.getAllGuiElements().values().stream()
                .filter(SettingsManager.GuiElementConfig::enabled)
                .map(SettingsManager.GuiElementConfig::slot)
                .collect(Collectors.toSet());
        occupiedSlots.addAll(enchantSlotsList); // Add enchantment slots to skip list

        // Fill TOP ROW (0-8) with placeholder IF the slot is not occupied
        for (int i = 0; i < 9; i++) {
            if (!occupiedSlots.contains(i)) {
                gui.setItem(i, placeholder.clone());
            }
        }

        // Fill BOTTOM ROW (45-53) with placeholders IF the slot is not occupied
        // Pagination buttons will overwrite these later if needed.
        for (int i = 45; i < 54; i++) {
             if (!occupiedSlots.contains(i)) {
                gui.setItem(i, placeholder.clone());
             }
        }
        
        // Add purple glass pane frame around the preview item
        ItemStack purpleGlass = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta purpleMeta = purpleGlass.getItemMeta();
        if (purpleMeta != null) {
            purpleMeta.displayName(Component.empty());
            purpleMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, 
                ItemFlag.HIDE_ITEM_SPECIFICS, ItemFlag.HIDE_DYE, ItemFlag.HIDE_ARMOR_TRIM);
            purpleGlass.setItemMeta(purpleMeta);
        }
        
        // Place purple frame around preview item (slot 22)
        // Following exact slot numbers provided with adjustment (-1)
        int[] frameSlots = {12, 13, 14, 21, 23, 30, 31, 32};
        for (int slot : frameSlots) {
            // Skip any slot that's already occupied (by enchantments or other elements)
            if (!occupiedSlots.contains(slot)) {
                gui.setItem(slot, purpleGlass.clone());
                // Add to occupied slots to prevent other items from being placed here
                occupiedSlots.add(slot);
            }
        }

        // Place configured static elements (buttons etc.)
        settings.getAllGuiElements().values().forEach(config -> {
            // Skip preview item, pagination (handled dynamically)
            if (config.enabled() && !config.key().equals("preview_item") && !config.key().startsWith("page_")) {
                ItemStack item = createGuiItem(config);
                if (item != null) {
                    gui.setItem(config.slot(), item);
                }
            }
        });
    }

    @Nullable
    private static ItemStack createGuiItem(@NotNull SettingsManager.GuiElementConfig config) {
        // (Keep this method as is, seems correct)
        if (config.material().isAir()) return null;
        ItemStack item = new ItemStack(config.material());
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(config.name().decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
                meta.lore(config.lore().stream()
                        .map(line -> line.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE))
                        .collect(Collectors.toList()));
                if (config.customModelData() != -1) meta.setCustomModelData(config.customModelData());
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ITEM_SPECIFICS, ItemFlag.HIDE_DYE, ItemFlag.HIDE_ARMOR_TRIM);
                item.setItemMeta(meta);
            }
            return item;
        } catch (Exception e) {
            TEditPlugin.getInstance().getLogger().warning("Error creating GUI item for element '" + config.key() + "' (" + config.material() + "): " + e.getMessage());
            return null;
        }
    }

    public static void placePreviewItem(@NotNull EditSession session) {
         // (Keep this method as is, seems correct)
        SettingsManager settings = TEditPlugin.getInstance().getSettingsManager();
        settings.getGuiElement("preview_item").ifPresent(config -> {
            int slot = config.slot();
            if (slot >= 0 && slot < session.getGuiInventory().getSize()) {
                session.getGuiInventory().setItem(slot, session.getPreviewItem());
            } else if (settings.isDebugEnabled()){
                TEditPlugin.getInstance().getLogger().warning("[Debug] Invalid or missing slot configuration for 'preview_item'.");
            }
        });
    }

    /** Populates the enchantment display slots based on the current page, clearing unused slots. */
    public static void populateEnchantments(@NotNull EditSession session, @NotNull SettingsManager settings) {
        TEditPlugin plugin = TEditPlugin.getInstance();
        Inventory gui = session.getGuiInventory();
        List<Integer> enchantSlots = settings.getEnchantmentSlots();
        Player player = Bukkit.getPlayer(session.getPlayerId());

        if (enchantSlots.isEmpty() || player == null || !player.isOnline()) return;

        // --- Clear existing enchantment slots first ---
        for (int slot : enchantSlots) {
             if (slot >= 0 && slot < gui.getSize()) {
                 gui.setItem(slot, null); // Set to null to clear
             }
        }

        List<Enchantment> applicableEnchants = EnchantmentUtil.getApplicableEnchantmentsForGui(session.getPreviewItem(), player, settings);

        int slotsPerPage = enchantSlots.size();
        int totalEnchants = applicableEnchants.size();
        int totalPages = (totalEnchants == 0) ? 1 : (int) Math.ceil((double) totalEnchants / slotsPerPage);
        session.setTotalEnchantmentPages(totalPages);

        int currentPage = session.getEnchantmentPage();
        int startIndex = currentPage * slotsPerPage;
        int endIndex = Math.min(startIndex + slotsPerPage, totalEnchants);

        List<Enchantment> enchantsToShow = (startIndex < totalEnchants)
                ? applicableEnchants.subList(startIndex, endIndex)
                : Collections.emptyList();

        // --- Populate Slots with Books (No Placeholders Here) ---
        for (int i = 0; i < enchantsToShow.size(); i++) {
            // Ensure we don't go out of bounds for enchantSlots list
             if (i >= enchantSlots.size()) break;

             int slot = enchantSlots.get(i);
             if (slot >= 0 && slot < gui.getSize()) {
                 gui.setItem(slot, createEnchantmentBook(enchantsToShow.get(i), session, settings, plugin, player));
             }
        }
        updatePaginationElements(session, settings); // Update pagination after potentially changing total pages
    }

    @NotNull
    private static ItemStack createEnchantmentBook(@NotNull Enchantment enchant, @NotNull EditSession session, @NotNull SettingsManager settings, @NotNull TEditPlugin plugin, @NotNull Player player) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta == null) return book;

        ItemStack previewItem = session.getPreviewItem();
        int currentLevel = previewItem.getEnchantmentLevel(enchant);
        int maxLevel = EnchantmentUtil.getMaxLevel(enchant, player, settings);
        int vanillaMax = enchant.getMaxLevel();

        boolean conflicts = false;
        if (currentLevel == 0) {
            boolean canBypassConflict = settings.isAllowBypassConflicts() && player.hasPermission("tedit.enchant.bypassconflict");
            conflicts = !canBypassConflict && EnchantmentUtil.conflictsWithExisting(enchant, previewItem);
        }

        // Debug placeholder values if needed
        if (settings.isDebugEnabled()) {
            plugin.getLogger().info("[Debug Placeholder] Enchant: " + enchant.getKey() + ", Name: " + EnchantmentUtil.getFriendlyName(enchant) + ", Level: " + currentLevel + ", MaxLevel: " + maxLevel);
        }

        // Get the raw format string from settings
        String nameFormat = settings.getEnchantBookNameFormat();
        
        // Handle direct replacement for common placeholders
        // This ensures the placeholders work even without MiniMessage's more complex processing
        nameFormat = nameFormat
            .replace("{enchant_name}", settings.miniMessage().serialize(EnchantmentUtil.getFriendlyName(enchant)))
            .replace("{level}", String.valueOf(currentLevel))
            .replace("{max_level}", String.valueOf(maxLevel))
            .replace("{vanilla_max_level}", String.valueOf(vanillaMax))
            .replace("{enchant_key}", enchant.getKey().toString());

        // Now create tag resolver as backup for any other placeholders
        TagResolver bookPlaceholders = TagResolver.builder()
                .resolver(Placeholder.unparsed("enchant_key", enchant.getKey().toString()))
                .resolver(Placeholder.component("enchant_name", EnchantmentUtil.getFriendlyName(enchant)))
                .resolver(Placeholder.unparsed("level", String.valueOf(currentLevel)))
                .resolver(Placeholder.unparsed("max_level", String.valueOf(maxLevel)))
                .resolver(Placeholder.unparsed("vanilla_max_level", String.valueOf(vanillaMax)))
                .build();

        // Set Name using book placeholders
        try {
            meta.displayName(mm.deserialize(nameFormat, bookPlaceholders)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        } catch (Exception e) {
             plugin.getLogger().log(Level.SEVERE, "[Debug Placeholder] Error deserializing book name: " + nameFormat, e);
             meta.displayName(Component.text("NAME PARSE ERROR", NamedTextColor.RED));
        }

        // Build Lore - using cached MiniMessage instance
        List<Component> lore = new ArrayList<>();
        List<String> loreFormats = settings.getEnchantBookLoreFormat();
        boolean canLevelUp = currentLevel < maxLevel;
        boolean canLevelDown = currentLevel > 0;

        for (String format : loreFormats) {
            // Apply direct placeholder replacement for lore lines too
            String processedFormat = format
                .replace("{enchant_name}", settings.miniMessage().serialize(EnchantmentUtil.getFriendlyName(enchant)))
                .replace("{level}", String.valueOf(currentLevel))
                .replace("{max_level}", String.valueOf(maxLevel))
                .replace("{vanilla_max_level}", String.valueOf(vanillaMax))
                .replace("{enchant_key}", enchant.getKey().toString());
                
            Component line;
            try {
                Matcher matcher = MESSAGE_KEY_PATTERN.matcher(processedFormat);
                if (matcher.matches()) {
                    String messageKey = matcher.group(1);
                    boolean shouldAdd = false;
                    
                    switch (messageKey) {
                        case "lore_enchant_conflicts": shouldAdd = conflicts; break;
                        case "lore_enchant_howto_increase": shouldAdd = !conflicts && canLevelUp; break;
                        case "lore_enchant_howto_decrease": shouldAdd = !conflicts && canLevelDown; break;
                        case "lore_enchant_howto_max_shift": shouldAdd = !conflicts && canLevelUp && maxLevel > 1; break;
                        case "lore_enchant_howto_max_bypass": shouldAdd = !conflicts && canLevelUp && maxLevel > vanillaMax; break;
                        case "lore_enchant_howto_remove_shift": shouldAdd = !conflicts && canLevelDown; break;
                        case "lore_enchant_howto_add": shouldAdd = !conflicts && currentLevel == 0; break;
                        default:
                             if (processedFormat.contains("---")) {
                                lore.add(mm.deserialize(processedFormat, bookPlaceholders).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
                             } else {
                                // Use raw message without prefix
                                String rawMessage = settings.getRawMessageWithoutPrefix(messageKey);
                                lore.add(mm.deserialize(rawMessage, bookPlaceholders).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
                             }
                            continue;
                    }
                    if (shouldAdd) {
                        // Get raw message without prefix and parse it directly
                        String rawMessage = settings.getRawMessageWithoutPrefix(messageKey);
                        line = mm.deserialize(rawMessage, bookPlaceholders);
                    } else {
                        continue; // Skip if condition not met
                    }
                } else {
                    // Not a message key, parse directly with book placeholders
                    line = mm.deserialize(processedFormat, bookPlaceholders);
                }
                lore.add(line.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[Debug Placeholder] Error deserializing lore line: " + format, e);
                lore.add(Component.text("LORE PARSE ERROR", NamedTextColor.RED));
            }
        }
        meta.lore(lore);

        meta.getPersistentDataContainer().set(plugin.getEnchantmentPDCKey(), PersistentDataType.STRING, enchant.getKey().toString());
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ITEM_SPECIFICS, ItemFlag.HIDE_DYE, ItemFlag.HIDE_ARMOR_TRIM);
        book.setItemMeta(meta);
        return book;
    }

    public static void updateEnchantmentBook(@NotNull EditSession session, @NotNull Enchantment enchant) {
        // (Keep this method as is, seems correct - relies on populateEnchantments if enchant disappears)
        TEditPlugin plugin = TEditPlugin.getInstance();
        SettingsManager settings = plugin.getSettingsManager();
        Inventory gui = session.getGuiInventory();
        NamespacedKey pdcKey = plugin.getEnchantmentPDCKey();
        String targetEnchantKeyStr = enchant.getKey().toString();
        List<Integer> enchantSlots = settings.getEnchantmentSlots();
        Player player = Bukkit.getPlayer(session.getPlayerId());

        if (player == null || !player.isOnline() || enchantSlots.isEmpty()) return;

        List<Enchantment> applicableEnchants = EnchantmentUtil.getApplicableEnchantmentsForGui(session.getPreviewItem(), player, settings);
        int enchantIndex = -1;
        for (int i = 0; i < applicableEnchants.size(); i++) {
            if (applicableEnchants.get(i).equals(enchant)) {
                enchantIndex = i;
                break;
            }
        }

        if (enchantIndex == -1) {
            if(settings.isDebugEnabled()) plugin.getLogger().info("[Debug] Enchant " + targetEnchantKeyStr + " no longer applicable during update. Refreshing page.");
            populateEnchantments(session, settings); // Refresh whole page
            return;
        }

        int slotsPerPage = enchantSlots.size();
        int expectedPage = enchantIndex / slotsPerPage;
        int indexOnPage = enchantIndex % slotsPerPage;

        if (session.getEnchantmentPage() == expectedPage) {
            // Ensure indexOnPage is valid for the enchantSlots list
            if (indexOnPage < enchantSlots.size()) {
                int slot = enchantSlots.get(indexOnPage);
                 if (slot >= 0 && slot < gui.getSize()) {
                    gui.setItem(slot, createEnchantmentBook(enchant, session, settings, plugin, player));
                    return;
                 }
            }
        } else if(settings.isDebugEnabled()) {
            plugin.getLogger().info("[Debug] Skipped updating book " + targetEnchantKeyStr + " on page " + expectedPage + " (viewing " + session.getEnchantmentPage() + ")");
        }
    }


    /** Updates the pagination buttons (prev/next) and info display. */
    public static void updatePaginationElements(@NotNull EditSession session, @NotNull SettingsManager settings) {
        Inventory gui = session.getGuiInventory();
        int currentPage = session.getEnchantmentPage();
        int totalPages = session.getTotalEnchantmentPages();
        boolean hasPrev = currentPage > 0;
        boolean hasNext = currentPage < totalPages - 1;
        ItemStack placeholder = settings.getPlaceholderItem();

        // Debug logs if enabled
        if (settings.isDebugEnabled()) {
            TEditPlugin.getInstance().getLogger().info("[Debug Placeholder] Page: " + (currentPage + 1) + ", TotalPages: " + totalPages);
        }

        settings.getGuiElement("page_prev").ifPresent(config -> {
            if (!config.enabled()) return; int slot = config.slot(); if(slot < 0 || slot >= gui.getSize()) return;
            gui.setItem(slot, hasPrev ? createGuiItem(config) : placeholder.clone());
        });
        
        settings.getGuiElement("page_next").ifPresent(config -> {
            if (!config.enabled()) return; int slot = config.slot(); if(slot < 0 || slot >= gui.getSize()) return;
            gui.setItem(slot, hasNext ? createGuiItem(config) : placeholder.clone());
        });
        
        settings.getGuiElement("page_info").ifPresent(config -> {
            if (!config.enabled()) return; int slot = config.slot(); if(slot < 0 || slot >= gui.getSize()) return;
            ItemStack infoItem = createGuiItem(config);
            if (infoItem == null) { gui.setItem(slot, placeholder.clone()); return; }

            ItemMeta meta = infoItem.getItemMeta();
            if (meta != null) {
                String nameFormat = settings.miniMessage().serialize(config.name());
                List<String> loreFormats = config.lore().stream()
                    .map(comp -> settings.miniMessage().serialize(comp))
                    .collect(Collectors.toList());

                // Direct string replacement first - most reliable approach
                nameFormat = nameFormat
                    .replace("{page}", String.valueOf(currentPage + 1))
                    .replace("<page>", String.valueOf(currentPage + 1))
                    .replace("{total_pages}", String.valueOf(totalPages))
                    .replace("<total_pages>", String.valueOf(totalPages));

                // Then create TagResolver as backup for any other placeholders
                TagResolver pageResolver = TagResolver.builder()
                        .resolver(Placeholder.unparsed("page", String.valueOf(currentPage + 1))) // Display 1-based page
                        .resolver(Placeholder.unparsed("total_pages", String.valueOf(totalPages))) // Use unparsed for consistency
                        .build();

                try {
                    meta.displayName(mm.deserialize(nameFormat, pageResolver)
                            .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
                    
                    List<Component> finalLore = new ArrayList<>();
                    for (String format : loreFormats) {
                        // Apply direct placeholder replacement for lore lines
                        String processedFormat = format
                            .replace("{page}", String.valueOf(currentPage + 1))
                            .replace("<page>", String.valueOf(currentPage + 1))
                            .replace("{total_pages}", String.valueOf(totalPages))
                            .replace("<total_pages>", String.valueOf(totalPages));
                            
                        finalLore.add(mm.deserialize(processedFormat, pageResolver)
                            .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
                    }
                    meta.lore(finalLore);
                } catch (Exception e) {
                    TEditPlugin.getInstance().getLogger().log(Level.SEVERE, "[Debug Placeholder] Error deserializing page info item", e);
                    meta.displayName(Component.text("PAGE INFO ERROR", NamedTextColor.RED));
                    meta.lore(null);
                }

                if (config.customModelData() != -1) meta.setCustomModelData(config.customModelData());
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ITEM_SPECIFICS, ItemFlag.HIDE_DYE, ItemFlag.HIDE_ARMOR_TRIM);
                infoItem.setItemMeta(meta);
            }
            gui.setItem(slot, infoItem);
        });
    }
}