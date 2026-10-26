package github.nighter.smartspawner.spawner.gui.storage;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.utils.ItemTooltipUtil;
import github.nighter.smartspawner.spawner.config.SpawnerMobHeadTexture;
import github.nighter.smartspawner.spawner.gui.layout.GuiButton;
import github.nighter.smartspawner.spawner.gui.layout.GuiButtonRenderer;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayoutConfig;
import github.nighter.smartspawner.spawner.gui.layout.GuiText;
import github.nighter.smartspawner.hooks.economy.ItemPriceManager;
import github.nighter.smartspawner.spawner.lootgen.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import github.nighter.smartspawner.spawner.properties.ItemSignature;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.Scheduler.Task;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class SpawnerStorageUI {
    private static final String STORAGE_TITLE = "gui_title_storage";
    private static final String SINGLE_STORAGE_TITLE = "gui_title_storage_single_spawner";

    private static final int INVENTORY_SIZE = 54;

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    @Getter
    private GuiLayoutConfig layoutConfig;

    // Precomputed buttons to avoid repeated creation
    private final Map<String, ItemStack> staticButtons;

    // Lightweight caches with better eviction strategies
    private final Map<String, ItemStack> navigationButtonCache;
    private final Map<String, ItemStack> pageIndicatorCache;
    private final Map<String, ItemStack> sortButtonCache;

    // Cache expiry time reduced for more responsive updates
    private static final int MAX_CACHE_SIZE = 100;
    // Sort button key cardinality is (spawner types x their loot items), ~200+ on a full vanilla
    // server, so it gets a larger cap than the page-based caches to avoid thrashing. Each entry is
    // a few KB, so even at this cap the retained memory stays around 1 MB.
    private static final int MAX_SORT_CACHE_SIZE = 300;
    
    // Cache for title format to avoid repeated language lookups
    private String cachedStorageTitleFormat = null;
    private String cachedSingleStorageTitleFormat = null;

    // Cleanup task to remove stale entries from caches
    private Task cleanupTask;

    public SpawnerStorageUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.layoutConfig = plugin.getGuiLayoutConfig();

        // Initialize caches with appropriate initial capacity
        this.staticButtons = new ConcurrentHashMap<>(16);
        this.navigationButtonCache = new ConcurrentHashMap<>(16);
        this.pageIndicatorCache = new ConcurrentHashMap<>(16);
        this.sortButtonCache = new ConcurrentHashMap<>(16);

        initializeStaticButtons();
        startCleanupTask();
    }

    public void reload() {
        // Reload layout configuration
        this.layoutConfig = plugin.getGuiLayoutConfig();

        // Clear caches to force reloading of buttons
        navigationButtonCache.clear();
        pageIndicatorCache.clear();
        sortButtonCache.clear();
        staticButtons.clear();
        cachedStorageTitleFormat = null;
        cachedSingleStorageTitleFormat = null;

        // Reinitialize static buttons
        initializeStaticButtons();
    }

    private void initializeStaticButtons() {
        GuiLayout layout = layoutConfig.getCurrentStorageLayout();

        // OPTIMIZATION: Iterate through all buttons and create static buttons by action
        for (GuiButton button : layout.getAllButtons().values()) {
            String action = getAnyActionFromButton(button);
            if (action == null) continue;

            // Viewer-dependent buttons are rebuilt per player on each repaint, so pre-caching
            // them with no player would only bake wrong PlaceholderAPI values.
            if (isViewerDependent(button)) continue;

            switch (action) {
                case "return_main":
                    getStaticButton("return", button, null, meta -> {
                        meta.setDisplayName(languageManager.getGuiItemName("return_button.name"));
                        meta.setLore(languageManager.getGuiItemLoreAsList("return_button.lore"));
                    });
                    break;
                case "take_all":
                    getStaticButton("takeAll", button, null, meta -> {
                        meta.setDisplayName(languageManager.getGuiItemName("take_all_button.name"));
                        meta.setLore(languageManager.getGuiItemLoreAsList("take_all_button.lore"));
                    });
                    break;
                case "sort_items":
                    // Sort button is created dynamically (material stored for later)
                    break;
                case "drop_page":
                    getStaticButton("dropPage", button, null, meta -> {
                        meta.setDisplayName(languageManager.getGuiItemName("drop_page_button.name"));
                        meta.setLore(languageManager.getGuiItemLoreAsList("drop_page_button.lore"));
                    });
                    break;
                case "open_filter":
                    getStaticButton("itemFilter", button, null, meta -> {
                        meta.setDisplayName(languageManager.getGuiItemName("item_filter_button.name"));
                        meta.setLore(languageManager.getGuiItemLoreAsList("item_filter_button.lore"));
                    });
                    break;
                // Note: Sell buttons are created dynamically to show current total sell price
            }
        }
    }

    /**
     * Get any action from button - checks click, left_click, right_click
     * OPTIMIZATION: Return first found action
     */
    private String getAnyActionFromButton(GuiButton button) {
        // Check click type actions in priority order
        Map<String, String> actions = button.getActions();
        if (actions == null || actions.isEmpty()) {
            return null;
        }

        // Check in priority: click -> left_click -> right_click
        String action = actions.get("click");
        if (action != null && !action.isEmpty()) {
            return action;
        }

        action = actions.get("left_click");
        if (action != null && !action.isEmpty()) {
            return action;
        }

        action = actions.get("right_click");
        if (action != null && !action.isEmpty()) {
            return action;
        }

        return null;
    }

    /**
     * Gets the formatted title for storage GUI with page information.
     * Uses cached title format pattern for performance.
     * Optimized to only compute entity placeholders if they're used in the title.
     *
     * @param spawner The spawner data
     * @param page Current page number
     * @param totalPages Total number of pages
     * @return Formatted title with page information
     */
    public String getStorageTitle(SpawnerData spawner, int page, int totalPages) {
        return getStorageTitle(null, spawner, page, totalPages);
    }

    public String getStorageTitle(Player player, SpawnerData spawner, int page, int totalPages) {
        boolean singleSpawner = spawner.getStackSize() == 1;

        String titleKey = STORAGE_TITLE;
        String titleFormat;
        if (singleSpawner && languageManager.gui().contains(SINGLE_STORAGE_TITLE)) {
            titleKey = SINGLE_STORAGE_TITLE;
            if (cachedSingleStorageTitleFormat == null) {
                cachedSingleStorageTitleFormat = languageManager.getGuiTitle(titleKey);
            }
            titleFormat = cachedSingleStorageTitleFormat;
        } else {
            if (cachedStorageTitleFormat == null) {
                cachedStorageTitleFormat = languageManager.getGuiTitle(titleKey);
            }
            titleFormat = cachedStorageTitleFormat;
        }

        String customTitle = singleSpawner && layoutConfig.getStorageSingleTitle() != null
                ? layoutConfig.getStorageSingleTitle()
                : layoutConfig.getStorageTitle();
        String template = customTitle != null ? customTitle : titleFormat;
        
        // Build base placeholders (always present)
        Map<String, String> placeholders = new HashMap<>(4);
        placeholders.put("current_page", String.valueOf(page));
        placeholders.put("total_pages", String.valueOf(totalPages));

        // OPTIMIZATION: Only compute entity placeholders if they exist in the title format
        if (template.contains("{entity}") || template.contains("{ᴇɴᴛɪᴛʏ}")) {
            String entityName = spawner.getDisplayName();

            if (template.contains("{entity}")) {
                placeholders.put("entity", entityName);
            }
            if (template.contains("{ᴇɴᴛɪᴛʏ}")) {
                placeholders.put("ᴇɴᴛɪᴛʏ", languageManager.getSmallCaps(entityName));
            }
        }

        // OPTIMIZATION: Only compute amount if it exists in the title format
        if (template.contains("{amount}")) {
            placeholders.put("amount", String.valueOf(spawner.getStackSize()));
        }

        if (customTitle != null) {
            return GuiText.render(player, customTitle, placeholders);
        }
        return languageManager.getGuiTitle(titleKey, placeholders);
    }

    public Inventory createStorageInventory(Player player, SpawnerData spawner, int page, int totalPages) {
        // Get total pages efficiently
        if (totalPages == -1) {
            totalPages = calculateTotalPages(spawner);
        }

        // Clamp page number to valid range
        page = Math.max(1, Math.min(page, totalPages));

        GuiLayout layout = layoutConfig.getStorageLayout(spawner, player);

        // Create inventory with title including page info using placeholder-based format
        Inventory pageInv = Bukkit.createInventory(
                new StoragePageHolder(spawner, page, totalPages, layout),
                INVENTORY_SIZE,
                getStorageTitle(player, spawner, page, totalPages)
        );

        // Populate the inventory
        updateDisplay(pageInv, spawner, page, totalPages, player);
        return pageInv;
    }

    public void updateDisplay(Inventory inventory, SpawnerData spawner, int page, int totalPages) {
        updateDisplay(inventory, spawner, page, totalPages, resolveViewer(inventory));
    }

    public void updateDisplay(Inventory inventory, SpawnerData spawner, int page, int totalPages,
                              Player viewer) {
        if (!spawner.getInventoryLock().tryLock()) {
            return;
        }

        if (totalPages == -1) {
            totalPages = calculateTotalPages(spawner);
        }

        // Track both changes and slots that need to be emptied
        Map<Integer, ItemStack> updates = new HashMap<>();
        Set<Integer> slotsToEmpty = new HashSet<>();

        // Clear storage area slots first
        for (int i = 0; i < StoragePageHolder.MAX_ITEMS_PER_PAGE; i++) {
            slotsToEmpty.add(i);
        }

        // Also mark all button slots for potential clearing (fixes visual bug where buttons remain when they shouldn't)
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
        assert holder != null;
        GuiLayout layout = holder.getLayout();
        if (layout != null) {
            slotsToEmpty.addAll(layout.getUsedSlots());
        }

        // Add items from virtual inventory
        addPageItems(updates, slotsToEmpty, spawner, page);

        // Add navigation buttons based on layout
        addNavigationButtons(updates, spawner, page, totalPages, layout, viewer);

        // Apply all updates in a batch
        for (int slot : slotsToEmpty) {
            if (!updates.containsKey(slot)) {
                inventory.setItem(slot, null);
            }
        }

        for (Map.Entry<Integer, ItemStack> entry : updates.entrySet()) {
            inventory.setItem(entry.getKey(), entry.getValue());
        }

        // Update hologram if enabled
        if (plugin.getConfig().getBoolean("hologram.enabled", false)) {
            Scheduler.runLocationTask(spawner.getSpawnerLocation(), spawner::updateHologramData);
        }

        // Check if we need to update total pages
        int oldUsedSlots = holder.getOldUsedSlots();
        int currentUsedSlots = spawner.getVirtualInventory().getUsedSlots();

        // Only recalculate total pages if there's a significant change
        if (oldUsedSlots != currentUsedSlots) {
            int newTotalPages = calculateTotalPages(spawner);
            holder.setTotalPages(newTotalPages);
            holder.updateOldUsedSlots();
        }
    }

    private void addPageItems(Map<Integer, ItemStack> updates, Set<Integer> slotsToEmpty, SpawnerData spawner, int page) {
        try {
            // Read only the requested page instead of materializing the full logical inventory.
            VirtualInventory virtualInv = spawner.getVirtualInventory();
            Int2ObjectMap<ItemStack> displayItems = virtualInv.getDisplayPage(page, StoragePageHolder.MAX_ITEMS_PER_PAGE);

            if (displayItems.isEmpty()) {
                return;
            }

            for (Int2ObjectMap.Entry<ItemStack> entry : displayItems.int2ObjectEntrySet()) {
                int displaySlot = entry.getIntKey();
                updates.put(displaySlot, entry.getValue());
                slotsToEmpty.remove(displaySlot);
            }
        } finally {
            spawner.getInventoryLock().unlock();
        }
    }

    private void addNavigationButtons(Map<Integer, ItemStack> updates, SpawnerData spawner, int page,
                                      int totalPages, GuiLayout layout, Player viewer) {
        if (totalPages == -1) {
            totalPages = calculateTotalPages(spawner);
        }

        // OPTIMIZATION: Iterate through all buttons and add items based on action
        for (GuiButton button : layout.getAllButtons().values()) {
            if (!button.isEnabled()) {
                continue;
            }

            // Check condition if present
            if (button.hasCondition() && !evaluateButtonCondition(button)) {
                continue;
            }

            // Handle info_button (spawner mob head with loot info)
            if (button.isInfoButton()) {
                updates.put(button.getSlot(), createStorageSpawnerInfoButton(spawner, button, viewer));
                continue;
            }

            String action = getAnyActionFromButton(button);
            if (action == null) continue;

            ItemStack item = null;

            switch (action) {
                case "previous_page":
                    if (page > 1) {
                        item = createCachedNavigationButton("previous", page - 1, button, viewer);
                    }
                    break;
                case "next_page":
                    if (page < totalPages) {
                        item = createCachedNavigationButton("next", page + 1, button, viewer);
                    }
                    break;
                case "take_all":
                    item = getStaticButton("takeAll", button, viewer, meta -> {
                        meta.setDisplayName(languageManager.getGuiItemName("take_all_button.name"));
                        meta.setLore(languageManager.getGuiItemLoreAsList("take_all_button.lore"));
                    });
                    break;
                case "sort_items":
                    item = createSortButton(spawner, button, viewer);
                    break;
                case "drop_page":
                    item = getStaticButton("dropPage", button, viewer, meta -> {
                        meta.setDisplayName(languageManager.getGuiItemName("drop_page_button.name"));
                        meta.setLore(languageManager.getGuiItemLoreAsList("drop_page_button.lore"));
                    });
                    break;
                case "open_filter":
                    item = getStaticButton("itemFilter", button, viewer, meta -> {
                        meta.setDisplayName(languageManager.getGuiItemName("item_filter_button.name"));
                        meta.setLore(languageManager.getGuiItemLoreAsList("item_filter_button.lore"));
                    });
                    break;
                case "return_main":
                    item = getStaticButton("return", button, viewer, meta -> {
                        meta.setDisplayName(languageManager.getGuiItemName("return_button.name"));
                        meta.setLore(languageManager.getGuiItemLoreAsList("return_button.lore"));
                    });
                    break;
                case "sell_all":
                    item = createSellButton(spawner, button, viewer);
                    break;
                case "sell_and_exp":
                    item = createSellAndExpButton(spawner, button, viewer);
                    break;
                case "collect_exp":
                    item = createCollectExpButton(spawner, button, viewer);
                    break;
            }

            if (item != null) {
                updates.put(button.getSlot(), item);
            }
        }
    }

    /**
     * Evaluate button condition based on server state
     * OPTIMIZATION: Centralized condition evaluation for storage GUI
     */
    private boolean evaluateButtonCondition(GuiButton button) {
        String condition = button.getCondition();
        if (condition == null || condition.isEmpty()) {
            return true;
        }

        switch (condition) {
            case "shop_integration":
                return plugin.hasSellIntegration();
            case "no_shop_integration":
                return !plugin.hasSellIntegration();
            default:
                plugin.getLogger().warning("Unknown button condition: " + condition);
                return true;
        }
    }

    private int calculateTotalPages(SpawnerData spawner) {
        int usedSlots = spawner.getVirtualInventory().getUsedSlots();
        return Math.max(1, (int) Math.ceil((double) usedSlots / StoragePageHolder.MAX_ITEMS_PER_PAGE));
    }

    private ItemStack createButtonWithCustomTexture(GuiButton button, Player player,
                                                    Map<String, String> placeholders,
                                                    Consumer<ItemMeta> metaModifier) {
        Consumer<ItemMeta> combined = meta -> {
            metaModifier.accept(meta);
            GuiButtonRenderer.applyOverrides(meta, button, player, placeholders);
        };

        ItemStack item;
        if (button.getMaterial() == Material.PLAYER_HEAD && button.getCustomTexture() != null && !button.getCustomTexture().trim().isEmpty()) {
            item = SpawnerMobHeadTexture.getCustomHeadFromTexture(button.getCustomTexture(), combined);
        } else {
            item = GuiButtonRenderer.createBaseItem(button);
            item.editMeta(combined);
        }
        GuiButtonRenderer.finish(item, button);

        // Hide tooltip for BUNDLE material (prevents showing bundle contents)
        if (item.getType() == Material.BUNDLE) {
            ItemTooltipUtil.hideBundleTooltip(item);
        }

        return item;
    }

    private ItemStack getStaticButton(String action, GuiButton button, Player player,
                                      Consumer<ItemMeta> metaModifier) {
        if (isViewerDependent(button)) {
            return createButtonWithCustomTexture(button, player, Map.of(), metaModifier);
        }

        String cacheKey = action + "|" + button.getMaterial() + "|" + button.getCustomTexture();
        return staticButtons.computeIfAbsent(
                cacheKey, ignored -> createButtonWithCustomTexture(button, null, Map.of(), metaModifier)).clone();
    }

    private ItemStack createNavigationButton(String type, int targetPage, GuiButton button, Player player) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("target_page", String.valueOf(targetPage));

        String buttonKey = type.equals("previous") ? "navigation_button_previous" : "navigation_button_next";
        String buttonName = languageManager.getGuiItemName(buttonKey + ".name", placeholders);
        String[] buttonLore = languageManager.getGuiItemLore(buttonKey + ".lore", placeholders);

        return createButtonWithCustomTexture(button, player, placeholders, meta -> {
            meta.setDisplayName(buttonName);
            if (buttonLore.length > 0) {
                meta.setLore(Arrays.asList(buttonLore));
            }
        });
    }

    private ItemStack createCachedNavigationButton(String type, int targetPage, GuiButton button,
                                                   Player player) {
        if (isViewerDependent(button)) {
            return createNavigationButton(type, targetPage, button, player);
        }

        String cacheKey = type + "-" + targetPage + "-" + button.getMaterial()
                + "|" + button.getCustomTexture();
        return navigationButtonCache.computeIfAbsent(
                cacheKey, ignored -> createNavigationButton(type, targetPage, button, null)).clone();
    }

    private ItemStack createSellButton(SpawnerData spawner, GuiButton button, Player player) {
        Map<String, String> placeholders = new HashMap<>();
        refreshSellValue(spawner, player);
        placeholders.put("total_sell_price", languageManager.formatNumber(spawner.getAccumulatedSellValue()));
        
        return createButtonWithCustomTexture(button, player, placeholders, meta -> {
            meta.setDisplayName(languageManager.getGuiItemName("sell_button.name", placeholders));
            meta.setLore(languageManager.getGuiItemLoreAsList("sell_button.lore", placeholders));
        });
    }

    private ItemStack createSellAndExpButton(SpawnerData spawner, GuiButton button, Player player) {
        Map<String, String> placeholders = new HashMap<>();
        refreshSellValue(spawner, player);
        placeholders.put("total_sell_price", languageManager.formatNumber(spawner.getAccumulatedSellValue()));

        return createButtonWithCustomTexture(button, player, placeholders, meta -> {
            meta.setDisplayName(languageManager.getGuiItemName("sell_and_exp_button.name", placeholders));
            meta.setLore(languageManager.getGuiItemLoreAsList("sell_and_exp_button.lore", placeholders));
        });
    }

    private ItemStack createCollectExpButton(SpawnerData spawner, GuiButton button, Player player) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("current_exp", languageManager.formatNumber(spawner.getSpawnerExp()));

        return createButtonWithCustomTexture(button, player, placeholders, meta -> {
            meta.setDisplayName(languageManager.getGuiItemName("collect_exp_button.name", placeholders));
            meta.setLore(languageManager.getGuiItemLoreAsList("collect_exp_button.lore", placeholders));
        });
    }

    private ItemStack createSortButton(SpawnerData spawner, GuiButton button, Player player) {
        Material currentSort = spawner.getPreferredSortItem();

        if (isViewerDependent(button)) {
            return buildSortButton(spawner, button, currentSort, player);
        }

        // Cache key = loot identity (the available_items list) + selected sort + button look.
        // These only change on click or reload, so the cached ItemStack is reused across redraws.
        String lootKey = spawner.isItemSpawner()
                ? "item:" + spawner.getSpawnedItemMaterial()
                : "mob:" + spawner.getEntityType();
        String cacheKey = lootKey
                + "|" + (currentSort == null ? "none" : currentSort.name())
                + "|" + button.getMaterial()
                + "|" + button.getCustomTexture();

        return sortButtonCache.computeIfAbsent(
                cacheKey, ignored -> buildSortButton(spawner, button, currentSort, null)).clone();
    }

    private ItemStack buildSortButton(SpawnerData spawner, GuiButton button, Material currentSort,
                                      Player player) {
        Map<String, String> placeholders = new HashMap<>();

        String selectedItemFormat = languageManager.getGuiItemName("sort_items_button.selected_item");
        String unselectedItemFormat = languageManager.getGuiItemName("sort_items_button.unselected_item");
        String noneText = languageManager.getGuiItemName("sort_items_button.no_item");

        StringBuilder availableItems = new StringBuilder();
        if (spawner.getLootConfig() != null && spawner.getLootConfig().getAllItems() != null) {
            boolean first = true;
            var sortedLoot = spawner.getLootConfig().getAllItems().stream()
                .sorted(Comparator.comparing(item -> item.material().name()))
                .toList();

            for (var lootItem : sortedLoot) {
                if (!first) availableItems.append("\n");
                String itemName = languageManager.getVanillaItemName(lootItem.material());
                String format = currentSort == lootItem.material() ? selectedItemFormat : unselectedItemFormat;
                availableItems.append(format.replace("{item_name}", itemName));
                first = false;
            }
        }

        if (availableItems.isEmpty()) {
            availableItems.append(noneText);
        }

        placeholders.put("available_items", availableItems.toString());

        return createButtonWithCustomTexture(button, player, placeholders, meta -> {
            meta.setDisplayName(languageManager.getGuiItemName("sort_items_button.name", placeholders));
            meta.setLore(languageManager.getGuiItemLoreWithMultilinePlaceholders("sort_items_button.lore", placeholders));
        });
    }

    private ItemStack createStorageSpawnerInfoButton(SpawnerData spawner, GuiButton button, Player player) {
        Map<ItemSignature, Long> storedItems = spawner.getVirtualInventory().getConsolidatedItems();
        List<Component> lootComponents = buildStorageInfoLootComponents(spawner, storedItems);

        Map<String, String> placeholders = new HashMap<>();
        String entityName = spawner.getDisplayName();
        placeholders.put("entity", entityName);
        placeholders.put("ᴇɴᴛɪᴛʏ", languageManager.getSmallCaps(entityName));
        placeholders.put("stack_size", String.valueOf(spawner.getStackSize()));

        int currentItems = spawner.getVirtualInventory().getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        double percentStorageDecimal = maxSlots > 0 ? ((double) currentItems / maxSlots) * 100 : 0;
        placeholders.put("percent_storage_decimal", String.format("%.1f", percentStorageDecimal));
        placeholders.put("percent_storage_rounded", String.valueOf((int) Math.round(percentStorageDecimal)));

        long currentExp = spawner.getSpawnerExp();
        long maxExp = spawner.getMaxStoredExp();
        double percentExpDecimal = maxExp > 0 ? ((double) currentExp / maxExp) * 100 : 0;
        placeholders.put("percent_exp_decimal", String.format("%.1f", percentExpDecimal));
        placeholders.put("percent_exp_rounded", String.valueOf((int) Math.round(percentExpDecimal)));

        Consumer<ItemMeta> metaModifier = meta -> {
            meta.setDisplayName(languageManager.getGuiItemName("storage_spawner_info_button.name", placeholders));
            List<Component> lore = languageManager.buildGuiLoreAsComponents(
                    "storage_spawner_info_button.lore", placeholders, lootComponents,
                    "storage_spawner_info_button.loot_items_empty");
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            GuiButtonRenderer.applyOverrides(meta, button, player, placeholders);
        };

        ItemStack item;
        if (spawner.isItemSpawner()) {
            item = SpawnerMobHeadTexture.getSpawnerHead(spawner, metaModifier);
        } else if (button.getMaterial() == Material.PLAYER_HEAD) {
            String customTexture = button.getCustomTexture();
            if (customTexture != null && !customTexture.trim().isEmpty()) {
                item = SpawnerMobHeadTexture.getCustomHeadFromTexture(customTexture, metaModifier);
            } else {
                item = SpawnerMobHeadTexture.getSpawnerHead(spawner, metaModifier);
            }
        } else {
            item = GuiButtonRenderer.createBaseItem(button);
            item.editMeta(metaModifier);
        }
        GuiButtonRenderer.finish(item, button);

        if (item.getType() == Material.SPAWNER) {
            ItemTooltipUtil.hideTooltip(item);
        }

        return item;
    }

    /**
     * Whether the built item depends on the viewer (custom display text with PlaceholderAPI tokens
     * or a full item template), in which case it must not be cached.
     */
    private boolean isViewerDependent(GuiButton button) {
        return button.getTemplate() != null
                || button.hasCustomDisplay()
                || GuiButtonRenderer.usesPlaceholderApi(button);
    }

    private void refreshSellValue(SpawnerData spawner, Player player) {
        ItemPriceManager priceManager = plugin.getItemPriceManager();
        if (spawner.isSellValueDirty() || (priceManager != null && priceManager.usesPlayerPricing())) {
            spawner.recalculateSellValue(player);
        }
    }

    private static Player resolveViewer(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        for (org.bukkit.entity.HumanEntity viewer : inventory.getViewers()) {
            if (viewer instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private List<Component> buildStorageInfoLootComponents(SpawnerData spawner,
            Map<ItemSignature, Long> storedItems) {
        Map<Material, Long> materialAmountMap = new HashMap<>();
        for (Map.Entry<ItemSignature, Long> entry : storedItems.entrySet()) {
            Material mat = entry.getKey().getMaterial();
            materialAmountMap.merge(mat, entry.getValue(), Long::sum);
        }

        EntityLootConfig lootConfig = spawner.getLootConfig();
        List<LootItem> possibleLootItems = lootConfig != null ? lootConfig.getAllItems() : Collections.emptyList();

        if (possibleLootItems.isEmpty() && storedItems.isEmpty()) {
            return Collections.emptyList();
        }

        List<Component> components = new ArrayList<>();
        if (!possibleLootItems.isEmpty()) {
            possibleLootItems.sort(Comparator.comparing(item -> item.material().name()));
            for (LootItem lootItem : possibleLootItems) {
                Material mat = lootItem.material();
                long amount = materialAmountMap.getOrDefault(mat, 0L);
                String formattedAmount = languageManager.formatNumber(amount);
                String chance = String.format("%.1f", lootItem.chance()) + "%";
                components.add(languageManager.buildTranslatableGuiLootLine(
                        "storage_spawner_info_button.loot_items", mat, formattedAmount, chance));
            }
        } else {
            List<Map.Entry<ItemSignature, Long>> sortedItems = new ArrayList<>(storedItems.entrySet());
            sortedItems.sort(Comparator.comparing(e -> e.getKey().getMaterialName()));
            for (Map.Entry<ItemSignature, Long> entry : sortedItems) {
                Material mat = entry.getKey().getMaterial();
                long amount = entry.getValue();
                String formattedAmount = languageManager.formatNumber(amount);
                components.add(languageManager.buildTranslatableGuiLootLine(
                        "storage_spawner_info_button.loot_items", mat, formattedAmount, ""));
            }
        }
        return components;
    }

    private void startCleanupTask() {
        cleanupTask = Scheduler.runTaskTimer(this::cleanupCaches, 20L * 30, 20L * 30); // Run every 30 seconds
    }

    public void cancelTasks() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }

    private void cleanupCaches() {
        // LRU-like cleanup for navigation buttons
        if (navigationButtonCache.size() > MAX_CACHE_SIZE) {
            int toRemove = navigationButtonCache.size() - (MAX_CACHE_SIZE / 2);
            List<String> keysToRemove = new ArrayList<>(navigationButtonCache.keySet());
            for (int i = 0; i < Math.min(toRemove, keysToRemove.size()); i++) {
                navigationButtonCache.remove(keysToRemove.get(i));
            }
        }

        // LRU-like cleanup for page indicators
        if (pageIndicatorCache.size() > MAX_CACHE_SIZE) {
            int toRemove = pageIndicatorCache.size() - (MAX_CACHE_SIZE / 2);
            List<String> keysToRemove = new ArrayList<>(pageIndicatorCache.keySet());
            for (int i = 0; i < Math.min(toRemove, keysToRemove.size()); i++) {
                pageIndicatorCache.remove(keysToRemove.get(i));
            }
        }

        // LRU-like cleanup for sort buttons
        if (sortButtonCache.size() > MAX_SORT_CACHE_SIZE) {
            int toRemove = sortButtonCache.size() - (MAX_SORT_CACHE_SIZE / 2);
            List<String> keysToRemove = new ArrayList<>(sortButtonCache.keySet());
            for (int i = 0; i < Math.min(toRemove, keysToRemove.size()); i++) {
                sortButtonCache.remove(keysToRemove.get(i));
            }
        }
    }

    public void cleanup() {
        navigationButtonCache.clear();
        pageIndicatorCache.clear();
        sortButtonCache.clear();
        cachedStorageTitleFormat = null;
        cachedSingleStorageTitleFormat = null;

        // Cancel scheduled tasks
        cancelTasks();

        // Re-initialize static buttons (just in case language has changed)
        initializeStaticButtons();
    }
}
