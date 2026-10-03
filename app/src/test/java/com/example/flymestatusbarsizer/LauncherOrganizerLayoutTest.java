package com.example.flymestatusbarsizer;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.List;
import java.util.Set;

public final class LauncherOrganizerLayoutTest {
    @Test public void preservedPageWhitespaceIsNotFilled() {
        List<int[]> positions = LauncherOrganizerLayout.place(4, 4, List.of(9, 5),
                List.of(new int[]{9, 0, 0, 1, 1}), List.of(new int[]{2, 2}), Set.of(9));
        assertArrayEquals(new int[]{5, 0, 0}, positions.get(0));
    }

    @Test public void allPagesProtectedCreatesANewPageWithoutReusingTheirHoles() {
        List<int[]> positions = LauncherOrganizerLayout.place(4, 4, List.of(9, 5),
                List.of(), List.of(new int[]{2, 2}, new int[]{1, 1}), Set.of(9, 5));
        assertArrayEquals(new int[]{10, 0, 0}, positions.get(0));
        assertArrayEquals(new int[]{10, 2, 0}, positions.get(1));
    }

    @Test public void placementUsesVisiblePageOrderInsteadOfSortingPageIds() {
        List<int[]> positions = LauncherOrganizerLayout.place(4, 4, List.of(9, 5),
                List.of(), List.of(new int[]{1, 1}));
        assertArrayEquals(new int[]{9, 0, 0}, positions.get(0));
    }

    @Test public void fixedAppFolderAndWidgetCellsRemainReserved() {
        List<int[]> reserved = List.of(new int[]{5, 0, 0, 2, 2}, new int[]{5, 2, 0, 1, 1}, new int[]{5, 0, 2, 4, 2});
        List<int[]> positions = LauncherOrganizerLayout.place(4, 4, List.of(5), reserved,
                List.of(new int[]{1, 1}, new int[]{2, 2}));
        assertArrayEquals(new int[]{5, 3, 0}, positions.get(0));
        assertArrayEquals(new int[]{6, 0, 0}, positions.get(1));
    }

    @Test public void rejectedOversizedFolderDoesNotAlterReservedRectangles() {
        int[] widget = {5, 0, 0, 2, 2};
        assertThrows(IllegalArgumentException.class, () -> LauncherOrganizerLayout.place(4, 4, List.of(5),
                List.of(widget), List.of(new int[]{5, 2}), Set.of()));
        assertArrayEquals(new int[]{5, 0, 0, 2, 2}, widget);
    }
}
