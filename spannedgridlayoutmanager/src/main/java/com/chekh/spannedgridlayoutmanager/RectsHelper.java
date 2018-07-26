package com.chekh.spannedgridlayoutmanager;

import android.graphics.Rect;

import com.chekh.spannedgridlayoutmanager.SpannedGridLayoutManager.SpanSize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

class RectsHelper {
    private final Comparator<Rect> rectComparator = new Comparator<Rect>() {
        @Override
        public int compare(Rect rect1, Rect rect2) {
            if (orientation == SpannedGridLayoutManager.Orientation.VERTICAL) {
                if (rect1.top == rect2.top) {
                    if (rect1.left < rect2.left) {
                        return -1;
                    } else {
                        return 1;
                    }
                } else {
                    if (rect1.top < rect2.top) {
                        return -1;
                    } else {
                        return 1;
                    }
                }
            } else if (orientation == SpannedGridLayoutManager.Orientation.HORIZONTAL) {
                if (rect1.left == rect2.left) {
                    if (rect1.top < rect2.top) {
                        return -1;
                    } else {
                        return 1;
                    }
                } else {
                    if (rect1.left < rect2.left) {
                        return -1;
                    } else {
                        return 1;
                    }
                }

            }

            return 0;
        }
    };

    private final LinkedHashMap<Integer, Rect> rectsCache = new LinkedHashMap<>();
    private final List<Rect> freeRects = new ArrayList<>();


    private final SpannedGridLayoutManager layoutManager;
    private final SpannedGridLayoutManager.Orientation orientation;

    RectsHelper(SpannedGridLayoutManager layoutManager, SpannedGridLayoutManager.Orientation orientation) {
        this.layoutManager = layoutManager;
        this.orientation = orientation;

        final Rect initialFreeRect;
        if (orientation == SpannedGridLayoutManager.Orientation.VERTICAL) {
            initialFreeRect = new Rect(0, 0, layoutManager.getSpans(), Integer.MAX_VALUE);
        } else {
            initialFreeRect = new Rect(0, 0, Integer.MAX_VALUE, layoutManager.getSpans());
        }

        freeRects.add(initialFreeRect);
    }

    private int getSize() {
        if (orientation == SpannedGridLayoutManager.Orientation.VERTICAL) {
            return layoutManager.getWidth() - layoutManager.getPaddingLeft() - layoutManager.getPaddingRight();
        } else {
            return layoutManager.getHeight() - layoutManager.getPaddingTop() - layoutManager.getPaddingBottom();
        }
    }

    int getItemSize() {
        return getSize() / layoutManager.getSpans();
    }

    private int getStart() {
        if (orientation == SpannedGridLayoutManager.Orientation.VERTICAL) {
            return ListUtils.first(freeRects).top * getItemSize();
        } else {
            return ListUtils.first(freeRects).left * getItemSize();
        }
    }

    private int getEnd() {
        if (orientation == SpannedGridLayoutManager.Orientation.VERTICAL) {
            return (ListUtils.last(freeRects).top + 1) * getItemSize();
        } else {
            return (ListUtils.last(freeRects).left + 1) * getItemSize();
        }
    }

    Rect findRect(int position, SpanSize spanSize) {
        Rect rect = rectsCache.get(position);

        if (rect == null) {
            rect = findRectForSpanSize(spanSize);
        }

        return rect;
    }

    private Rect findRectForSpanSize(SpanSize spanSize) {
        Rect lane = ListUtils.firstMatch(freeRects, it -> {
            Rect itemRect = new Rect(it.left, it.top, it.left + spanSize.width, it.top + spanSize.height);
            return it.contains(itemRect);
        });

        return new Rect(lane.left, lane.top, lane.left + spanSize.width, lane.top + spanSize.height);
    }

    void pushRect(int position, Rect rect) {
        rectsCache.put(position, rect);
        subtract(rect);
    }

    private void subtract(Rect subtractedRect) {
        List<Rect> interestingRects = ListUtils.filter(freeRects, (it) -> RectUtils.isAdjacentTo(it, subtractedRect) || RectUtils.intersects(it, subtractedRect));

        List<Rect> possibleNewRects = new ArrayList<>();
        List<Rect> adjacentRects = new ArrayList<>();

        for (Rect free : interestingRects) {
            if (RectUtils.isAdjacentTo(free, subtractedRect) && !subtractedRect.contains(free)) {
                adjacentRects.add(free);
            } else {
                freeRects.remove(free);

                if (free.left < subtractedRect.left) { // Left
                    possibleNewRects.add(new Rect(free.left, free.top, subtractedRect.left, free.bottom));
                }

                if (free.right > subtractedRect.right) { // Right
                    possibleNewRects.add(new Rect(subtractedRect.right, free.top, free.right, free.bottom));
                }

                if (free.top < subtractedRect.top) { // Top
                    possibleNewRects.add(new Rect(free.left, free.top, free.right, subtractedRect.top));
                }

                if (free.bottom > subtractedRect.bottom) { // Bottom
                    possibleNewRects.add(new Rect(free.left, subtractedRect.bottom, free.right, free.bottom));
                }
            }
        }

        for (Rect rect : possibleNewRects) {
            boolean isAdjacent = ListUtils.firstMatchOrNull(adjacentRects, it -> it != rect && it.contains(rect)) != null;
            if (isAdjacent) continue;

            boolean isContained = ListUtils.firstMatchOrNull(possibleNewRects, (it) -> it != rect && it.contains(rect)) != null;
            if (isContained) continue;

            freeRects.add(rect);
        }

        Collections.sort(freeRects, rectComparator);
    }
}
