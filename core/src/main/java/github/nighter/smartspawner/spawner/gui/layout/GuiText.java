package github.nighter.smartspawner.spawner.gui.layout;

import github.nighter.smartspawner.hooks.PlaceholderApiHook;
import github.nighter.smartspawner.language.format.ColorUtil;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders the free-form text a server owner writes in a GUI layout file.
 *
 * <p>The order matters: this plugin's own {@code {placeholder}} tokens are replaced first, then
 * PlaceholderAPI tokens are expanded against the viewer, and colour codes are translated last so a
 * placeholder that expands to coloured text still renders correctly.</p>
 */
public final class GuiText {

    private GuiText() {
    }

    /**
     * Renders one line.
     *
     * @param player       the viewing player, used for PlaceholderAPI; may be {@code null}
     * @param raw          the configured text
     * @param placeholders this plugin's own placeholders, keyed without braces
     * @return the rendered text, or {@code null} when {@code raw} is {@code null}
     */
    public static String render(Player player, String raw, Map<String, String> placeholders) {
        if (raw == null) {
            return null;
        }

        String text = raw;
        if (placeholders != null && !placeholders.isEmpty()) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                text = text.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        text = PlaceholderApiHook.apply(player, text);
        return ColorUtil.translateHexColorCodes(text);
    }

    /**
     * Renders a list of lines, keeping line breaks already present inside a single entry.
     */
    public static List<String> renderLines(Player player, List<String> raw, Map<String, String> placeholders) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }

        List<String> result = new ArrayList<>(raw.size());
        for (String line : raw) {
            if (line == null) {
                continue;
            }
            String rendered = render(player, line, placeholders);
            if (rendered == null) {
                continue;
            }
            if (rendered.indexOf('\n') >= 0) {
                result.addAll(List.of(rendered.split("\n", -1)));
            } else {
                result.add(rendered);
            }
        }
        return result;
    }
}
