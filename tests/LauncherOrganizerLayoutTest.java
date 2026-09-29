package com.example.flymestatusbarsizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Run with javac/java; no Android runtime or test dependencies. */
public final class LauncherOrganizerLayoutTest {
    public static void main(String[] args) {
        List<int[]> positions = LauncherOrganizerLayout.place(2, 2, List.of(0),
                List.of(new int[]{0, 0, 0, 2, 1}), 3);
        check(Arrays.equals(positions.get(0), new int[]{0, 0, 1}), "widget space must remain occupied");
        check(Arrays.equals(positions.get(2), new int[]{1, 0, 0}), "overflow must create a page");
        positions = LauncherOrganizerLayout.place(1, 1, List.of(0, 3), List.of(), 3);
        check(positions.get(1)[0] == 3 && positions.get(2)[0] == 4, "keep existing page IDs");
        check(LauncherOrganizerLayout.place(4, 6, List.of(), List.of(), 0).isEmpty(), "zero items");

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
            positions = LauncherOrganizerLayout.place(columns, rows, List.of(0), reserved, count);
            check(positions.size() == count, "no omitted placements");
            for (int[] cell : positions) {
                check(cell[0] >= 0 && cell[1] >= 0 && cell[1] < columns && cell[2] >= 0 && cell[2] < rows, "valid cell");
                check(occupied.add(cell[0] + ":" + cell[1] + ":" + cell[2]), "no widget or app overlap");
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
        rejects(() -> LauncherOrganizerLayout.place(0, 6, List.of(), List.of(), 1));
        rejects(() -> LauncherOrganizerLayout.place(4, 6, List.of(0), List.of(new int[]{0, 3, 0, 2, 1}), 1));
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
