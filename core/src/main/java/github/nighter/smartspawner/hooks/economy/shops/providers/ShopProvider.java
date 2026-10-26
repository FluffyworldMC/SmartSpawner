package github.nighter.smartspawner.hooks.economy.shops.providers;

import org.bukkit.Material;
import org.bukkit.entity.Player;

public interface ShopProvider {

    String getPluginName();

    boolean isAvailable();

    double getSellPrice(Material material);

    /**
     * Player-aware price lookup. Providers whose prices depend on the player (conditions, sell
     * multipliers) override this; everyone else inherits the material-only lookup.
     *
     * @param material the item being sold, never {@code null}
     * @param player   the seller, may be {@code null} when the caller has no player context
     * @return the price for one item, or {@code 0} when the item cannot be sold
     */
    default double getSellPrice(Material material, Player player) {
        return getSellPrice(material);
    }

    /**
     * Whether {@link #getSellPrice(Material, Player)} can produce a different result than the
     * material-only lookup. Used to decide when the cached sell value must be refreshed per player.
     */
    default boolean supportsPlayerPricing() {
        return false;
    }
}
