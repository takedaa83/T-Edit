package com.takeda.config;

import com.takeda.TEditPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag; 
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Manages loading, validation, and cached access for all T-Edit configuration files (config.yml, gui.yml, etc.).
 */
public class SettingsManager {

    private final TEditPlugin plugin;
    private final MiniMessage miniMessage;

    // File & Config Objects
    private File configFile, guiFile, messagesFile, soundsFile;
    private FileConfiguration config, guiConfig, messagesConfig, soundsConfig;

    // --- Cached Settings ---
    private boolean debugEnabled;
    private boolean allowBypassLevelCaps;
    private boolean allowBypassConflicts;
    private boolean allowTreasureEnchants;
    private boolean allowCurseEnchants;
    private Set<String> itemBlacklist; 
    private Component guiTitle;
    private int guiSize;
    private ItemStack placeholderItem;
    private List<Integer> enchantmentSlots;
    private Map<String, GuiElementConfig> guiElements;
    private String enchantBookNameFormat;
    private List<String> enchantBookLoreFormat;
    private List<String> confirmButtonLoreFormat;
    private final Map<String, String> messageFormats = new HashMap<>();
    private String messagePrefix = "";
    private boolean soundsEnabled;
    private final Map<String, Sound> soundMap = new HashMap<>();

    /** Holds parsed configuration data for a static GUI element. Ensures non-null where applicable. */
    public record GuiElementConfig(
            @NotNull String key,
            boolean enabled,
            int slot,
            @Nullable String permission,
            @NotNull Material material,
            @NotNull Component name,
            @NotNull List<Component> lore,
            int customModelData // -1 if not set
    ) {}


    public SettingsManager(@NotNull TEditPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin instance cannot be null");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** Loads all configuration files. Returns false if a critical error occurs. */
    public boolean load() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        guiFile = new File(plugin.getDataFolder(), "gui.yml");
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        soundsFile = new File(plugin.getDataFolder(), "sounds.yml");

        try {
            saveDefaultConfig("config.yml", configFile);
            saveDefaultConfig("gui.yml", guiFile);
            saveDefaultConfig("messages.yml", messagesFile);
            saveDefaultConfig("sounds.yml", soundsFile);

            config = loadYamlConfig(configFile);
            guiConfig = loadYamlConfig(guiFile);
            messagesConfig = loadYamlConfig(messagesFile);
            soundsConfig = loadYamlConfig(soundsFile);

            // Reloading: Clear old cached maps/lists before loading new ones
            if (guiElements != null) guiElements.clear();
            if (messageFormats != null) messageFormats.clear();
            if (soundMap != null) soundMap.clear();
            if (itemBlacklist != null) itemBlacklist.clear();

            loadCoreConfig();
            loadGuiConfig();
            loadMessagesConfig();
            loadSoundsConfig();

            plugin.getLogger().info("Configurations loaded successfully.");
            return true;

        } catch (IOException | InvalidConfigurationException e) {
            logConfigError("Failed to load or parse configuration files! T-Edit cannot function correctly.", e);
            return false;
        } catch (IllegalArgumentException e) { // Catch errors from internal parsing (bad material, slot etc)
            logConfigError("Configuration error: " + e.getMessage(), e);
            return false;
        } catch (Exception e) { // Catch any other unexpected errors during loading
            logConfigError("An unexpected error occurred while loading configurations.", e);
            return false;
        }
    }

    private void saveDefaultConfig(String resourcePath, File destination) throws IOException {
        if (!destination.exists()) {
            plugin.getLogger().info("Creating default configuration file: " + destination.getName());
            File parentDir = destination.getParentFile();
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                throw new IOException("Could not create parent directories for " + destination.getAbsolutePath());
            }
            plugin.saveResource(resourcePath, false); // false = don't replace if exists
        }
    }

    private FileConfiguration loadYamlConfig(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yamlConfig = new YamlConfiguration();
        yamlConfig.load(file);
        return yamlConfig;
    }

    // --- Loading Sections ---

    private void loadCoreConfig() {
        debugEnabled = config.getBoolean("debug.enabled", false);
        allowBypassLevelCaps = config.getBoolean("enchantments.allow_bypass_level_caps", false);
        allowBypassConflicts = config.getBoolean("enchantments.allow_bypass_conflicts", false);
        allowTreasureEnchants = config.getBoolean("enchantments.allow_treasure", true);
        allowCurseEnchants = config.getBoolean("enchantments.allow_curses", true);

        // Load item blacklist
        List<String> rawBlacklist = config.getStringList("item-blacklist");
        itemBlacklist = new HashSet<>();
        if (rawBlacklist != null) {
            for (String entry : rawBlacklist) {
                if (entry != null && !entry.isBlank()) {
                    // TODO: Add support for Material Tags (#minecraft:planks) here later if needed
                    Material mat = Material.matchMaterial(entry.toUpperCase());
                    if (mat != null) {
                        itemBlacklist.add(mat.getKey().toString()); // Store as namespaced key string for consistency
                        if (debugEnabled) plugin.getLogger().info("[Debug] Added to blacklist: " + mat.getKey().toString());
                    } else {
                        plugin.getLogger().warning("Invalid material '" + entry + "' found in config.yml item-blacklist.");
                    }
                }
            }
        }

        if (debugEnabled) plugin.getLogger().info("[Debug] Core settings loaded. Blacklist size: " + itemBlacklist.size());
    }

    private void loadGuiConfig() {
        guiTitle = parseComponent(guiConfig.getString("title", "<gold>T-Edit"));
        guiSize = guiConfig.getInt("size", 54);
        if (guiSize <= 0 || guiSize % 9 != 0 || guiSize > 54) { // Max size 54 (6 rows)
            plugin.getLogger().warning("Invalid GUI size '" + guiSize + "' in gui.yml. Must be multiple of 9, max 54. Defaulting to 54.");
            guiSize = 54;
        }

        placeholderItem = parseItemStackFromSection(guiConfig.getConfigurationSection("placeholder_item"), Material.BLACK_STAINED_GLASS_PANE, " ");

        enchantmentSlots = guiConfig.getIntegerList("enchantment_display.slots");
        if (enchantmentSlots.isEmpty()) {
            plugin.getLogger().warning("No 'enchantment_display.slots' defined. Enchantments cannot be displayed.");
        } else {
            enchantmentSlots = enchantmentSlots.stream()
                    .filter(slot -> validateSlot(slot, "enchantment_display.slots", false)) // Filter invalid slots
                    .distinct() // Remove duplicates
                    .sorted()   // Sort for predictable order
                    .collect(Collectors.toList());
            if (debugEnabled) plugin.getLogger().info("[Debug] Loaded " + enchantmentSlots.size() + " valid enchantment slots.");
        }

        enchantBookNameFormat = Objects.requireNonNullElse(guiConfig.getString("enchantment_display.book_item.name"), "<light_purple>{enchant_name} <gray>Lvl: {level}");
        enchantBookLoreFormat = Objects.requireNonNullElse(guiConfig.getStringList("enchantment_display.book_item.lore"), Collections.emptyList());

        // Load dynamic lore format for confirm button, ensure it exists even if empty
        confirmButtonLoreFormat = Objects.requireNonNullElse(guiConfig.getStringList("elements.confirm.item.lore"), List.of("<status>")); // Provide a default if missing

        guiElements = new HashMap<>();
        ConfigurationSection elementsSection = guiConfig.getConfigurationSection("elements");
        if (elementsSection != null) {
            final Map<Integer, String> usedSlots = new HashMap<>(); // For conflict checking
            // Ensure enchantment slots don't conflict with static elements
            enchantmentSlots.forEach(slot -> usedSlots.put(slot, "ENCHANTMENT_DISPLAY"));

            for (String key : elementsSection.getKeys(false)) {
                if (!elementsSection.isConfigurationSection(key)) continue;
                ConfigurationSection elementSection = elementsSection.getConfigurationSection(key);
                if (elementSection == null) continue;

                try {
                    GuiElementConfig elementConfig = parseGuiElement(key, elementSection);
                    if (elementConfig.enabled()) {
                        int slot = elementConfig.slot();
                        // Check if slot is valid and not already used
                        if (validateSlot(slot, "element '" + key + "'", true)) {
                            String existingKey = usedSlots.put(slot, key); // Put returns previous value if key exists
                            if (existingKey != null) {
                                plugin.getLogger().warning("Duplicate slot " + slot + " definition in gui.yml! Element '" + key + "' conflicts with '" + existingKey + "'. '" + key + "' will be used.");
                                // Decide on override behavior - currently allows override
                            }
                            guiElements.put(key, elementConfig);
                            if (debugEnabled) plugin.getLogger().info("[Debug] Loaded GUI Element '" + key + "' in slot " + slot);
                        }
                    } else if (debugEnabled) {
                        plugin.getLogger().info("[Debug] GUI Element '" + key + "' is disabled.");
                    }
                } catch (IllegalArgumentException e) { // Catch errors from parseGuiElement or validateSlot
                    plugin.getLogger().warning("Skipping GUI element '" + key + "' due to config error: " + e.getMessage());
                }
            }
        } else {
            plugin.getLogger().severe("CRITICAL: Missing 'elements' section in gui.yml. No GUI buttons or elements loaded!");
        }

        // Validate required elements exist after loading
        validateRequiredElement("preview_item");
        // Remove validation for elements we don't need in our implementation
        // validateRequiredElement("confirm");
        // validateRequiredElement("cancel");
        // Pagination elements are optional, handled by checks later
        // validateRequiredElement("page_next");
        // validateRequiredElement("page_prev");
        // validateRequiredElement("page_info");

        if (debugEnabled) plugin.getLogger().info("[Debug] GUI settings loaded.");
    }

    private void validateRequiredElement(String key) {
        if (!guiElements.containsKey(key)) {
            plugin.getLogger().severe("CRITICAL: Required GUI element '" + key + "' is missing or invalid in gui.yml!");
            // Consider disabling the plugin here if these are truly critical
        }
    }

    @NotNull
    private GuiElementConfig parseGuiElement(String key, @NotNull ConfigurationSection section) throws IllegalArgumentException {
        boolean enabled = section.getBoolean("enabled", true);
        // Don't validate slot yet, do it in loadGuiConfig after parsing
        int slot = section.getInt("slot", -1);

        String permission = section.getString("permission"); // Nullable allowed

        ConfigurationSection itemSection = section.getConfigurationSection("item");
        Material material = Material.AIR; // Default
        Component name = Component.empty();
        List<Component> lore = Collections.emptyList();
        int customModelData = -1;

        // Special handling for preview_item which only needs a slot
        if (key.equals("preview_item")) {
            if (slot == -1) throw new IllegalArgumentException("Missing 'slot' for element 'preview_item'");
            // Material etc. are irrelevant, keep defaults
        } else if (itemSection != null) { // Standard elements require an 'item' section
            if (slot == -1) throw new IllegalArgumentException("Missing 'slot' for element '" + key + "'");

            String materialName = itemSection.getString("material");
            if (materialName == null || materialName.isBlank()) throw new IllegalArgumentException("Missing material for element '" + key + "'");
            material = Material.matchMaterial(materialName.toUpperCase()); // Use uppercase for consistency
            if (material == null) throw new IllegalArgumentException("Invalid material '" + materialName + "' for element '" + key + "'");

            name = parseComponent(itemSection.getString("name", "<red>" + key)); // Default to key if name missing
            lore = parseComponentList(itemSection.getStringList("lore"));
            customModelData = itemSection.getInt("custom_model_data", -1); // Optional CMD
        } else {
            // Allow elements without an 'item' section if they are disabled
            if (enabled) {
                throw new IllegalArgumentException("Missing 'item' section for enabled element '" + key + "'");
            }
            // If disabled, allow missing item section, but set defaults
            if (slot == -1) slot = 0; // Assign dummy slot if disabled and missing
            material = Material.BARRIER;
            name = parseComponent("<gray>DISABLED: " + key);
        }

        return new GuiElementConfig(key, enabled, slot, permission, material, name, lore, customModelData);
    }

    @NotNull
    private ItemStack parseItemStackFromSection(@Nullable ConfigurationSection section, @NotNull Material defaultMat, @NotNull String defaultName) {
        Material material = defaultMat;
        String nameStr = defaultName;
        List<String> loreStr = Collections.emptyList();
        int cmd = -1;

        if (section != null) {
            String configMatName = section.getString("material");
            if (configMatName != null && !configMatName.isBlank()) {
                Material configMat = Material.matchMaterial(configMatName.toUpperCase());
                if (configMat != null) material = configMat;
                else plugin.getLogger().warning("Invalid placeholder material '" + configMatName + "'. Using default: " + defaultMat.name());
            }
            nameStr = section.getString("name", defaultName);
            loreStr = section.getStringList("lore");
            cmd = section.getInt("custom_model_data", -1);
        }

        ItemStack item = new ItemStack(material);
        try {
            ItemMeta meta = item.getItemMeta(); // Can be null for AIR
            if (meta != null) {
                meta.displayName(parseComponent(nameStr));
                meta.lore(parseComponentList(loreStr));
                if (cmd != -1) meta.setCustomModelData(cmd);
                // Apply default hide flags to clean up placeholder appearance
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ITEM_SPECIFICS, ItemFlag.HIDE_DYE, ItemFlag.HIDE_ARMOR_TRIM); // Add more flags
                item.setItemMeta(meta);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error applying meta to placeholder item '" + material.name() + "': " + e.getMessage());
        }
        return item;
    }

    private void loadMessagesConfig() {
        messageFormats.clear(); // Ensure clean slate on reload
        messagePrefix = messagesConfig.getString("prefix", "");
        ConfigurationSection messagesSection = messagesConfig.getConfigurationSection(""); // Get root section
        if (messagesSection != null) {
            messagesSection.getKeys(false).stream()
                    .filter(key -> !key.equals("prefix") && messagesConfig.isString(key))
                    .forEach(key -> messageFormats.put(key, messagesConfig.getString(key)));
        }

        // Add default fallbacks for critical messages used in code to prevent NPEs if missing from file
        messageFormats.putIfAbsent("error_no_permission", "<red>You don't have permission for: <gray><permission>");
        messageFormats.putIfAbsent("error_player_only", "<red>This command can only be run by a player.");
        messageFormats.putIfAbsent("info_changes_discarded", "<yellow>Changes discarded.");
        messageFormats.putIfAbsent("error_item_blacklisted", "<red>Editing of <item> is not allowed.");
        messageFormats.putIfAbsent("error_item_type_not_enchantable", "<red>This item type cannot be enchanted.");
        messageFormats.putIfAbsent("info_reload_success", "<green>T-Edit configuration reloaded successfully.");
        messageFormats.putIfAbsent("info_reload_fail", "<red>T-Edit configuration reload failed. Check console.");
        messageFormats.putIfAbsent("gui_page_info", "<gray>Page <gold><page></gold>/<total_pages>");
        // Add others as needed...
        if (debugEnabled) plugin.getLogger().info("[Debug] Message formats loaded (" + messageFormats.size() + " entries).");
    }

    private void loadSoundsConfig() {
        soundMap.clear(); // Ensure clean slate on reload
        soundsEnabled = soundsConfig.getBoolean("enabled", true);
        if (!soundsEnabled && debugEnabled) {
            plugin.getLogger().info("[Debug] Sounds are disabled globally.");
            return;
        }

        ConfigurationSection soundsSection = soundsConfig.getConfigurationSection("sounds");
        if (soundsSection != null) {
            soundsSection.getKeys(false).forEach(key -> {
                String soundName = soundsSection.getString(key);
                if (soundName != null && !soundName.isBlank()) {
                    try {
                        // Allow period or underscore, convert to uppercase underscore standard
                        Sound sound = Sound.valueOf(soundName.toUpperCase().replace('.', '_'));
                        soundMap.put(key, sound);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid sound name '" + soundName + "' for action '" + key + "' in sounds.yml.");
                    }
                }
            });
        }
        // Add default sounds for pagination if missing
        soundMap.putIfAbsent("page_change", Sound.ITEM_BOOK_PAGE_TURN);

        if (debugEnabled) plugin.getLogger().info("[Debug] Sound mappings loaded (" + soundMap.size() + " entries).");
    }

    // --- Validation & Helpers ---
    /** Returns true if valid, false otherwise. Logs warning if logError=true */
    private boolean validateSlot(int slot, String context, boolean logError) {
        if (slot < 0 || slot >= guiSize) {
            if(logError) plugin.getLogger().warning("Invalid slot number " + slot + " for " + context + ". Must be between 0 and " + (guiSize - 1) + ".");
            return false;
        }
        return true;
    }

    private void logConfigError(String message, @Nullable Throwable throwable) {
        plugin.getLogger().log(Level.SEVERE, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        plugin.getLogger().log(Level.SEVERE, " T-EDIT CONFIGURATION ERROR:");
        plugin.getLogger().log(Level.SEVERE, " " + message);
        if (throwable != null) {
            // Simplified error message if not debug
            String details = (debugEnabled || throwable instanceof InvalidConfigurationException || throwable instanceof IllegalArgumentException)
                    ? throwable.getMessage() : throwable.getClass().getSimpleName();
            plugin.getLogger().log(Level.SEVERE, " Details: " + details);
            // Only log stack trace if debug is enabled to avoid console spam, unless it's a critical parse error
            if (debugEnabled || throwable instanceof InvalidConfigurationException) {
                plugin.getLogger().log(Level.SEVERE, " Stacktrace:", throwable);
            }
        }
        plugin.getLogger().log(Level.SEVERE, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
    }

    // --- Getters ---
    public boolean isDebugEnabled() { return debugEnabled; }
    public boolean isAllowBypassLevelCaps() { return allowBypassLevelCaps; }
    public boolean isAllowBypassConflicts() { return allowBypassConflicts; }
    public boolean isAllowTreasureEnchants() { return allowTreasureEnchants; }
    public boolean isAllowCurseEnchants() { return allowCurseEnchants; }
    public boolean isItemBlacklisted(@NotNull Material material) {
        return itemBlacklist != null && itemBlacklist.contains(material.getKey().toString());
    }
    @NotNull public Component getGuiTitleComponent() { return Objects.requireNonNullElseGet(guiTitle, () -> parseComponent("<red>ERR")); }
    public int getGuiSize() { return guiSize; }
    @NotNull public ItemStack getPlaceholderItem() { return placeholderItem != null ? placeholderItem.clone() : new ItemStack(Material.AIR); }
    @NotNull public List<Integer> getEnchantmentSlots() { return enchantmentSlots != null ? Collections.unmodifiableList(enchantmentSlots) : Collections.emptyList(); }
    @NotNull public Optional<GuiElementConfig> getGuiElement(@NotNull String key) { return Optional.ofNullable(guiElements.get(key)); }
    @NotNull public Map<String, GuiElementConfig> getAllGuiElements() { return guiElements != null ? Collections.unmodifiableMap(guiElements) : Collections.emptyMap(); }
    @NotNull public Optional<GuiElementConfig> getGuiElementBySlot(int slot) {
        if (guiElements == null) return Optional.empty();
        // Optimize: If maps were keyed by slot instead of string key, this would be faster.
        // But for a small number of elements, stream().filter() is acceptable.
        return guiElements.values().stream().filter(e -> e.slot() == slot).findFirst();
    }
    @NotNull public String getEnchantBookNameFormat() { return Objects.requireNonNullElse(enchantBookNameFormat, ""); }
    @NotNull public List<String> getEnchantBookLoreFormat() { return enchantBookLoreFormat != null ? Collections.unmodifiableList(enchantBookLoreFormat) : Collections.emptyList(); }
    @NotNull public List<String> getConfirmButtonLoreFormat() { return confirmButtonLoreFormat != null ? Collections.unmodifiableList(confirmButtonLoreFormat) : Collections.emptyList(); }
    @NotNull public MiniMessage miniMessage() { return miniMessage; }

    // --- Message & Sound ---
    @NotNull private String getRawMessageFormat(@NotNull String key) {
        // Retrieve format, provide fallback INCLUDING prefix if key is missing
        return messageFormats.getOrDefault(key, messagePrefix + "<red>Missing message: " + key);
    }

    /**
     * Gets the raw message format without the prefix - used for enchantment book lore
     */
    @NotNull public String getRawMessageWithoutPrefix(@NotNull String key) {
        // Return just the raw message without prefix
        return messageFormats.getOrDefault(key, "<red>Missing message: " + key);
    }

    @NotNull public Component getMessageComponent(@NotNull String key, @NotNull TagResolver... resolvers) {
        // Apply prefix only if the key exists OR if the fallback didn't already include it (which it does now)
        String rawFormat = messageFormats.get(key);
        boolean usePrefix = rawFormat != null && !messagePrefix.isEmpty(); // Only add prefix if key exists and prefix is set
        rawFormat = rawFormat != null ? rawFormat : "<red>Missing message: " + key; // Get raw or fallback (without prefix)

        return miniMessage.deserialize((usePrefix ? messagePrefix : "") + rawFormat, resolvers);
    }
    public void sendMessage(@NotNull Player player, @NotNull String key, @NotNull TagResolver... resolvers) {
        plugin.adventure().player(player).sendMessage(getMessageComponent(key, resolvers));
    }
    public void sendActionBar(@NotNull Player player, @NotNull String key, @NotNull TagResolver... resolvers) {
        plugin.adventure().player(player).sendActionBar(getMessageComponent(key, resolvers));
    }
    public void playSound(@NotNull Player player, @NotNull String actionKey) {
        if (!soundsEnabled) return;
        Sound sound = soundMap.get(actionKey);
        if (sound != null) {
            try {
                // Play sound at player's location for better spatial awareness if desired, or eye location
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            }
            catch (Exception e) {
                if (debugEnabled) plugin.getLogger().log(Level.WARNING, "Error playing sound '" + actionKey + "' (" + sound + ") for " + player.getName(), e);
            }
        } else if (debugEnabled) {
            plugin.getLogger().warning("Attempted to play undefined sound key: '" + actionKey + "'");
        }
    }

    // --- Component Parsing ---
    @NotNull private Component parseComponent(@Nullable String miniMessageString) {
        if (miniMessageString == null || miniMessageString.isBlank()) return Component.empty();
        try {
            // Optionally add legacy color code support (&)
            // miniMessageString = ChatColor.translateAlternateColorCodes('&', miniMessageString);
            return miniMessage.deserialize(miniMessageString);
        }
        catch (Exception e) {
            plugin.getLogger().warning("MiniMessage parse failed for string: '" + miniMessageString + "'. Error: " + e.getMessage());
            return Component.text("<PARSE ERROR>", net.kyori.adventure.text.format.NamedTextColor.RED);
        }
    }
    @NotNull private List<Component> parseComponentList(@Nullable List<String> list) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        return list.stream()
                .map(this::parseComponent)
                .collect(Collectors.toList()); // Collect into a mutable list first
    }
}