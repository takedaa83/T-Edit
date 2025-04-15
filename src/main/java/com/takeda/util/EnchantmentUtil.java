package com.takeda.util;

import com.takeda.TEditPlugin;
import com.takeda.config.SettingsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta; // Import for Enchanted Books
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Utility class for enchantment-related operations like compatibility, naming, permissions, and application.
 */
public class EnchantmentUtil {

    private EnchantmentUtil() {} // Static class

    // Set of materials generally considered enchantable for armor/pvp/tools
    // (Keep this as is, seems fine)
    private static final Set<Material> ENCHANTABLE_BASE_TYPES = EnumSet.of(
            Material.NETHERITE_SWORD, Material.DIAMOND_SWORD, Material.IRON_SWORD, Material.GOLDEN_SWORD, Material.STONE_SWORD, Material.WOODEN_SWORD,
            Material.NETHERITE_AXE, Material.DIAMOND_AXE, Material.IRON_AXE, Material.GOLDEN_AXE, Material.STONE_AXE, Material.WOODEN_AXE,
            Material.NETHERITE_PICKAXE, Material.DIAMOND_PICKAXE, Material.IRON_PICKAXE, Material.GOLDEN_PICKAXE, Material.STONE_PICKAXE, Material.WOODEN_PICKAXE,
            Material.NETHERITE_SHOVEL, Material.DIAMOND_SHOVEL, Material.IRON_SHOVEL, Material.GOLDEN_SHOVEL, Material.STONE_SHOVEL, Material.WOODEN_SHOVEL,
            Material.NETHERITE_HOE, Material.DIAMOND_HOE, Material.IRON_HOE, Material.GOLDEN_HOE, Material.STONE_HOE, Material.WOODEN_HOE,
            Material.BOW, Material.CROSSBOW, Material.TRIDENT, Material.FISHING_ROD, Material.SHEARS,
            Material.FLINT_AND_STEEL, Material.SHIELD, Material.ELYTRA, Material.TURTLE_HELMET,
            Material.BOOK, Material.ENCHANTED_BOOK // Necessary
            // Add others if needed
    );

    public static boolean isEnchantableType(@NotNull ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack cannot be null");
        Material type = itemStack.getType();
        if (type.isAir() || type.isBlock() || type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION || type == Material.ARROW || type == Material.SPECTRAL_ARROW || type == Material.TIPPED_ARROW || type.isEdible()) {
            if (type != Material.ENCHANTED_BOOK && type != Material.BOOK) {
                return false;
            }
        }
        ItemMeta meta = itemStack.getItemMeta();
        return meta instanceof ArmorMeta || meta instanceof Damageable || ENCHANTABLE_BASE_TYPES.contains(type) || type == Material.ENCHANTED_BOOK; // Explicitly allow enchanted book meta
    }


    @NotNull
    public static List<Enchantment> getApplicableEnchantmentsForGui(@NotNull ItemStack itemStack, @NotNull Player player, @NotNull SettingsManager settings) {
        Objects.requireNonNull(itemStack, "itemStack cannot be null");
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(settings, "settings cannot be null");

        // Allow enchanting books regardless of isEnchantableType maybe? No, use canEnchantItem.
        if (itemStack.getType().isAir()) { // Simplified check - rely on canEnchantItem later
            return Collections.emptyList();
        }

        List<Enchantment> applicable = new ArrayList<>();
        Stream<Enchantment> enchantmentStream = Registry.ENCHANTMENT.stream();

        // For Enchanted Books, filter slightly differently
        boolean isBook = itemStack.getType() == Material.ENCHANTED_BOOK;

        enchantmentStream
                .filter(Objects::nonNull)
                // 1. Basic Compatibility Check (Vanilla rules) OR if it's a book (books can hold any enchant)
                .filter(enchantment -> isBook || enchantment.canEnchantItem(itemStack))
                // 2. Player Permission & Config Check (Treasure, Curse, Base)
                .filter(enchantment -> canPlayerApply(player, enchantment, settings))
                // Sort alphabetically by key for consistent display order
                .sorted(Comparator.comparing(e -> e.getKey().toString()))
                .forEach(applicable::add);

        return applicable;
    }

    @NotNull
    public static Component getFriendlyName(@NotNull Enchantment enchantment) {
        Objects.requireNonNull(enchantment, "enchantment cannot be null");
        @SuppressWarnings("deprecation")
        String translationKey = enchantment.translationKey();
        TranslatableComponent name = Component.translatable(translationKey);
        return name.decoration(TextDecoration.ITALIC, false);
    }

    /** Checks vanilla conflicts between a new enchantment and existing ones on item. */
    public static boolean conflictsWithExisting(@NotNull Enchantment newEnchantment, @NotNull ItemStack itemStack) {
        Objects.requireNonNull(newEnchantment, "newEnchantment cannot be null");
        Objects.requireNonNull(itemStack, "itemStack cannot be null");

        Map<Enchantment, Integer> existingEnchants;
        ItemMeta meta = itemStack.getItemMeta();

        // Get enchantments correctly from item or stored enchantments on book
        if (meta instanceof EnchantmentStorageMeta bookMeta) {
            existingEnchants = bookMeta.getStoredEnchants();
        } else if (meta != null && meta.hasEnchants()) {
            existingEnchants = meta.getEnchants();
        } else {
            return false; // No existing enchants to conflict with
        }

        // Enchanted books ignore conflicts conceptually, but we check anyway for consistency
        // if (itemStack.getType() == Material.ENCHANTED_BOOK) { return false; }

        for (Enchantment existing : existingEnchants.keySet()) {
            if (newEnchantment.equals(existing)) continue; // Cannot conflict with itself
            if (newEnchantment.conflictsWith(existing)) {
                return true;
            }
        }
        return false;
    }


    public static boolean canPlayerApply(@NotNull Player player, @NotNull Enchantment enchantment, @NotNull SettingsManager settings) {
        // (Keep this method as is, seems correct)
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(enchantment, "Enchantment cannot be null");
        Objects.requireNonNull(settings, "SettingsManager cannot be null");

        if (!player.hasPermission("tedit.enchant.base")) {
            return false;
        }
        if (enchantment.isTreasure()) {
            if (!settings.isAllowTreasureEnchants() || !player.hasPermission("tedit.enchant.treasure")) {
                return false;
            }
        }
        if (enchantment.isCursed()) {
            if (!settings.isAllowCurseEnchants() || !player.hasPermission("tedit.enchant.curses")) {
                return false;
            }
        }
        return true;
    }

    public static int getMaxLevel(@NotNull Enchantment enchantment, @NotNull Player player, @NotNull SettingsManager settings) {
        // (Keep this method as is, seems correct)
        Objects.requireNonNull(enchantment, "enchantment cannot be null");
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(settings, "settings cannot be null");

        boolean canBypassLevel = settings.isAllowBypassLevelCaps() && player.hasPermission("tedit.enchant.bypasslevel");

        if (canBypassLevel) {
            return 255; // Practical high limit
        } else {
            return enchantment.getMaxLevel();
        }
    }

    /**
     * Applies an enchantment to an item, respecting level caps and conflict rules based on player permissions and config.
     * This version performs all checks manually and then uses addUnsafeEnchantment/storeEnchantment.
     *
     * @param itemStack The item to enchant (will be modified directly).
     * @param enchantment The enchantment to apply.
     * @param level The desired level (0 to remove).
     * @param player The player performing the action.
     * @param settings The settings manager.
     * @return true if the enchantment was successfully applied or removed, false otherwise.
     */
    public static boolean applyEnchantment(@NotNull ItemStack itemStack, @NotNull Enchantment enchantment, int level, @NotNull Player player, @NotNull SettingsManager settings) {
        Objects.requireNonNull(itemStack, "itemStack cannot be null");
        Objects.requireNonNull(enchantment, "enchantment cannot be null");
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(settings, "settings cannot be null");

        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            TEditPlugin.getInstance().getLogger().warning("Cannot apply enchant to item with null meta: " + itemStack.getType());
            return false; // Cannot enchant item without meta
        }

        boolean isBook = meta instanceof EnchantmentStorageMeta;

        // --- Removal ---
        if (level <= 0) {
            boolean removed;
            if (isBook) {
                EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) meta;
                removed = bookMeta.removeStoredEnchant(enchantment);
            } else {
                removed = meta.removeEnchant(enchantment);
            }
            if (removed) {
                itemStack.setItemMeta(meta);
            }
            return removed; // Return true if removal happened or wasn't needed
        }

        // --- Permissions Check ---
        if (!canPlayerApply(player, enchantment, settings)) {
            // Log? Or rely on caller feedback? Let's log if debug enabled.
             if (settings.isDebugEnabled()) TEditPlugin.getInstance().getLogger().info("[Debug] Denied enchant " + enchantment.getKey() + " for " + player.getName() + " due to permissions/config.");
            return false;
        }

        // --- Conflict Check ---
        boolean canBypassConflict = settings.isAllowBypassConflicts() && player.hasPermission("tedit.enchant.bypassconflict");
        if (!isBook && !canBypassConflict && conflictsWithExisting(enchantment, itemStack)) {
            // Log? Caller handles feedback.
             if (settings.isDebugEnabled()) TEditPlugin.getInstance().getLogger().info("[Debug] Denied enchant " + enchantment.getKey() + " for " + player.getName() + " due to conflict.");
            return false; // Conflict exists and player cannot bypass (ignore conflicts for books)
        }

        // --- Level Clamping ---
        int maxLevel = getMaxLevel(enchantment, player, settings);
        int finalLevel = Math.min(level, maxLevel); // Clamp to max level allowed
        finalLevel = Math.max(1, finalLevel); // Ensure level is at least 1

        // --- Apply using appropriate method ---
        try {
            boolean applied;
            if (isBook) {
                EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) meta;
                // storeEnchantment: levelRestriction = false (allows bypass), conflict = false (implicitly ignored by books)
                applied = bookMeta.addStoredEnchant(enchantment, finalLevel, true); // Use ignoreLevelRestriction=true

            } else {
                // Use addUnsafeEnchantment: bypasses Bukkit's level AND conflict checks.
                // We've already done our own checks, so this is safe.
                 applied = meta.addEnchant(enchantment, finalLevel, true); // Use ignoreLevelRestriction=true
                // Note: addEnchant with ignoreLevelRestriction=true effectively acts like addUnsafeEnchantment
                // regarding levels, but might still respect conflicts internally in some versions?
                // Using addEnchant(..., true) seems safer than addUnsafeEnchantment if available.
                // If issues persist, revert to itemStack.addUnsafeEnchantment(enchantment, finalLevel);
            }

            if (applied) {
                itemStack.setItemMeta(meta); // IMPORTANT: Apply the modified meta back to the ItemStack
                return true;
            } else {
                 // This might happen if addStoredEnchant/addEnchant returns false for some internal reason
                 TEditPlugin.getInstance().getLogger().warning("Failed applying enchant " + enchantment.getKey() + " L" + finalLevel + " to " + itemStack.getType() + " - apply method returned false.");
                 return false;
            }

        } catch (IllegalArgumentException e) { // Catch potential errors like invalid level if ignoreLevelRestriction wasn't fully respected
            TEditPlugin.getInstance().getLogger().log(Level.WARNING, "IllegalArgumentException applying enchant " + enchantment.getKey() + " L" + finalLevel + " to " + itemStack.getType(), e);
            return false;
        } catch (Exception e) { // Catch unexpected errors
             TEditPlugin.getInstance().getLogger().log(Level.SEVERE, "Unexpected error applying enchant " + enchantment.getKey() + " L" + finalLevel + " to " + itemStack.getType(), e);
            return false;
        }
    }


    public static boolean removeAllEnchantments(@NotNull ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack cannot be null");
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return false;

        boolean changed = false;
        List<Enchantment> keysToRemove;

        if (meta instanceof EnchantmentStorageMeta bookMeta) {
            keysToRemove = new ArrayList<>(bookMeta.getStoredEnchants().keySet());
            if (!keysToRemove.isEmpty()) {
                keysToRemove.forEach(bookMeta::removeStoredEnchant);
                changed = true;
            }
        } else {
            keysToRemove = new ArrayList<>(meta.getEnchants().keySet());
             if (!keysToRemove.isEmpty()) {
                keysToRemove.forEach(meta::removeEnchant);
                changed = true;
            }
        }

        if (changed) {
            itemStack.setItemMeta(meta);
        }
        return changed;
    }

    @Nullable
    public static Enchantment getEnchantmentByKey(@Nullable String keyString) {
       // (Keep this method as is, seems correct)
        if (keyString == null || keyString.isBlank()) return null;
        try {
            NamespacedKey key = NamespacedKey.fromString(keyString.toLowerCase());
            return key != null ? Registry.ENCHANTMENT.get(key) : null;
        } catch (IllegalArgumentException e) {
            TEditPlugin.getInstance().getLogger().warning("Invalid format for enchantment key: '" + keyString + "'");
            return null;
        } catch (Exception e) {
            TEditPlugin.getInstance().getLogger().log(Level.WARNING, "Error looking up enchantment by key: '" + keyString + "'", e);
            return null;
        }
    }
}