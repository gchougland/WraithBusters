package com.hexvane.wraithbusters.puzzle;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public enum BookColor {
    BLUE(
        "WraithBusters_Puzzle_Bookshelf_Blue",
        "WraithBusters_Book_Pickup_Blue",
        "WraithBusters_Book_Blue"
    ),
    GREEN(
        "WraithBusters_Puzzle_Bookshelf_Green",
        "WraithBusters_Book_Pickup_Green",
        "WraithBusters_Book_Green"
    ),
    ORANGE(
        "WraithBusters_Puzzle_Bookshelf_Orange",
        "WraithBusters_Book_Pickup_Orange",
        "WraithBusters_Book_Orange"
    ),
    RED(
        "WraithBusters_Puzzle_Bookshelf_Red",
        "WraithBusters_Book_Pickup_Red",
        "WraithBusters_Book_Red"
    );

    @Nonnull
    private final String bookshelfBlockId;
    @Nonnull
    private final String pickupBlockId;
    @Nonnull
    private final String itemId;

    BookColor(@Nonnull String bookshelfBlockId, @Nonnull String pickupBlockId, @Nonnull String itemId) {
        this.bookshelfBlockId = bookshelfBlockId;
        this.pickupBlockId = pickupBlockId;
        this.itemId = itemId;
    }

    @Nonnull
    public String getBookshelfBlockId() {
        return bookshelfBlockId;
    }

    @Nonnull
    public String getPickupBlockId() {
        return pickupBlockId;
    }

    @Nonnull
    public String getItemId() {
        return itemId;
    }

    @Nullable
    public static BookColor fromBookshelfBlockId(@Nullable String blockId) {
        if (blockId == null) {
            return null;
        }
        for (BookColor color : values()) {
            if (color.bookshelfBlockId.equals(blockId)) {
                return color;
            }
        }
        return null;
    }

    @Nullable
    public static BookColor fromPickupBlockId(@Nullable String blockId) {
        if (blockId == null) {
            return null;
        }
        for (BookColor color : values()) {
            if (color.pickupBlockId.equals(blockId)) {
                return color;
            }
        }
        return null;
    }

    @Nullable
    public static BookColor fromItemId(@Nullable String itemId) {
        if (itemId == null) {
            return null;
        }
        for (BookColor color : values()) {
            if (color.itemId.equals(itemId)) {
                return color;
            }
        }
        return null;
    }

    @Nullable
    public static BookColor fromSetupExtra(@Nullable String extra) {
        if (extra == null || extra.isBlank()) {
            return null;
        }
        return switch (extra.trim().toLowerCase()) {
            case "blue" -> BLUE;
            case "green" -> GREEN;
            case "orange" -> ORANGE;
            case "red" -> RED;
            default -> null;
        };
    }
}
