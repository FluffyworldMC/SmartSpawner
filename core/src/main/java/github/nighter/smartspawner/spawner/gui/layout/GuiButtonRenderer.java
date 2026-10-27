package github.nighter.smartspawner.spawner.gui.layout;

import github.nighter.smartspawner.language.format.LanguageComponentFormatter;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Applies the per-button display customisation ({@code item}, {@code name}, {@code lore},
 * {@code item_model}, {@code custom_model_data}, {@code amount}, {@code hide_tooltip}) that a server
 * owner writes in a GUI layout file.
 */
public final class GuiButtonRenderer {

    private static final Pattern PAPI_TOKEN = Pattern.compile("%[^%\\s]+%");

    private GuiButtonRenderer() {
    }

    /**
     * Builds the base icon for a button: the configured {@code item:} template when present, else a
     * plain stack of the configured material.
     */
    public static ItemStack createBaseItem(GuiButton button) {
        if (button.getTemplate() != null) {
            return button.getTemplate().clone();
        }
        return new ItemStack(button.getMaterial());
    }

    /**
     * Overrides the display name, lore and item components on an already-built icon.
     *
     * <p>The button owns its text; this applies only the parts it declares and leaves the icon's
     * own template values alone otherwise.</p>
     */
    public static void applyOverrides(ItemMeta meta, GuiButton button, Player player,
                                      Map<String, String> placeholders) {
        applyOverrides(meta, button, player, placeholders, null, null);
    }

    /**
     * Applies display overrides and expands {@code {loot_items}} in a button's custom lore to the
     * supplied components. Used by the buttons that show a loot summary.
     */
    public static void applyOverrides(ItemMeta meta, GuiButton button, Player player,
                                      Map<String, String> placeholders,
                                      List<Component> lootComponents, String emptyLootLine) {
        apply(meta, button, player, placeholders, lootComponents, emptyLootLine, false);
    }

    /**
     * Applies the {@code name_with_exp} / {@code lore_with_exp} variant when the button is the
     * sell-confirmation button and the click also collects experience.
     */
    public static void applyOverridesWithExp(ItemMeta meta, GuiButton button, Player player,
                                             Map<String, String> placeholders,
                                             List<Component> lootComponents, String emptyLootLine) {
        apply(meta, button, player, placeholders, lootComponents, emptyLootLine, true);
    }

    private static void apply(ItemMeta meta, GuiButton button, Player player,
                              Map<String, String> placeholders,
                              List<Component> lootComponents, String emptyLootLine,
                              boolean withExp) {
        if (meta == null || button == null) {
            return;
        }

        ItemMeta templateMeta = button.getTemplate() != null
                ? button.getTemplate().getItemMeta() : null;
        String displayName = withExp && button.getDisplayNameWithExp() != null
                ? button.getDisplayNameWithExp() : button.getDisplayName();
        List<String> displayLore = withExp && button.getDisplayLoreWithExp() != null
                ? button.getDisplayLoreWithExp() : button.getDisplayLore();

        if (displayName != null) {
            String name = GuiText.render(player, displayName, placeholders);
            if (name != null) {
                meta.setDisplayName(name);
            }
        } else if (templateMeta != null && templateMeta.hasDisplayName()) {
            // A name baked into the item: template wins over the fallback name.
            meta.setDisplayName(templateMeta.getDisplayName());
        }

        if (displayLore != null) {
            if (lootComponents != null && containsLootPlaceholder(displayLore)) {
                meta.lore(LanguageComponentFormatter.loreComponents(
                        displayLore,
                        line -> GuiText.render(player, line, placeholders),
                        () -> emptyLootLine == null ? ""
                                : GuiText.render(player, emptyLootLine, placeholders),
                        lootComponents));
            } else {
                meta.setLore(GuiText.renderLines(player, displayLore, placeholders));
            }
        } else if (templateMeta != null && templateMeta.hasLore()) {
            meta.setLore(templateMeta.getLore());
        }

        if (button.getItemModel() != null) {
            NamespacedKey key = parseKey(button.getItemModel());
            if (key != null) {
                meta.setItemModel(key);
            }
        }

        if (button.getCustomModelData() != null) {
            meta.setCustomModelData(button.getCustomModelData());
        }

        if (button.isHideTooltip()) {
            meta.setHideTooltip(true);
        }
    }

    /**
     * Applies the stack amount configured on the button.
     */
    public static ItemStack finish(ItemStack item, GuiButton button) {
        if (item != null && button != null && button.getAmount() != 1) {
            item.setAmount(button.getAmount());
        }
        return item;
    }

    /**
     * Whether the button's custom text contains PlaceholderAPI tokens. Such a button must not be
     * cached per spawner, because the rendered value depends on the viewer.
     */
    public static boolean usesPlaceholderApi(GuiButton button) {
        if (button == null) {
            return false;
        }
        if (hasPapi(button.getDisplayName())) {
            return true;
        }
        if (button.getDisplayLore() != null) {
            for (String line : button.getDisplayLore()) {
                if (hasPapi(line)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether any part of the button's presentation depends on the viewer, which makes the built
     * {@link ItemStack} unsafe to cache.
     */
    public static boolean isViewerDependent(GuiButton button) {
        return usesPlaceholderApi(button);
    }

    private static boolean hasPapi(String text) {
        return text != null && PAPI_TOKEN.matcher(text).find();
    }

    private static boolean containsLootPlaceholder(List<String> lore) {
        for (String line : lore) {
            if (line != null && line.contains("{loot_items}")) {
                return true;
            }
        }
        return false;
    }

    private static NamespacedKey parseKey(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        if (value.indexOf(':') < 0) {
            value = "minecraft:" + value;
        }
        try {
            return NamespacedKey.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
