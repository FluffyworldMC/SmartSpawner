package github.nighter.smartspawner.hooks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Optional PlaceholderAPI bridge.
 *
 * <p>PlaceholderAPI is not a compile-time dependency: every call goes through reflection so the
 * plugin still boots when PlaceholderAPI is missing. The reflective lookup runs once, lazily, and
 * every failure degrades to "return the text unchanged".</p>
 */
public final class PlaceholderApiHook {

    private static final String PLUGIN_NAME = "PlaceholderAPI";
    private static final String API_CLASS = "me.clip.placeholderapi.PlaceholderAPI";

    private static volatile boolean initialised;
    private static volatile boolean available;
    private static Method setPlaceholders;

    private PlaceholderApiHook() {
    }

    /**
     * Resolves the PlaceholderAPI bridge. Safe to call more than once; only the first call does work.
     */
    public static void initialize() {
        if (initialised) {
            return;
        }
        synchronized (PlaceholderApiHook.class) {
            if (initialised) {
                return;
            }
            initialised = true;

            if (Bukkit.getPluginManager().getPlugin(PLUGIN_NAME) == null) {
                return;
            }

            try {
                Class<?> api = Class.forName(API_CLASS);
                setPlaceholders = api.getMethod("setPlaceholders", Player.class, String.class);
                available = true;
            } catch (Throwable ignored) {
                // PlaceholderAPI present but its API shape is not what we expect. Stay dormant.
                available = false;
            }
        }
    }

    public static boolean isAvailable() {
        initialize();
        return available;
    }

    /**
     * Expands {@code %placeholder%} tokens for the given player.
     *
     * @param player the player to resolve against, or {@code null} to return the text unchanged
     * @param text   the raw text, may be {@code null}
     * @return the expanded text, or the input when PlaceholderAPI is unavailable
     */
    public static String apply(Player player, String text) {
        if (player == null || text == null || text.isEmpty() || text.indexOf('%') < 0) {
            return text;
        }
        if (!isAvailable()) {
            return text;
        }

        try {
            Object result = setPlaceholders.invoke(null, player, text);
            return result instanceof String expanded ? expanded : text;
        } catch (Throwable ignored) {
            return text;
        }
    }
}
