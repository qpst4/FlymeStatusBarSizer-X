package com.example.flymestatusbarsizer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/** Grid calculation without Android state; each rectangle is screen, x, y, width, height. */
final class LauncherOrganizerLayout {
    static final String[] FOLDER_TYPES = {
            "普通文件夹", "1×3 竖向大文件夹", "3×1 横向大文件夹", "3×3 大文件夹",
            "4×3 大文件夹", "6×3 大文件夹", "7×3 大文件夹"
    };

    // Flyme's preview grid differs from its occupied desktop cells (spanToConfig).
    static int[] folderSpan(int type) {
        return switch (type) {
            case 0 -> new int[]{1, 1};
            case 1 -> new int[]{1, 2};
            case 2 -> new int[]{2, 1};
            case 3 -> new int[]{2, 2};
            case 4 -> new int[]{3, 2};
            case 5 -> new int[]{4, 2};
            case 6 -> new int[]{5, 2};
            default -> throw new IllegalArgumentException("不支持的文件夹类型");
        };
    }

    // -1 means automatic; explicit choices keep their size as membership changes.
    static int folderType(int requested, int appCount, int columns, int rows) {
        if (requested != -1) folderSpan(requested);
        if (appCount <= 1) return 0;
        if (requested != -1) return requested;
        int type = appCount <= 3 ? 2 : appCount <= 9 ? 3 : appCount <= 12 ? 4 : appCount <= 18 ? 5 : 6;
        // Use the smallest large preview that fits the apps, capped by the desktop grid.
        for (; type > 0; type--) {
            int[] span = folderSpan(type);
            if (span[0] <= columns && span[1] <= rows) return type;
        }
        return 0;
    }

    static List<int[]> place(int columns, int rows, List<Integer> screens,
            List<int[]> reserved, List<int[]> sizes) {
        if (columns < 1 || columns > 20 || rows < 1 || rows > 30 || sizes.size() > 2000) {
            throw new IllegalArgumentException("桌面网格或应用数量不受支持");
        }
        for (int[] size : sizes) {
            if (size[0] < 1 || size[1] < 1 || size[0] > columns || size[1] > rows) {
                throw new IllegalArgumentException("所选文件夹超出当前桌面网格，请选择较小的类型");
            }
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
        while (result.size() < sizes.size()) {
            int[] size = sizes.get(result.size());
            boolean placed = false;
            for (int screen : pages.keySet()) {
                boolean[][] cells = pages.get(screen);
                for (int y = 0; y <= rows - size[1] && !placed; y++) {
                    for (int x = 0; x <= columns - size[0] && !placed; x++) {
                        boolean free = true;
                        for (int dx = 0; dx < size[0] && free; dx++) {
                            for (int dy = 0; dy < size[1]; dy++) {
                                if (cells[x + dx][y + dy]) { free = false; break; }
                            }
                        }
                        if (!free) continue;
                        for (int dx = 0; dx < size[0]; dx++) {
                            for (int dy = 0; dy < size[1]; dy++) cells[x + dx][y + dy] = true;
                        }
                        result.add(new int[]{screen, x, y});
                        placed = true;
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

    static List<String> validateMembership(List<String> expected, List<List<String>> groups) {
        Set<String> missing = new LinkedHashSet<>(expected);
        if (missing.size() != expected.size() || missing.isEmpty()) {
            throw new IllegalArgumentException("应用列表为空或重复");
        }
        for (List<String> group : groups) {
            if (group.isEmpty()) throw new IllegalArgumentException("分类中存在空文件夹");
            for (String id : group) {
                if (!missing.remove(id)) throw new IllegalArgumentException("分类包含重复或未知应用：" + id);
            }
        }
        return new ArrayList<>(missing);
    }
}
