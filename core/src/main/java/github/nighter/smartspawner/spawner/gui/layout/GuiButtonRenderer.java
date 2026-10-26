package github.nighter.smartspawner.spawner.gui.layout;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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
     * <p>Call this after the UI has written the language-default name and lore into the meta. Only
     * the parts the server owner actually configured are replaced.</p>
     */
    public static void applyOverrides(ItemMeta meta, GuiButton button, Player player,
                                      Map<String, String> placeholders) {
        if (meta == null || button == null) {
            return;
        }

        ItemMeta templateMeta = button.getTemplate() != null
                ? button.getTemplate().getItemMeta() : null;

        if (button.getDisplayName() != null) {
            String name = GuiText.render(player, button.getDisplayName(), placeholders);
            if (name != null) {
                meta.setDisplayName(name);
            }
        } else if (templateMeta != null && templateMeta.hasDisplayName()) {
            // A name baked into the item: template wins over the language default.
            meta.setDisplayName(templateMeta.getDisplayName());
        }

        if (button.getDisplayLore() != null) {
            meta.setLore(GuiText.renderLines(player, button.getDisplayLore(), placeholders));
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
