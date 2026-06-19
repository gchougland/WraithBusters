package com.hexvane.wraithbusters.arena;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.puzzle.BookColor;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

public final class BookshelfMarker {
    @Nonnull
    private String puzzleId = WraithBustersConstants.LIBRARY_BOOKS_PUZZLE_ID;
    @Nonnull
    private BookColor color = BookColor.BLUE;
    @Nonnull
    private Vector3i blockPos = new Vector3i();

    @Nonnull
    public String getPuzzleId() {
        return puzzleId;
    }

    public void setPuzzleId(@Nonnull String puzzleId) {
        this.puzzleId = puzzleId;
    }

    @Nonnull
    public BookColor getColor() {
        return color;
    }

    public void setColor(@Nonnull BookColor color) {
        this.color = color;
    }

    @Nonnull
    public Vector3i getBlockPos() {
        return blockPos;
    }

    public void setBlockPos(@Nonnull Vector3i blockPos) {
        this.blockPos = blockPos;
    }
}
