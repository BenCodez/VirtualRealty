package com.modnmetl.virtualrealty.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralised, null-safe player name lookup.
 *
 * Handles:
 *  - Online Java players
 *  - Offline players with cached names
 *  - Floodgate / Bedrock UUIDs that may not have names yet
 *  - Missing profiles (falls back to short UUID string)
 *
 * All methods are safe to call on Paper 1.21.x and in Geyser/Floodgate setups.
 */
public final class PlayerLookupUtil {

    // Simple in-memory cache to avoid hammering Bukkit lookups repeatedly.
    private static final Map<UUID, String> NAME_CACHE = new ConcurrentHashMap<>();

    private PlayerLookupUtil() {
        // utility
    }

    /**
     * Manually remember a mapping (useful from join listeners, etc.).
     */
    public static void remember(UUID uuid, String name) {
        if (uuid == null || name == null || name.isEmpty()) {
            return;
        }
        NAME_CACHE.put(uuid, name);
    }

    /**
     * Get the best display name we can for this UUID.
     *
     * Never throws; returns:
     *  - Player#getName() if online
     *  - OfflinePlayer#getName() if available
     *  - cached name from this util if known
     *  - short UUID string as a last resort
     *
     * May return null only if uuid is null.
     */
    public static String getBestName(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        // 0. Our own cache first (fast path, avoids frequent Bukkit calls)
        String cached = NAME_CACHE.get(uuid);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        // 1. If they're online, trust the live Player object
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            String name = online.getName();
            if (name != null && !name.isEmpty()) {
                NAME_CACHE.put(uuid, name);
                return name;
            }
        }

        // 2. Try the OfflinePlayer cache
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline != null) {
            String name = offline.getName();
            if (name != null && !name.isEmpty()) {
                NAME_CACHE.put(uuid, name);
                return name;
            }
        }

        // 3. Still nothing? Use a short UUID string as a stable fallback.
        // This is safe because UUID.toString() is always 36 chars.
        String shortId = uuid.toString().substring(0, 8);
        NAME_CACHE.put(uuid, shortId);
        return shortId;
    }

    /**
     * Null-safe helper when you already have an OfflinePlayer.
     * Delegates to getBestName(UUID) when needed.
     */
    public static String getBestName(OfflinePlayer offlinePlayer) {
        if (offlinePlayer == null) {
            return null;
        }

        UUID uuid = offlinePlayer.getUniqueId();

        // If it's actually a Player, use that directly
        if (offlinePlayer instanceof Player) {
            String name = ((Player) offlinePlayer).getName();
            if (name != null && !name.isEmpty()) {
                if (uuid != null) {
                    NAME_CACHE.put(uuid, name);
                }
                return name;
            }
        }

        // Try OfflinePlayer#getName first
        String name = offlinePlayer.getName();
        if (name != null && !name.isEmpty()) {
            if (uuid != null) {
                NAME_CACHE.put(uuid, name);
            }
            return name;
        }

        // Fallback to UUID-based lookup (will handle cache, online check, etc.)
        if (uuid == null) {
            return null;
        }
        return getBestName(uuid);
    }

    /**
     * Convenience: try best name, else fall back to a provided string,
     * else short UUID. Handy when you have the original argument (like a username).
     */
    public static String getBestNameOrFallback(UUID uuid, String fallback) {
        String best = getBestName(uuid);
        if (best != null && !best.isEmpty()) {
            return best;
        }
        if (fallback != null && !fallback.isEmpty()) {
            return fallback;
        }
        return uuid == null ? null : uuid.toString().substring(0, 8);
    }
}
