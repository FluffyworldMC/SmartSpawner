package github.nighter.smartspawner.spawner.gui.member;

import github.nighter.smartspawner.api.gui.ExternalGuiLayoutLoader;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The member-management screen: title, the head shown for each member, and the slot buttons
 * ({@code add_member}, {@code return_main}).
 */
public final class MemberGuiLayout {

    private final String title;
    private final String memberName;
    private final List<String> memberLore;
    private final Set<Integer> memberSlots;
    private final GuiLayout buttons;

    private MemberGuiLayout(String title, String memberName, List<String> memberLore,
                            Set<Integer> memberSlots, GuiLayout buttons) {
        this.title = title;
        this.memberName = memberName;
        this.memberLore = memberLore;
        this.memberSlots = memberSlots;
        this.buttons = buttons;
    }

    public static MemberGuiLayout empty() {
        return new MemberGuiLayout("Spawner Members", "&f{player}",
                List.of("&7Click to remove this member"), Set.of(), new GuiLayout());
    }

    public static MemberGuiLayout load(File file, ExternalGuiLayoutLoader loader) {
        if (file == null || !file.isFile()) {
            return empty();
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        GuiLayout buttons = loader.loadLayout(file, "members");

        Set<Integer> slots = new HashSet<>();
        for (Integer slot : config.getIntegerList("member_slots")) {
            if (slot != null && slot >= 1 && slot <= 27) {
                slots.add(slot - 1);
            }
        }

        String title = config.getString("title");
        String name = config.getString("member_item.name");
        List<String> lore = config.getStringList("member_item.lore");

        return new MemberGuiLayout(
                title == null || title.isBlank() ? "Spawner Members" : title,
                name == null || name.isBlank() ? "&f{player}" : name,
                lore.isEmpty() ? List.of("&7Click to remove this member") : List.copyOf(lore),
                Set.copyOf(slots),
                buttons
        );
    }

    public String getTitle() {
        return title;
    }

    public String getMemberName() {
        return memberName;
    }

    public List<String> getMemberLore() {
        return memberLore;
    }

    /**
     * Configured 0-based member slots. Empty means "every slot not used by a button".
     */
    public Set<Integer> getMemberSlots() {
        return memberSlots;
    }

    public GuiLayout getButtons() {
        return buttons;
    }

    public List<Integer> resolveMemberSlots(int inventorySize) {
        Set<Integer> usedByButtons = buttons.getUsedSlots();
        List<Integer> slots = new ArrayList<>();
        if (!memberSlots.isEmpty()) {
            for (Integer slot : memberSlots) {
                if (slot < inventorySize && !usedByButtons.contains(slot)) {
                    slots.add(slot);
                }
            }
            return slots;
        }

        for (int slot = 0; slot < inventorySize; slot++) {
            if (!usedByButtons.contains(slot)) {
                slots.add(slot);
            }
        }
        return slots;
    }
}
