package com.takeda.sessions;

import com.takeda.TEditPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages active T-Edit GUI sessions for players.
 */
public class EditSessionManager {

    private final TEditPlugin plugin;
    private final Map<UUID, EditSession> activeSessions = new ConcurrentHashMap<>();

    public EditSessionManager(@NotNull TEditPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin instance cannot be null");
    }

    /** Creates and registers a new session, closing any existing one first. */
    @NotNull
    public EditSession createSession(@NotNull Player player, @NotNull ItemStack originalItem, int originalSlot, @NotNull Inventory guiInventory) {
        UUID playerId = player.getUniqueId();
        closeSession(playerId, "Starting new session"); // Close previous if exists

        EditSession session = new EditSession(player, originalItem, originalSlot, guiInventory);
        activeSessions.put(playerId, session);
        if (plugin.getSettingsManager().isDebugEnabled()) {
            plugin.getLogger().info("[Debug] Created T-Edit session for " + player.getName());
        }
        return session;
    }

    /** Retrieves the active session for a player UUID, if one exists. */
    @NotNull
    public Optional<EditSession> getSession(@NotNull UUID playerId) {
        return Optional.ofNullable(activeSessions.get(playerId));
    }

    /** Checks if a player has an active T-Edit session. */
    public boolean isActive(@NotNull UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    /**
     * Removes session data. Internal use mainly; use closeSession for proper cleanup.
     */
    public void removeSession(@NotNull UUID playerId) {
        EditSession removed = activeSessions.remove(playerId);
        if (removed != null && plugin.getSettingsManager().isDebugEnabled()) {
            plugin.getLogger().info("[Debug] Removed T-Edit session data for UUID: " + playerId + ". Session Details: " + removed);
        }
    }

    /** Safely closes a player's T-Edit GUI (if applicable) and removes their session. */
    public void closeSession(@NotNull UUID playerId, @NotNull String reason) {
        EditSession session = activeSessions.remove(playerId); // Remove data first
        if (session != null) {
            if (plugin.getSettingsManager().isDebugEnabled()) {
                plugin.getLogger().info("[Debug] Closing T-Edit session for UUID: " + playerId + ". Reason: " + reason + ". Session Details: " + session);
            }
            // Attempt to close inventory only if player is online and viewing the correct GUI
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                try {
                    // Check if the currently open inventory's top inventory is the one from our session
                    Inventory topInv = player.getOpenInventory().getTopInventory();
                    // Check equality based on the Inventory object itself AND ensure the session GUI is not null
                    if (session.getGuiInventory() != null && session.getGuiInventory().equals(topInv)) {
                        // Run task later to avoid issues within event handlers (like InventoryClickEvent)
                        new BukkitRunnable() {
                            @Override public void run() {
                                // Double-check player is still online before closing
                                if (player.isOnline()) {
                                    player.closeInventory();
                                }
                            }
                        }.runTask(plugin);
                    }
                } catch (Exception e) {
                    // Ignore errors if player state is somehow invalid (e.g., logged out between check and execution)
                    if (plugin.getSettingsManager().isDebugEnabled()) {
                        plugin.getLogger().log(Level.FINEST, "Minor exception during inventory close attempt for " + playerId, e);
                    }
                }
            }
        }
    }

    /** Closes all active sessions (e.g., on plugin disable or reload). */
    public void closeAllSessions(@NotNull String reason) {
        if (activeSessions.isEmpty()) return;
        plugin.getLogger().info("Closing " + activeSessions.size() + " active T-Edit session(s)... Reason: " + reason);
        // Iterate over a copy of keys to allow modification during iteration
        for (UUID playerId : Set.copyOf(activeSessions.keySet())) {
            try {
                closeSession(playerId, reason); // Use the safe close method
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error closing session for " + playerId + ": " + e.getMessage(), e);
            }
        }
        // Should be empty now, but clear just in case of errors during closure
        if (!activeSessions.isEmpty()) {
            plugin.getLogger().warning("Session map not empty after closeAllSessions. Clearing forcefully.");
            activeSessions.clear();
        }
        plugin.getLogger().info("All active T-Edit sessions processed for closure.");
    }
}