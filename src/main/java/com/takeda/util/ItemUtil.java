package com.takeda.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for common ItemStack manipulations and information retrieval.
 */
public class ItemUtil {

    private ItemUtil() {} // Static class

    /** Gets display name Component, prefers custom, falls back to translatable, removes default italics. */
    @NotNull
    public static Component getItemNameComponent(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) return Component.empty();

        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component displayName = meta.displayName();
            if (displayName != null) {
                // Only remove italics if it wasn't explicitly set with formatting
                return displayName.hasDecoration(TextDecoration.ITALIC) ? displayName : displayName.decoration(TextDecoration.ITALIC, false);
            }
        }
        // Fallback: Use translatable key, force italics off
        // Ensure the translation key isn't null, though unlikely for non-air items
        String key = itemStack.getType().getItemTranslationKey();
        if (key == null) {
            // Fallback for potentially custom/invalid items
            return Component.text(itemStack.getType().name().toLowerCase().replace('_', ' ')).decoration(TextDecoration.ITALIC, false);
        }
        return Component.translatable(key).decoration(TextDecoration.ITALIC, false);
    }

    /** Safely repairs a Damageable item fully. */
    public static boolean repairItem(@NotNull ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack cannot be null");
        ItemMeta meta = itemStack.getItemMeta();
        if (meta instanceof Damageable damageable && damageable.hasDamage()) {
            damageable.setDamage(0);
            itemStack.setItemMeta(meta);
            return true;
        }
        return false;
    }

    /** Clears all lore from an item. */
    public static boolean clearLore(@NotNull ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack cannot be null");
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && meta.hasLore()) {
            meta.lore(null); // Setting lore to null clears it
            itemStack.setItemMeta(meta);
            return true;
        }
        return false;
    }

    /**
     * Renames an item using a Component. Use null component to clear custom name.
     */
    public static void renameItem(@NotNull ItemStack itemStack, @Nullable Component nameComponent) {
        Objects.requireNonNull(itemStack, "itemStack cannot be null");
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.displayName(nameComponent);
            itemStack.setItemMeta(meta);
        }
    }

    /**
     * Adds a Component lore line. Initializes list if needed.
     */
    public static void addLoreLine(@NotNull ItemStack itemStack, @NotNull Component loreLineComponent) {
        Objects.requireNonNull(itemStack, "itemStack cannot be null");
        Objects.requireNonNull(loreLineComponent, "loreLineComponent cannot be null");
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            List<Component> currentLore = meta.lore();
            // Ensure we have a mutable list, creating new if currentLore is null or immutable
            List<Component> newLore = (currentLore == null) ? new ArrayList<>() : new ArrayList<>(currentLore);
            newLore.add(loreLineComponent);
            meta.lore(newLore); // Set the modified list back
            itemStack.setItemMeta(meta);
        }
    }
}