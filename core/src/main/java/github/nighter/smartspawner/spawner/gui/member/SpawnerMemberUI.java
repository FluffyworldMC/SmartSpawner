package github.nighter.smartspawner.spawner.gui.member;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.gui.layout.GuiButton;
import github.nighter.smartspawner.spawner.gui.layout.GuiButtonRenderer;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.gui.layout.GuiText;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The owner-only member list: add a player by typing their name in chat, remove one by clicking
 * their head. Members can open the spawner but cannot open this screen.
 */
public class SpawnerMemberUI implements Listener {
    private static final int INVENTORY_SIZE = 27;
    private static final String BYPASS_PERMISSION = "smartspawner.owner.bypass";

    private final SmartSpawner plugin;
    private final MessageService messageService;

    /** Player waiting to type a name, mapped to the spawner they are editing. */
    private final Map<UUID, String> pendingAdds = new ConcurrentHashMap<>();

    public SpawnerMemberUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
    }

    public void reload() {
        // Layout is resolved from GuiLayoutConfig on every open.
    }

    public void open(Player player, SpawnerData spawner) {
        if (player == null || spawner == null) {
            return;
        }
        if (!spawner.isOwner(player.getUniqueId())
                && !player.hasPermission(BYPASS_PERMISSION)) {
            messageService.sendMessage(player, "spawner_not_owner");
            return;
        }

        MemberGuiLayout layout = plugin.getGuiLayoutConfig().getCurrentMemberLayout();
        GuiLayout buttons = layout.getButtons();
        List<UUID> members = new ArrayList<>(spawner.getMemberUUIDs());
        members.sort((left, right) -> displayName(left).compareToIgnoreCase(displayName(right)));

        List<Integer> slots = layout.resolveMemberSlots(INVENTORY_SIZE);
        Map<Integer, UUID> memberSlots = new HashMap<>();
        int index = 0;
        for (UUID member : members) {
            while (index < slots.size() && buttons.isSlotUsed(slots.get(index))) {
                index++;
            }
            if (index >= slots.size()) {
                break;
            }
            memberSlots.put(slots.get(index), member);
            index++;
        }

        String title = GuiText.render(player, layout.getTitle(), Map.of(
                "player", player.getName(),
                "members", String.valueOf(members.size())
        ));
        Inventory inventory = Bukkit.createInventory(
                new SpawnerMemberHolder(spawner, buttons, memberSlots),
                INVENTORY_SIZE,
                title);

        for (Map.Entry<Integer, UUID> entry : memberSlots.entrySet()) {
            inventory.setItem(entry.getKey(), createMemberHead(player, layout, entry.getValue()));
        }

        for (GuiButton button : buttons.getAllButtons().values()) {
            if (!button.isEnabled()) {
                continue;
            }
            ItemStack item = GuiButtonRenderer.createBaseItem(button);
            item.editMeta(meta -> GuiButtonRenderer.applyOverrides(meta, button, player, Map.of()));
            GuiButtonRenderer.finish(item, button);
            inventory.setItem(button.getSlot(), item);
        }

        player.openInventory(inventory);
    }

    private ItemStack createMemberHead(Player viewer, MemberGuiLayout layout, UUID memberId) {
        OfflinePlayer member = Bukkit.getOfflinePlayer(memberId);
        String name = displayName(memberId);
        Map<String, String> placeholders = Map.of(
                "player", name,
                "uuid", memberId.toString()
        );

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            if (member.getName() != null) {
                meta.setOwningPlayer(member);
            }
            meta.setDisplayName(GuiText.render(viewer, layout.getMemberName(), placeholders));
            meta.setLore(GuiText.renderLines(viewer, layout.getMemberLore(), placeholders));
            head.setItemMeta(meta);
        }
        return head;
    }

    private String displayName(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return player.getName() != null ? player.getName() : playerId.toString().substring(0, 8);
    }

    @EventHandler
    public void onMemberClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof SpawnerMemberHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder(false) instanceof SpawnerMemberHolder)) {
            return;
        }

        SpawnerData spawner = holder.getSpawnerData();
        if (!spawner.isOwner(player.getUniqueId())
                && !player.hasPermission(BYPASS_PERMISSION)) {
            player.closeInventory();
            messageService.sendMessage(player, "spawner_not_owner");
            return;
        }

        int slot = event.getRawSlot();
        UUID member = holder.getMemberSlots().get(slot);
        if (member != null) {
            if (spawner.removeMember(member)) {
                plugin.getSpawnerManager().markSpawnerModified(spawner.getSpawnerId());
                messageService.sendMessage(player, "member_removed",
                        Map.of("player", displayName(member)));
            }
            open(player, spawner);
            return;
        }

        GuiButton button = holder.getLayout().getButtonAtSlot(slot).orElse(null);
        if (button == null) {
            return;
        }
        String action = button.getActionWithFallback(clickType(event.getClick()));
        if (action == null) {
            return;
        }

        switch (action) {
            case "add_member" -> {
                pendingAdds.put(player.getUniqueId(), spawner.getSpawnerId());
                player.closeInventory();
                messageService.sendMessage(player, "member_add_prompt");
            }
            case "return_main" -> plugin.getSpawnerMenuUI().openSpawnerMenu(player, spawner, true);
            default -> {
                // Unknown member-screen action: ignore it rather than falling back to a main-menu click.
            }
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String spawnerId = pendingAdds.remove(player.getUniqueId());
        if (spawnerId == null) {
            return;
        }

        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        Scheduler.runEntityTask(player, () -> addMember(player, spawnerId, input));
    }

    private void addMember(Player player, String spawnerId, String input) {
        SpawnerData spawner = plugin.getSpawnerManager().getSpawnerById(spawnerId);
        if (spawner == null) {
            return;
        }
        if (!spawner.isOwner(player.getUniqueId())
                && !player.hasPermission(BYPASS_PERMISSION)) {
            messageService.sendMessage(player, "spawner_not_owner");
            return;
        }

        if (input.equalsIgnoreCase("cancel") || input.isBlank()) {
            open(player, spawner);
            return;
        }

        OfflinePlayer target = Bukkit.getPlayerExact(input);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(input);
        }
        if (target == null || target.getUniqueId() == null) {
            messageService.sendMessage(player, "member_not_found", Map.of("player", input));
            open(player, spawner);
            return;
        }

        UUID targetId = target.getUniqueId();
        if (targetId.equals(spawner.getOwnerUUID())) {
            messageService.sendMessage(player, "member_cannot_add_owner");
            open(player, spawner);
            return;
        }
        if (spawner.hasMember(targetId)) {
            messageService.sendMessage(player, "member_already_added",
                    Map.of("player", target.getName() != null ? target.getName() : input));
            open(player, spawner);
            return;
        }

        spawner.addMember(targetId);
        plugin.getSpawnerManager().markSpawnerModified(spawner.getSpawnerId());
        messageService.sendMessage(player, "member_added",
                Map.of("player", target.getName() != null ? target.getName() : input));
        open(player, spawner);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingAdds.remove(event.getPlayer().getUniqueId());
    }

    private String clickType(ClickType clickType) {
        return switch (clickType) {
            case RIGHT -> "right_click";
            case SHIFT_LEFT -> "shift_left_click";
            case SHIFT_RIGHT -> "shift_right_click";
            default -> "left_click";
        };
    }

    public void cleanup() {
        pendingAdds.clear();
    }
}
