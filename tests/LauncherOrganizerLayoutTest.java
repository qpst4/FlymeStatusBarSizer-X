package com.example.flymestatusbarsizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Run with javac/java; no Android runtime or test dependencies. */
public final class LauncherOrganizerLayoutTest {
    public static void main(String[] args) {
        List<int[]> positions = LauncherOrganizerLayout.place(2, 2, List.of(0),
                List.of(new int[]{0, 0, 0, 2, 1}), Collections.nCopies(3, new int[]{1, 1}));
        check(Arrays.equals(positions.get(0), new int[]{0, 0, 1}), "widget space must remain occupied");
        check(Arrays.equals(positions.get(2), new int[]{1, 0, 0}), "overflow must create a page");
        positions = LauncherOrganizerLayout.place(1, 1, List.of(0, 3), List.of(), Collections.nCopies(3, new int[]{1, 1}));
        check(positions.get(1)[0] == 3 && positions.get(2)[0] == 4, "keep existing page IDs");
        check(LauncherOrganizerLayout.place(4, 6, List.of(), List.of(), List.of()).isEmpty(), "zero items");

        int[][] spans = {{1, 1}, {1, 2}, {2, 1}, {2, 2}, {3, 2}, {4, 2}, {5, 2}};
        for (int type = 0; type < spans.length; type++) {
            check(Arrays.equals(LauncherOrganizerLayout.folderSpan(type), spans[type]), "native Flyme folder span " + type);
        }
        positions = LauncherOrganizerLayout.place(4, 3, List.of(0), List.of(new int[]{0, 0, 0, 4, 1}),
                List.of(LauncherOrganizerLayout.folderSpan(5), new int[]{1, 1}, LauncherOrganizerLayout.folderSpan(1)));
        check(Arrays.equals(positions.get(0), new int[]{0, 0, 1}), "6x3 preview occupies 4x2 cells below widget");
        check(Arrays.equals(positions.get(1), new int[]{1, 0, 0}), "unclassified icon moves to next screen");
        check(Arrays.equals(positions.get(2), new int[]{1, 1, 0}), "vertical folder avoids the unclassified icon");
        positions = LauncherOrganizerLayout.place(3, 2, List.of(0),
                List.of(new int[]{0, 1, 0, 1, 1}), List.of(new int[]{2, 2}, new int[]{1, 1}));
        check(positions.get(0)[0] == 1 && positions.get(1)[0] == 0, "fragmented space cannot fit a folder but can fit an icon");

        Random random = new Random(20260929);
        for (int iteration = 0; iteration < 1000; iteration++) {
            int columns = 1 + random.nextInt(6);
            int rows = 1 + random.nextInt(8);
            List<int[]> reserved = new ArrayList<>();
            Set<String> occupied = new HashSet<>();
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < columns; x++) {
                    if (random.nextBoolean()) {
                        reserved.add(new int[]{0, x, y, 1, 1});
                        occupied.add("0:" + x + ":" + y);
                    }
                }
            }
            int count = 1 + random.nextInt(100);
            List<int[]> sizes = new ArrayList<>();
            for (int i = 0; i < count; i++) sizes.add(new int[]{1 + random.nextInt(columns), 1 + random.nextInt(rows)});
            positions = LauncherOrganizerLayout.place(columns, rows, List.of(0), reserved, sizes);
            check(positions.size() == count, "no omitted placements");
            for (int i = 0; i < count; i++) {
                int[] cell = positions.get(i);
                int[] size = sizes.get(i);
                check(cell[0] >= 0 && cell[1] >= 0 && cell[1] + size[0] <= columns
                        && cell[2] >= 0 && cell[2] + size[1] <= rows, "entire folder fits the grid");
                for (int x = cell[1]; x < cell[1] + size[0]; x++) {
                    for (int y = cell[2]; y < cell[2] + size[1]; y++) {
                        check(occupied.add(cell[0] + ":" + x + ":" + y), "no widget, folder or app overlap");
                    }
                }
            }
        }
        check(LauncherOrganizerLayout.validateMembership(List.of("a", "b", "clone"),
                List.of(List.of("clone", "a"), List.of("b"))).isEmpty(), "all apps classified");
        check(LauncherOrganizerLayout.validateMembership(List.of("a", "b", "clone"),
                List.of(List.of("b"))).equals(List.of("a", "clone")), "unclassified apps keep desktop order");
        check(LauncherOrganizerLayout.validateMembership(List.of("a", "b"), List.of())
                .equals(List.of("a", "b")), "all apps may remain unclassified");
        rejects(() -> LauncherOrganizerLayout.validateMembership(List.of(), List.of()));
        rejects(() -> LauncherOrganizerLayout.validateMembership(List.of("a", "a"), List.of()));
        rejects(() -> LauncherOrganizerLayout.validateMembership(List.of("a", "b"), List.of(List.of("a", "b", "a"))));
        rejects(() -> LauncherOrganizerLayout.validateMembership(List.of("a"), List.of(List.of("unknown"))));
        rejects(() -> LauncherOrganizerLayout.validateMembership(List.of("a"), List.of(List.of(), List.of("a"))));
        rejects(() -> LauncherOrganizerLayout.folderSpan(-1));
        rejects(() -> LauncherOrganizerLayout.folderSpan(7));
        rejects(() -> LauncherOrganizerLayout.place(0, 6, List.of(), List.of(), List.of(new int[]{1, 1})));
        rejects(() -> LauncherOrganizerLayout.place(4, 6, List.of(0), List.of(new int[]{0, 3, 0, 2, 1}), List.of(new int[]{1, 1})));
        rejects(() -> LauncherOrganizerLayout.place(3, 6, List.of(0), List.of(), List.of(LauncherOrganizerLayout.folderSpan(5))));
        rejects(() -> LauncherOrganizerLayout.place(4, 1, List.of(0), List.of(), List.of(LauncherOrganizerLayout.folderSpan(1))));
        rejects(() -> LauncherOrganizerLayout.place(4, 6, List.of(0), List.of(), List.of(new int[]{0, 1})));
        System.out.println("Desktop organization layout and membership checks passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void rejects(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected rejection");
    }
}
