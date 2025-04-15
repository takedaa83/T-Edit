package com.takeda.sessions;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Holds the state for an active T-Edit GUI session, including the player,
 * original item state, current preview item, GUI inventory, and current action state.
 */
public class EditSession {

    /** Defines states, especially when waiting for player input. */
    public enum EditActionState { VIEWING, WAITING_FOR_RENAME, WAITING_FOR_LORE_ADD }
    // TODO: Add states for WAITING_FOR_LORE_EDIT, WAITING_FOR_LORE_REMOVE if implementing advanced lore editing

    private final UUID playerId;
    private final ItemStack originalItem; // Clone of the item at session start
    private final int originalSlot;       // Inventory slot of the original item
    private ItemStack previewItem;      // Active clone being modified in the GUI
    private final Inventory guiInventory; // The T-Edit GUI inventory object
    private EditActionState currentState;
    private int enchantmentPage;        // Current page of enchantments being viewed
    private int totalEnchantmentPages;  // Total number of enchantment pages available

    public EditSession(@NotNull Player player, @NotNull ItemStack itemToEdit, int slot, @NotNull Inventory guiInventory) {
        this.playerId = player.getUniqueId();
        this.originalItem = Objects.requireNonNull(itemToEdit, "Item to edit cannot be null").clone();
        this.previewItem = originalItem.clone(); // Start preview as clone
        this.originalSlot = slot;
        this.guiInventory = Objects.requireNonNull(guiInventory, "GUI Inventory cannot be null");
        this.currentState = EditActionState.VIEWING;
        this.enchantmentPage = 0; // Start at first page
        this.totalEnchantmentPages = 1; // Default to 1 page until calculated
    }

    // --- Getters ---
    @NotNull public UUID getPlayerId() { return playerId; }
    @NotNull public ItemStack getOriginalItem() { return originalItem.clone(); /* Always return a clone */ }
    public int getOriginalSlot() { return originalSlot; }
    @NotNull public ItemStack getPreviewItem() { return previewItem; /* Return direct ref for modification */ }
    @NotNull public Inventory getGuiInventory() { return guiInventory; }
    @NotNull public EditActionState getCurrentState() { return currentState; }
    public int getEnchantmentPage() { return enchantmentPage; }
    public int getTotalEnchantmentPages() { return totalEnchantmentPages; }

    // --- Setters ---
    /** Updates the preview item. Input is cloned to protect internal state. */
    public void setPreviewItem(@NotNull ItemStack previewItem) {
        this.previewItem = Objects.requireNonNull(previewItem, "Preview item cannot be null").clone();
    }
    public void setCurrentState(@NotNull EditActionState currentState) {
        this.currentState = Objects.requireNonNull(currentState, "Current state cannot be null");
    }
    public void setEnchantmentPage(int enchantmentPage) {
        // Ensure page is within valid bounds (0 to totalPages - 1)
        this.enchantmentPage = Math.max(0, Math.min(enchantmentPage, Math.max(0, this.totalEnchantmentPages - 1)));
    }
    public void setTotalEnchantmentPages(int totalEnchantmentPages) {
        this.totalEnchantmentPages = Math.max(1, totalEnchantmentPages); // Ensure at least 1 page
        // Adjust current page if it becomes invalid due to reduced total pages
        this.enchantmentPage = Math.min(this.enchantmentPage, Math.max(0, this.totalEnchantmentPages - 1));
    }

    // --- Overrides ---
    @Override public boolean equals(Object o) { return this == o || (o instanceof EditSession s && playerId.equals(s.playerId)); }
    @Override public int hashCode() { return playerId.hashCode(); }
    @Override public String toString() {
        return "EditSession{playerId=" + playerId + ", slot=" + originalSlot + ", state=" + currentState
                + ", page=" + enchantmentPage + "/" + totalEnchantmentPages + '}';
    }
}