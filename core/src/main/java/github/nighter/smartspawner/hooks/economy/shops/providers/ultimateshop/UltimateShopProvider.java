package github.nighter.smartspawner.hooks.economy.shops.providers.ultimateshop;

import cn.superiormc.ultimateshop.api.ShopHelper;
import cn.superiormc.ultimateshop.objects.items.AbstractSingleThing;
import cn.superiormc.ultimateshop.objects.items.GiveResult;
import cn.superiormc.ultimateshop.objects.items.ThingType;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.economy.shops.providers.ShopProvider;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sells spawner loot at UltimateShop's configured sell prices.
 *
 * <p>UltimateShop prices are resolved from its sell-price API. Only prices paid in Vault are
 * accepted, because SmartSpawner pays the seller through its own currency layer. Items whose
 * UltimateShop reward is not Vault (items, commands, other economies) are treated as unsellable
 * rather than paying out a wrong amount.</p>
 *
 * <p>UltimateShop conditions can depend on the player, so the authoritative lookup happens at sell
 * time with the real seller. Lookups are cached briefly per player because the GUIs rebuild their
 * sell-price line every second; without the cache every viewer would re-scan every shop on every
 * repaint.</p>
 */
@RequiredArgsConstructor
public class UltimateShopProvider implements ShopProvider {

    private static final String PLUGIN_NAME = "UltimateShop";
    private static final long PLAYER_CACHE_TTL_MS = 10_000L;

    private final SmartSpawner plugin;
    private final Map<Material, Double> priceCache = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Material, CachedPrice>> playerPriceCache = new ConcurrentHashMap<>();

    private record CachedPrice(double price, long expiresAt) {
    }

    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    public boolean isAvailable() {
        Plugin ultimateShop = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        return ultimateShop != null && ultimateShop.isEnabled();
    }

    @Override
    public double getSellPrice(Material material) {
        if (material == null) {
            return 0.0;
        }
        return priceCache.getOrDefault(material, 0.0);
    }

    @Override
    public double getSellPrice(Material material, Player player) {
        if (material == null) {
            return 0.0;
        }
        if (player == null) {
            return getSellPrice(material);
        }
        if (!material.isItem()) {
            return 0.0;
        }

        if (playerPriceCache.size() > 256) {
            playerPriceCache.clear();
        }
        Map<Material, CachedPrice> playerPrices = playerPriceCache.computeIfAbsent(
                player.getUniqueId(), ignored -> new ConcurrentHashMap<>());
        CachedPrice cached = playerPrices.get(material);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt() > now) {
            return cached.price();
        }

        double price = priceCache.getOrDefault(material, 0.0);
        try {
            double resolved = queryVaultSellPrice(material, player);
            if (resolved >= 0.0) {
                price = resolved;
                priceCache.put(material, resolved);
            }
        } catch (Throwable throwable) {
            // UltimateShop not ready, a version without this API, or a broken shop entry.
            // Fall through to the last known price instead of failing the sale.
        }

        playerPrices.put(material, new CachedPrice(price, now + PLAYER_CACHE_TTL_MS));
        return price;
    }

    /**
     * Resolves the Vault-equivalent sell price for a single item.
     *
     * <p>UltimateShop can pay in items, commands or several economies at once. SmartSpawner pays the
     * seller through its own currency layer, so only a single Vault reward is safe to translate into
     * a number. Anything else returns {@code -1} and the item is treated as unsellable.</p>
     */
    private double queryVaultSellPrice(Material material, Player player) {
        GiveResult giveResult = ShopHelper.getSellPrices(
                new ItemStack[]{new ItemStack(material)}, player, 1);
        if (giveResult == null) {
            return -1.0;
        }

        Map<AbstractSingleThing, BigDecimal> result = giveResult.getResultMap();
        if (result == null || result.size() != 1) {
            return -1.0;
        }

        Map.Entry<AbstractSingleThing, BigDecimal> entry = result.entrySet().iterator().next();
        AbstractSingleThing thing = entry.getKey();
        BigDecimal amount = entry.getValue();
        if (thing == null || thing.empty || amount == null || thing.type != ThingType.HOOK_ECONOMY) {
            return -1.0;
        }

        String economy = thing.getSingleSection().getString("economy-plugin", "");
        if (!"Vault".equalsIgnoreCase(economy)) {
            return -1.0;
        }
        return amount.doubleValue();
    }

    @Override
    public boolean supportsPlayerPricing() {
        return true;
    }
}
