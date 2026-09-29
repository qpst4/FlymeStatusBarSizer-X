package com.example.flymestatusbarsizer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/** Grid calculation without Android state; each rectangle is screen, x, y, width, height. */
final class LauncherOrganizerLayout {
    static List<int[]> place(int columns, int rows, List<Integer> screens,
            List<int[]> reserved, int count) {
        if (columns < 1 || columns > 20 || rows < 1 || rows > 30 || count < 0 || count > 2000) {
            throw new IllegalArgumentException("桌面网格或应用数量不受支持");
        }
        TreeMap<Integer, boolean[][]> pages = new TreeMap<>();
        for (int screen : screens) {
            if (screen >= 0 && screen < 100_000_000) pages.put(screen, new boolean[columns][rows]);
        }
        if (pages.isEmpty()) pages.put(0, new boolean[columns][rows]);
        for (int[] rect : reserved) {
            boolean[][] cells = pages.computeIfAbsent(rect[0], ignored -> new boolean[columns][rows]);
            if (rect[1] < 0 || rect[2] < 0 || rect[3] < 1 || rect[4] < 1
                    || rect[1] + rect[3] > columns || rect[2] + rect[4] > rows) {
                throw new IllegalArgumentException("已有图标位置超出网格，请先重新加载桌面");
            }
            for (int x = rect[1]; x < rect[1] + rect[3]; x++) {
                for (int y = rect[2]; y < rect[2] + rect[4]; y++) cells[x][y] = true;
            }
        }
        List<int[]> result = new ArrayList<>();
        while (result.size() < count) {
            boolean placed = false;
            for (int screen : pages.keySet()) {
                boolean[][] cells = pages.get(screen);
                for (int y = 0; y < rows && !placed; y++) {
                    for (int x = 0; x < columns && !placed; x++) {
                        if (!cells[x][y]) {
                            cells[x][y] = true;
                            result.add(new int[]{screen, x, y});
                            placed = true;
                        }
                    }
                }
                if (placed) break;
            }
            if (!placed) {
                int screen = pages.lastKey() + 1;
                if (screen >= 100_000_000) throw new IllegalArgumentException("无法新增桌面页面");
                pages.put(screen, new boolean[columns][rows]);
            }
        }
        return result;
    }

    static void validateMembership(List<String> expected, List<List<String>> groups) {
        Set<String> missing = new HashSet<>(expected);
        if (missing.size() != expected.size() || missing.isEmpty() || groups.isEmpty()) {
            throw new IllegalArgumentException("应用列表或分类为空、重复");
        }
        for (List<String> group : groups) {
            if (group.isEmpty()) throw new IllegalArgumentException("分类中存在空文件夹");
            for (String id : group) {
                if (!missing.remove(id)) throw new IllegalArgumentException("分类包含重复或未知应用：" + id);
            }
        }
        if (!missing.isEmpty()) throw new IllegalArgumentException("分类遗漏了 " + missing.size() + " 个应用，请重新生成");
    }
}
