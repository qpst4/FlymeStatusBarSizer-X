package com.example.flymestatusbarsizer;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Positions children by desktop cells, including empty cells and multi-cell folders/widgets. */
final class LauncherOrganizerDesktopGrid extends ViewGroup {
    private final int columns;
    private final int rows;
    private int cellHeight;

    LauncherOrganizerDesktopGrid(Context context, int columns, int rows) {
        super(context);
        this.columns = Math.max(1, columns);
        this.rows = Math.max(1, rows);
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
    }

    void addItem(View view, int x, int y, int width, int height) {
        if (x < 0 || y < 0 || width < 1 || height < 1 || x + width > columns || y + height > rows) return;
        addView(view, new Cell(x, y, width, height));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        cellHeight = Math.max(PageViewUtils.dp(getContext(), 96), width / columns);
        setMeasuredDimension(width, cellHeight * rows);
        int gap = PageViewUtils.dp(getContext(), 2);
        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            Cell cell = (Cell) view.getLayoutParams();
            int itemWidth = (cell.x + cell.w) * width / columns - cell.x * width / columns;
            view.measure(MeasureSpec.makeMeasureSpec(Math.max(0, itemWidth - gap * 2), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.max(0, cell.h * cellHeight - gap * 2), MeasureSpec.EXACTLY));
        }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int gap = PageViewUtils.dp(getContext(), 2);
        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            Cell cell = (Cell) view.getLayoutParams();
            int x = cell.x * getWidth() / columns + gap;
            int y = cell.y * cellHeight + gap;
            view.layout(x, y, x + view.getMeasuredWidth(), y + view.getMeasuredHeight());
        }
    }

    private static final class Cell extends LayoutParams {
        final int x, y, w, h;
        Cell(int x, int y, int w, int h) {
            super(MATCH_PARENT, MATCH_PARENT);
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }
}
