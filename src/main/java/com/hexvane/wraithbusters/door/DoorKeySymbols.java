package com.hexvane.wraithbusters.door;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DoorKeySymbols {
    public static final String ATTIC_SYMBOL = "attic";
    public static final String ATTIC_KEY_ITEM = "WraithBusters_Key_Attic";
    public static final String KEY_ITEM_PREFIX = "WraithBusters_Key_";

    private static final Map<String, String> SYMBOL_TO_ITEM = Map.ofEntries(
        Map.entry("circle", "WraithBusters_Key_Circle"),
        Map.entry("cross", "WraithBusters_Key_Cross"),
        Map.entry("diamond", "WraithBusters_Key_Diamond"),
        Map.entry("heart", "WraithBusters_Key_Heart"),
        Map.entry("hexagon", "WraithBusters_Key_Hexagon"),
        Map.entry("moon", "WraithBusters_Key_Moon"),
        Map.entry("pentagon", "WraithBusters_Key_Pentagon"),
        Map.entry("shield", "WraithBusters_Key_Shield"),
        Map.entry("square", "WraithBusters_Key_Square"),
        Map.entry("star", "WraithBusters_Key_Star"),
        Map.entry("triangle", "WraithBusters_Key_Triangle"),
        Map.entry(ATTIC_SYMBOL, ATTIC_KEY_ITEM)
    );

    private static final Set<String> KNOWN_SYMBOLS = SYMBOL_TO_ITEM.keySet();

    private DoorKeySymbols() {}

    @Nonnull
    public static String normalizeSymbolId(@Nonnull String symbolId) {
        String normalized = symbolId.trim().toLowerCase(Locale.ROOT);
        if ("blue".equals(normalized)) {
            return "circle";
        }
        return normalized;
    }

    public static boolean isKnownSymbol(@Nonnull String symbolId) {
        return KNOWN_SYMBOLS.contains(normalizeSymbolId(symbolId));
    }

    @Nonnull
    public static String itemIdForSymbol(@Nonnull String symbolId) {
        String normalized = normalizeSymbolId(symbolId);
        return SYMBOL_TO_ITEM.getOrDefault(normalized, KEY_ITEM_PREFIX + titleCase(normalized));
    }

    public static boolean isKeyItem(@Nonnull String itemId) {
        return itemId.startsWith(KEY_ITEM_PREFIX);
    }

    @Nonnull
    public static String symbolFromItemId(@Nonnull String itemId) {
        if (!isKeyItem(itemId)) {
            return "";
        }
        if (ATTIC_KEY_ITEM.equals(itemId)) {
            return ATTIC_SYMBOL;
        }
        if ("WraithBusters_Key_Blue".equals(itemId)) {
            return "circle";
        }
        String suffix = itemId.substring(KEY_ITEM_PREFIX.length());
        return suffix.toLowerCase(Locale.ROOT);
    }

    @Nonnull
    public static String displayName(@Nonnull String symbolId) {
        String normalized = normalizeSymbolId(symbolId);
        if (ATTIC_SYMBOL.equals(normalized)) {
            return "Attic";
        }
        return titleCase(normalized);
    }

    @Nonnull
    private static String titleCase(@Nonnull String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    @Nullable
    public static String symbolMatchingItem(@Nullable String itemId) {
        if (itemId == null || !isKeyItem(itemId)) {
            return null;
        }
        return symbolFromItemId(itemId);
    }
}
