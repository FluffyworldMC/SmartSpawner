package github.nighter.smartspawner.spawner.gui.member;

import github.nighter.smartspawner.spawner.gui.SpawnerHolder;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.UUID;

public class SpawnerMemberHolder implements InventoryHolder, SpawnerHolder {
    @Getter
    private final SpawnerData spawnerData;
    @Getter
    private final GuiLayout layout;
    /** Slot to member UUID, so a click does not depend on the item's name. */
    @Getter
    private final Map<Integer, UUID> memberSlots;

    public SpawnerMemberHolder(SpawnerData spawnerData, GuiLayout layout,
                               Map<Integer, UUID> memberSlots) {
        this.spawnerData = spawnerData;
        this.layout = layout;
        this.memberSlots = Map.copyOf(memberSlots);
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
