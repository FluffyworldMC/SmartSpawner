package github.nighter.smartspawner.spawner.gui.storage.filter;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * The filter GUI's layout: title, divider icon and the text shown on generated allowed/blocked
 * items. The screen has no {@code slot_} buttons because its items come from the spawner's loot
 * table.
 */
public final class FilterGuiLayout {

    private static final Set<Integer> DEFAULT_DIVIDER_SLOTS = Set.of(4, 13, 22);

    private final String title;
    private final Set<Integer> dividerSlots;
    private final Material dividerMaterial;
    private final String dividerName;
    private final List<String> dividerLore;
    private final String allowedName;
    private final List<String> allowedLore;
    private final String blockedName;
    private final List<String> blockedLore;

    private FilterGuiLayout(String title, Set<Integer> dividerSlots, Material dividerMaterial,
                            String dividerName, List<String> dividerLore,
                            String allowedName, List<String> allowedLore,
                            String blockedName, List<String> blockedLore) {
        this.title = title;
        this.dividerSlots = dividerSlots;
        this.dividerMaterial = dividerMaterial;
        this.dividerName = dividerName;
        this.dividerLore = dividerLore;
        this.allowedName = allowedName;
        this.allowedLore = allowedLore;
        this.blockedName = blockedName;
        this.blockedLore = blockedLore;
    }

    public static FilterGuiLayout empty() {
        return new FilterGuiLayout(null, DEFAULT_DIVIDER_SLOTS,
                Material.CYAN_STAINED_GLASS_PANE, null, List.of(),
                null, List.of(), null, List.of());
    }

    public static FilterGuiLayout load(File file) {
        if (file == null || !file.isFile()) {
            return empty();
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        Set<Integer> slots = DEFAULT_DIVIDER_SLOTS;
        List<Integer> configuredSlots = config.getIntegerList("divider_slots");
        if (!configuredSlots.isEmpty()) {
            Set<Integer> parsed = new java.util.HashSet<>();
            for (Integer slot : configuredSlots) {
                if (slot != null && slot >= 1 && slot <= 27) {
                    parsed.add(slot - 1);
                }
            }
            if (!parsed.isEmpty()) {
                slots = Set.copyOf(parsed);
            }
        }

        ConfigurationSection divider = config.getConfigurationSection("divider");
        ConfigurationSection allowed = config.getConfigurationSection("allowed_item");
        ConfigurationSection blocked = config.getConfigurationSection("blocked_item");

        return new FilterGuiLayout(
                blankToNull(config.getString("title")),
                slots,
                readMaterial(divider),
                divider != null ? divider.getString("name") : null,
                readLore(divider),
                allowed != null ? allowed.getString("name") : null,
                readLore(allowed),
                blocked != null ? blocked.getString("name") : null,
                readLore(blocked)
        );
    }

    private static Material readMaterial(ConfigurationSection section) {
        String name = section != null ? section.getString("material") : null;
        if (name != null && !name.isBlank()) {
            try {
                return Material.valueOf(name.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Keep the default pane.
            }
        }
        return Material.CYAN_STAINED_GLASS_PANE;
    }

    private static List<String> readLore(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        return List.copyOf(section.getStringList("lore"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public String getTitle() {
        return title;
    }

    public Set<Integer> getDividerSlots() {
        return dividerSlots;
    }

    public Material getDividerMaterial() {
        return dividerMaterial;
    }

    public String getDividerName() {
        return dividerName;
    }

    public List<String> getDividerLore() {
        return dividerLore;
    }

    public String getAllowedName() {
        return allowedName;
    }

    public List<String> getAllowedLore() {
        return allowedLore;
    }

    public String getBlockedName() {
        return blockedName;
    }

    public List<String> getBlockedLore() {
        return blockedLore;
    }
}
