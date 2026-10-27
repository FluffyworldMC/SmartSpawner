package github.nighter.smartspawner.spawner.gui.layout;

import github.nighter.smartspawner.api.gui.GuiButtonSoundData;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class GuiButton {
    private final String buttonType;
    private final int slot;
    private final Material material;
    private final boolean enabled;
    private final String condition;
    private final Map<String, String> actions;
    private final boolean infoButton;
    private final String customTexture;
    private final long cooldownTicks;
    private final Map<String, List<GuiButtonSoundData>> clickSounds;
    private final Map<String, List<GuiButtonSoundData>> successSounds;
    private final Map<String, List<GuiButtonSoundData>> failSounds;

    /**
     * Optional full item template from the layout's {@code item:} key. It carries components
     * (item model, custom model data, ...) that a plain material cannot express.
     */
    private final ItemStack template;

    /** Custom display name from {@code name:}, or {@code null} to use the language default. */
    private final String displayName;

    /** Custom lore from {@code lore:}, or {@code null} to use the language default. */
    private final List<String> displayLore;

    /** Custom {@code item_model} component, or {@code null} to leave it untouched. */
    private final String itemModel;

    /** Custom {@code custom_model_data} component, or {@code null} to leave it untouched. */
    private final Integer customModelData;

    /** How many items the button stack shows. Defaults to 1. */
    private final int amount;

    /** Whether to hide the hover tooltip entirely. */
    private final boolean hideTooltip;

    /**
     * Extra text owned by this button: loot line format, countdown status, sort list labels.
     * Kept on the button so the whole GUI reads as one slot entry.
     */
    @Setter
    private Map<String, String> extraTexts = Map.of();

    /** Optional confirm-button variant used when the click also collects experience. */
    @Setter
    private String displayNameWithExp;
    @Setter
    private List<String> displayLoreWithExp;

    public String getText(String key) {
        return extraTexts.get(key);
    }

    public boolean hasText(String key) {
        return extraTexts.containsKey(key);
    }

    public GuiButton(String buttonType, int slot, Material material, boolean enabled,
                     String condition, Map<String, String> actions, boolean infoButton,
                     String customTexture) {
        this(buttonType, slot, material, enabled, condition, actions, infoButton,
                customTexture, 0L, Map.of(), Map.of(), Map.of(),
                null, null, null, null, null, 1, false);
    }

    public GuiButton(String buttonType, int slot, Material material, boolean enabled,
                     String condition, Map<String, String> actions, boolean infoButton,
                     String customTexture, long cooldownTicks, GuiButtonSoundData clickSound) {
        this(buttonType, slot, material, enabled, condition, actions, infoButton,
                customTexture, cooldownTicks, singletonSoundMap(clickSound), Map.of(), Map.of(),
                null, null, null, null, null, 1, false);
    }

    public GuiButton(String buttonType, int slot, Material material, boolean enabled,
                     String condition, Map<String, String> actions, boolean infoButton,
                     String customTexture, long cooldownTicks, GuiButtonSoundData clickSound,
                     GuiButtonSoundData successSound, GuiButtonSoundData failSound) {
        this(buttonType, slot, material, enabled, condition, actions, infoButton,
                customTexture, cooldownTicks, singletonSoundMap(clickSound),
                singletonSoundMap(successSound), singletonSoundMap(failSound),
                null, null, null, null, null, 1, false);
    }

    public GuiButton(String buttonType, int slot, Material material, boolean enabled,
                     String condition, Map<String, String> actions, boolean infoButton,
                     String customTexture, long cooldownTicks,
                     Map<String, List<GuiButtonSoundData>> clickSounds,
                     Map<String, List<GuiButtonSoundData>> successSounds,
                     Map<String, List<GuiButtonSoundData>> failSounds) {
        this(buttonType, slot, material, enabled, condition, actions, infoButton,
                customTexture, cooldownTicks, clickSounds, successSounds, failSounds,
                null, null, null, null, null, 1, false);
    }

    /**
     * Full constructor, including the per-button display customisation.
     */
    public GuiButton(String buttonType, int slot, Material material, boolean enabled,
                     String condition, Map<String, String> actions, boolean infoButton,
                     String customTexture, long cooldownTicks,
                     Map<String, List<GuiButtonSoundData>> clickSounds,
                     Map<String, List<GuiButtonSoundData>> successSounds,
                     Map<String, List<GuiButtonSoundData>> failSounds,
                     ItemStack template, String displayName, List<String> displayLore,
                     String itemModel, Integer customModelData, int amount, boolean hideTooltip) {
        this.buttonType = buttonType;
        this.slot = slot;
        this.template = template == null ? null : template.clone();
        this.material = template != null ? template.getType() : material;
        this.enabled = enabled;
        this.condition = condition;
        this.actions = actions;
        this.infoButton = infoButton;
        this.customTexture = customTexture;
        this.cooldownTicks = Math.max(0L, cooldownTicks);
        this.clickSounds = immutableSounds(clickSounds);
        this.successSounds = immutableSounds(successSounds);
        this.failSounds = immutableSounds(failSounds);
        this.displayName = displayName;
        this.displayLore = displayLore == null ? null : List.copyOf(displayLore);
        this.itemModel = itemModel;
        this.customModelData = customModelData;
        this.amount = Math.max(1, Math.min(99, amount));
        this.hideTooltip = hideTooltip;
    }

    /** Whether this button overrides any part of its rendered display. */
    public boolean hasCustomDisplay() {
        return displayName != null
                || displayLore != null
                || itemModel != null
                || customModelData != null
                || amount != 1
                || hideTooltip;
    }

    public String getAction(String clickType) {
        return actions != null ? actions.get(clickType) : null;
    }

    public String getActionWithFallback(String clickType) {
        if (actions == null || actions.isEmpty()) {
            return null;
        }
        String specificAction = actions.get(clickType);
        if (specificAction != null && !specificAction.isEmpty()) {
            return specificAction;
        }
        return actions.get("click");
    }

    public String getDefaultAction() {
        if (actions == null || actions.isEmpty()) {
            return null;
        }
        String clickAction = actions.get("click");
        if (clickAction != null && !clickAction.isEmpty()) {
            return clickAction;
        }
        return actions.get("default");
    }

    public List<GuiButtonSoundData> getClickSounds(String clickType) {
        return getSoundsWithFallback(clickSounds, clickType);
    }

    public List<GuiButtonSoundData> getSuccessSounds(String clickType) {
        return getSoundsWithFallback(successSounds, clickType);
    }

    public List<GuiButtonSoundData> getFailSounds(String clickType) {
        return getSoundsWithFallback(failSounds, clickType);
    }

    public GuiButtonSoundData getClickSound() {
        return firstSound(getClickSounds("click"));
    }

    public GuiButtonSoundData getSuccessSound() {
        return firstSound(getSuccessSounds("click"));
    }

    public GuiButtonSoundData getFailSound() {
        return firstSound(getFailSounds("click"));
    }

    public boolean hasCondition() {
        return condition != null && !condition.isEmpty();
    }

    private List<GuiButtonSoundData> getSoundsWithFallback(
            Map<String, List<GuiButtonSoundData>> sounds, String clickType) {
        List<GuiButtonSoundData> configured = sounds.get(clickType);
        if (configured != null) {
            return configured;
        }
        configured = sounds.get("click");
        return configured != null ? configured : List.of();
    }

    private static GuiButtonSoundData firstSound(List<GuiButtonSoundData> sounds) {
        return sounds.isEmpty() ? null : sounds.getFirst();
    }

    private static Map<String, List<GuiButtonSoundData>> singletonSoundMap(
            GuiButtonSoundData sound) {
        return sound == null ? Map.of() : Map.of("click", List.of(sound));
    }

    private static Map<String, List<GuiButtonSoundData>> immutableSounds(
            Map<String, List<GuiButtonSoundData>> sounds) {
        if (sounds == null || sounds.isEmpty()) {
            return Map.of();
        }
        Map<String, List<GuiButtonSoundData>> copy = new HashMap<>();
        sounds.forEach((clickType, values) -> {
            if (clickType != null && values != null) {
                copy.put(clickType, List.copyOf(values));
            }
        });
        return Collections.unmodifiableMap(copy);
    }
}
