package com.takeda;

import com.takeda.commands.EditCommand;
import com.takeda.commands.TEditAdminCommand; // Added
import com.takeda.config.SettingsManager;
import com.takeda.listeners.PlayerListener;
import com.takeda.sessions.EditSessionManager;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Main class for the T-Edit plugin. Handles initialization, core component access,
 * and plugin lifecycle management.
 */
public final class TEditPlugin extends JavaPlugin {

    private static TEditPlugin instance;
    private BukkitAudiences adventure;
    private SettingsManager settingsManager;
    private EditSessionManager sessionManager;
    private NamespacedKey enchantKeyPDC; // Key used to identify enchantments on book items

    @Override
    public void onEnable() {
        instance = this;
        this.enchantKeyPDC = new NamespacedKey(this, "tedit_enchant_key"); // Initialize the key

        // 1. Initialize Adventure (Essential for modern text components)
        try {
            this.adventure = BukkitAudiences.create(this);
        } catch (IllegalStateException e) { // Catch specific exception if Adventure is already initialized elsewhere (unlikely for own plugin)
            getLogger().log(Level.SEVERE, "Failed to initialize Adventure Platform! Is another plugin interfering? Disabling T-Edit.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        } catch (Exception e) { // Catch broader errors during init
            getLogger().log(Level.SEVERE, "An unexpected error occurred initializing Adventure. Disabling T-Edit.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 2. Load Settings Manager (Handles all config files)
        this.settingsManager = new SettingsManager(this);
        if (!this.settingsManager.load()) { // Load returns false on critical error
            getLogger().severe("Failed to load critical configurations! Check previous errors. Disabling T-Edit.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3. Initialize Session Manager (Tracks active GUIs)
        this.sessionManager = new EditSessionManager(this);

        // 4. Register Commands
        PluginCommand editCmd = getCommand("edit");
        PluginCommand adminCmd = getCommand("tedit"); // Get admin command

        if (editCmd != null) {
            editCmd.setExecutor(new EditCommand(this));
        } else {
            getLogger().severe("Command 'edit' not found! Ensure it is defined in plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (adminCmd != null) {
            adminCmd.setExecutor(new TEditAdminCommand(this)); // Register admin command executor
        } else {
            getLogger().warning("Admin command 'tedit' not found! Ensure it is defined in plugin.yml.");
            // Not necessarily fatal, maybe only '/edit' is desired.
        }


        // 5. Register Listeners (Handles player interactions)
        try {
            getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        } catch (Exception e) { // Catch potential errors during listener registration
            getLogger().log(Level.SEVERE, "Failed to register event listeners. Disabling T-Edit.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Success!
        getLogger().info("T-Edit v" + getDescription().getVersion() + " enabled successfully!");
        if (settingsManager.isDebugEnabled()) {
            getLogger().warning("<<< T-Edit Debug Mode is ENABLED >>>");
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling T-Edit v" + getDescription().getVersion() + "...");

        // 1. Clean up active sessions first to prevent data loss/issues
        if (this.sessionManager != null) {
            this.sessionManager.closeAllSessions("Plugin disabling");
        } else {
            getLogger().warning("Session Manager was null during disable sequence.");
        }

        // 2. Shutdown Adventure resources
        if (this.adventure != null) {
            this.adventure.close();
            this.adventure = null; // Allow garbage collection
        } else {
            getLogger().warning("Adventure Platform was null during disable sequence.");
        }

        getLogger().info("T-Edit disabled.");
        // Nullify references to help GC and prevent accidental use after disable
        this.settingsManager = null;
        this.sessionManager = null;
        this.enchantKeyPDC = null;
        instance = null;
    }

    // --- Static Accessors ---

    /** Gets the singleton instance of the TEditPlugin. */
    @NotNull
    public static TEditPlugin getInstance() {
        // Ensure instance is not null, crucial for other classes accessing core components
        return Objects.requireNonNull(instance, "TEditPlugin instance is not available. Plugin might be disabled or accessed too early.");
    }

    /** Gets the Adventure BukkitAudiences instance for platform-specific components. */
    @NotNull
    public BukkitAudiences adventure() {
        // Added null check during execution instead of relying solely on init
        if (this.adventure == null) {
            throw new IllegalStateException("Adventure Platform is not available. Plugin might be disabled or initializing.");
        }
        return this.adventure;
    }

    /** Gets the Settings Manager which handles configuration loading and access. */
    @NotNull
    public SettingsManager getSettingsManager() {
        // Added null check during execution
        if (this.settingsManager == null) {
            throw new IllegalStateException("SettingsManager is not available. Plugin might be disabled or initializing.");
        }
        return this.settingsManager;
    }

    /** Gets the Edit Session Manager which tracks active GUI editing sessions. */
    @NotNull
    public EditSessionManager getSessionManager() {
        // Added null check during execution
        if (this.sessionManager == null) {
            throw new IllegalStateException("EditSessionManager is not available. Plugin might be disabled or initializing.");
        }
        return this.sessionManager;
    }

    /** Gets the NamespacedKey used for storing enchantment identifiers on book items. */
    @NotNull
    public NamespacedKey getEnchantmentPDCKey() {
        // Added null check during execution
        if (this.enchantKeyPDC == null) {
            throw new IllegalStateException("Enchantment PDC Key is not available. Plugin might be disabled or initializing.");
        }
        return this.enchantKeyPDC;
    }
}