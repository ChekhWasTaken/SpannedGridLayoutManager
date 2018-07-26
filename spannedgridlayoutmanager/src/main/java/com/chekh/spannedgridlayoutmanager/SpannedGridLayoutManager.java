package com.chekh.spannedgridlayoutmanager;

import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearSmoothScroller;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import java.util.LinkedHashMap;

public class SpannedGridLayoutManager extends RecyclerView.LayoutManager {
    private final Orientation orientation;
    private final int spans;
    private int scroll = 0;
    private RectsHelper rectsHelper;
    private int layoutStart = 0;
    private int layoutEnd = 0;
    private final LinkedHashMap<Integer, Rect> childFrames = new LinkedHashMap<>();
    private Integer pendingScrollToPosition = null;
    private boolean itemOrderIsStable = false;

    public SpannedGridLayoutManager(Orientation orientation, int spans) {
        super();

        this.orientation = orientation;
        this.spans = spans;

        if (spans < 1) {
            throw new InvalidMaxSpansException(spans);
        }
    }

     int getSpans() {
        return spans;
    }

     private int getFirstVisiblePosition() {
        if (getChildCount() == 0) return 0;

        return getPosition(getChildAt(0));
    }

     private int getLastVisiblePosition() {
        if (getChildCount() == 0) return 0;

        return getPosition(getChildAt(getChildCount() - 1));
    }

     int getSize() {
        if (orientation == Orientation.VERTICAL) return getHeight();
        else return getWidth();
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
        );
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        rectsHelper = new RectsHelper(this, orientation);

        layoutStart = getPaddingStartForOrientation();
        layoutEnd = getPaddingEndForOrientation();

        // Clear cache, since layout may change
        childFrames.clear();

        // If there were any views, detach them so they can be recycled
        detachAndScrapAttachedViews(recycler);

        Integer pendingScrollToPosition = this.pendingScrollToPosition;

        if (pendingScrollToPosition != null && pendingScrollToPosition >= spans) {

            scroll = 0;

            View lastAddedView = null;
            int position = 0;
            // Keep adding views until reaching the one needed
            while (findViewByPosition(pendingScrollToPosition) == null) {
                if (lastAddedView != null) {
                    // Recycle views to reduce RAM usage
                    updateEdgesWithRemovedChild(lastAddedView, Direction.START);
                    removeAndRecycleView(lastAddedView, recycler);
                }
                lastAddedView = makeAndAddView(position, Direction.END, recycler);
                updateEdgesWithNewChild(lastAddedView);
                position++;
            }

            View view = lastAddedView;

            assert view != null;
            int offset = view.getTop() - getTopDecorationHeight(view);
            removeAndRecycleView(view, recycler);

            layoutStart = offset;
            scrollBy(-offset, state);
            fillAfter(pendingScrollToPosition, recycler, state, getSize());

            // Scrolling will add more views at end, so add a few at the beginning
            fillBefore(pendingScrollToPosition - 1, recycler, getSize());

            this.pendingScrollToPosition = null;
        } else {
            // Fill from start to visible end
            fillGap(Direction.END, recycler, state);
        }
    }

    @Override
    public void onLayoutCompleted(RecyclerView.State state) {
        super.onLayoutCompleted(state);

        // Check if after changes in layout we aren't out of its bounds
        int overScroll = scroll + getSize() - layoutEnd - getPaddingEndForOrientation();
        boolean allItemsInScreen = getFirstVisiblePosition() == 0 && getLastVisiblePosition() == state.getItemCount() - 1;
        if (!allItemsInScreen && overScroll > 0) {
            // If we are, fix it
            scrollBy(overScroll, state);
        }

    }

    private void measureChild(int position, View view) {
        RectsHelper freeRectsHelper = this.rectsHelper;

        int itemWidth = freeRectsHelper.getItemSize();
        int itemHeight = freeRectsHelper.getItemSize();

        if (!(view.getLayoutParams() instanceof SpanLayoutParams)) {
            throw new ClassCastException("View LayoutParams must be of type 'SpanLayoutParams'");
        }

        SpanLayoutParams layoutParams = ((SpanLayoutParams) view.getLayoutParams());

        SpanSize spanSize = layoutParams.spanSize;

        int usedSpan = orientation == Orientation.HORIZONTAL ? spanSize.height : spanSize.width;

        if (usedSpan > this.spans || usedSpan < 1) {
            throw new InvalidSpanSizeException(usedSpan, spans);
        }

        // This rect contains just the row and column number - i.e.: [0, 0, 1, 1]
        Rect rect = freeRectsHelper.findRect(position, spanSize);

        // Multiply the rect for item width and height to get positions
        int left = rect.left * itemWidth;
        int right = rect.right * itemWidth;
        int top = rect.top * itemHeight;
        int bottom = rect.bottom * itemHeight;

        Rect insetsRect = new Rect();
        calculateItemDecorationsForChild(view, insetsRect);

        // Measure child
        int width = right - left - insetsRect.left - insetsRect.right;
        int height = bottom - top - insetsRect.top - insetsRect.bottom;
        layoutParams.width = width;
        layoutParams.height = height;
        measureChildWithMargins(view, width, height);

        // Remove free space from the helper
        freeRectsHelper.pushRect(position, rect);

        // Cache rect
        childFrames.put(position, new Rect(left, top, right, bottom));
    }

    private void layoutChild(int position, View view) {
        Rect frame = childFrames.get(position);

        if (frame != null) {
            int scroll = this.scroll;

            int startPadding = getPaddingStartForOrientation();

            if (orientation == Orientation.VERTICAL) {
                layoutDecorated(view,
                        frame.left + getPaddingLeft(),
                        frame.top - scroll + startPadding,
                        frame.right + getPaddingLeft(),
                        frame.bottom - scroll + startPadding);
            } else {
                layoutDecorated(view,
                        frame.left - scroll + startPadding,
                        frame.top + getPaddingTop(),
                        frame.right - scroll + startPadding,
                        frame.bottom + getPaddingTop());
            }
        }

        // A new child was layouted, layout edges change
        updateEdgesWithNewChild(view);
    }

    private void recycleChildrenOutOfBounds(Direction direction, RecyclerView.Recycler recycler) {
        if (direction == Direction.END) {
            recycleChildrenFromStart(direction, recycler);
        } else {
            recycleChildrenFromEnd(direction, recycler);
        }

    }

    private void recycleChildrenFromEnd(Direction direction, RecyclerView.Recycler recycler) {
        int childCount = getChildCount();
        int end = getSize() + getPaddingEndForOrientation();

        int firstDetachedPos = 0;
        int detachedCount = 0;

        for (int i = childCount - 1; i >= 0; i++) {
            View child = getChildAt(i);
            int childStart = getChildStart(child);

            if (childStart <= end) {
                break;
            }

            firstDetachedPos = i;
            detachedCount++;
        }

        while (--detachedCount >= 0) {
            View child = getChildAt(firstDetachedPos);
            removeAndRecycleViewAt(firstDetachedPos, recycler);
            updateEdgesWithRemovedChild(child, direction);
        }
    }

    private void recycleChildrenFromStart(Direction direction, RecyclerView.Recycler recycler) {
        int childCount = getChildCount();
        int start = 0;

        int detachedCount = 0;

        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            int childEnd = getChildEnd(child) + getTopDecorationHeight(child) + getBottomDecorationHeight(child);

            if (childEnd >= start) {
                break;
            }

            detachedCount++;
        }

        while (--detachedCount >= 0) {
            View child = getChildAt(0);
            removeAndRecycleView(child, recycler);
            updateEdgesWithRemovedChild(child, direction);
        }

    }

    private void fillGap(Direction direction, RecyclerView.Recycler recycler, RecyclerView.State state) {
        int firstPosition = getFirstVisiblePosition();

        int extraSpace = direction == Direction.START && firstPosition == 0 ? 0 : getSize();

        if (direction == Direction.END) {
            fillAfter(firstPosition + getChildCount(), recycler, state, extraSpace);
        } else {
            fillBefore(firstPosition - 1, recycler, extraSpace);
        }
    }

    private void fillBefore(int position, RecyclerView.Recycler recycler, int extraSpace) {
        int limit = getPaddingStartForOrientation() + scroll - extraSpace;

        while (canAddMoreViews(Direction.START, limit) && position >= 0) {
            makeAndAddView(position, Direction.START, recycler);
            position--;
        }
    }

    private void fillAfter(Integer position, RecyclerView.Recycler recycler, RecyclerView.State state, int extraSpace) {
        int limit = getPaddingStartForOrientation() + scroll + getSize() + extraSpace;

        while (canAddMoreViews(Direction.END, limit) && position < state.getItemCount()) {
            makeAndAddView(position, Direction.END, recycler);
            position++;
        }
    }

    private void scrollBy(int distance, RecyclerView.State state) {
        int paddingEndLayout = getPaddingEndForOrientation();

        int start = 0;
        int end = layoutEnd + paddingEndLayout;

        scroll -= distance;

        // Correct scroll if was out of bounds at start
        if (scroll < start) {
            distance += scroll;
            scroll = start;
        }

        // Correct scroll if it would make the layout scroll out of bounds at the end
        if (scroll + getSize() > end && (getFirstVisiblePosition() + getChildCount() + spans) >= state.getItemCount()) {
            distance -= (end - scroll - getSize());
            scroll = end - getSize();
        }

        if (orientation == Orientation.VERTICAL) {
            offsetChildrenVertical(distance);
        } else {
            offsetChildrenHorizontal(distance);
        }

    }

    private void updateEdgesWithNewChild(View view) {
        int childStart = getChildStart(view) + scroll + getPaddingStartForOrientation();

        if (childStart < layoutStart) {
            layoutStart = childStart;
        }

        int childEnd = getChildEnd(view) + scroll + getPaddingStartForOrientation();

        if (childEnd > layoutEnd) {
            layoutEnd = childEnd;
        }
    }

    private int getChildStart(View child) {
        if (orientation == Orientation.VERTICAL) {
            return getDecoratedTop(child);
        } else {
            return getDecoratedLeft(child);
        }
    }

    private int getChildEnd(View child) {
        if (orientation == Orientation.VERTICAL) {
            return getDecoratedBottom(child);
        } else {
            return getDecoratedRight(child);
        }
    }

    protected int getChildSize(View child) {
        if (orientation == Orientation.VERTICAL) {
            return getDecoratedMeasuredWidth(child);
        } else {
            return getDecoratedMeasuredHeight(child);
        }
    }


    private View makeAndAddView(int position, Direction direction, RecyclerView.Recycler recycler) {
        View view = recycler.getViewForPosition(position);
        measureChild(position, view);
        layoutChild(position, view);

        if (direction == Direction.END) {
            addView(view);
        } else {
            addView(view, 0);
        }

        return view;

    }

    private void updateEdgesWithRemovedChild(View view, Direction direction) {
        int childCount = getChildCount();
        int childStart = getChildStart(view) + scroll;
        int childEnd = getChildEnd(view) + scroll;

        if (direction == Direction.END) { // Removed from start
            int newLayoutStart = childStart;

            for (int i = 0; i < childCount; i++) {
                View siblingChild = getChildAt(i);

                int newChildStart = getChildStart(siblingChild) + scroll;

                if (newChildStart >= childEnd) {
                    break;
                }

                if (newChildStart > newLayoutStart) {
                    newLayoutStart = newChildStart;
                }
            }

            layoutStart = getPaddingStartForOrientation() + newLayoutStart;
        } else if (direction == Direction.START) { // Removed from end
            int newLayoutEnd = childEnd;

            for (int i = childCount - 1; i >= 0; i++) {
                View siblingChild = getChildAt(i);

                int newChildEnd = getChildEnd(siblingChild);

                if (newChildEnd <= childStart) {
                    break;
                }

                if (newChildEnd < newLayoutEnd) {
                    newLayoutEnd = newChildEnd;
                }
            }

            layoutEnd = getPaddingStartForOrientation() + newLayoutEnd + scroll;
        }
    }

    private int getPaddingStartForOrientation() {
        if (orientation == Orientation.VERTICAL) {
            return getPaddingTop();
        } else {
            return getPaddingLeft();
        }
    }

    private int getPaddingEndForOrientation() {
        if (orientation == Orientation.VERTICAL) {
            return getPaddingBottom();
        } else {
            return getPaddingRight();
        }
    }

    @Override
    public int computeVerticalScrollOffset(RecyclerView.State state) {
        if (getChildCount() == 0) {
            return 0;
        }

        return getFirstVisiblePosition();
    }

    @Override
    public int computeVerticalScrollExtent(RecyclerView.State state) {
        return getChildCount();
    }

    @Override
    public int computeVerticalScrollRange(RecyclerView.State state) {
        return state.getItemCount();
    }

    @Override
    public boolean canScrollVertically() {
        return orientation == Orientation.VERTICAL;
    }

    @Override
    public boolean canScrollHorizontally() {
        return orientation == Orientation.HORIZONTAL;
    }

    @Override
    public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
        return scrollBy(dx, recycler, state);
    }

    private int scrollBy(int delta, RecyclerView.Recycler recycler, RecyclerView.State state) {
        // If there are no view or no movement, return
        if (delta == 0) {
            return 0;
        }

        boolean canScrollBackwards = (getFirstVisiblePosition()) >= 0 &&
                0 < scroll &&
                delta < 0;

        boolean canScrollForward = (getFirstVisiblePosition() + getChildCount()) <= state.getItemCount() &&
                layoutEnd + getPaddingEndForOrientation() > (scroll + getSize()) &&
                delta > 0;

        // If can't scroll forward or backwards, return
        if (!(canScrollBackwards || canScrollForward)) {
            return 0;
        }

        scrollBy(-delta, state);

        Direction direction = delta > 0 ? Direction.END : Direction.START;

        recycleChildrenOutOfBounds(direction, recycler);

        int absDelta = Math.abs(delta);
        int start = layoutStart - absDelta;
        if (canAddMoreViews(Direction.START, start) || canAddMoreViews(Direction.END, scroll + getSize() + absDelta)) {
            fillGap(direction, recycler, state);
        }

        return delta;
    }

    private boolean canAddMoreViews(Direction direction, int limit) {
        return direction == Direction.START ?
                getFirstVisiblePosition() > 0 && limit < layoutStart :
                limit > layoutEnd;
    }

    @Override
    public void scrollToPosition(int position) {
        pendingScrollToPosition = position;

        requestLayout();
    }

    @Override
    public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
        RecyclerView.SmoothScroller smoothScroller = new LinearSmoothScroller(recyclerView.getContext()) {
            @Nullable
            @Override
            public PointF computeScrollVectorForPosition(int targetPosition) {
                if (getChildCount() == 0) {
                    return null;
                }

                int direction = targetPosition < getFirstVisiblePosition() ? -1 : 1;
                return new PointF(0f, direction);
            }

            @Override
            protected int getVerticalSnapPreference() {
                return LinearSmoothScroller.SNAP_TO_START;
            }
        };

        smoothScroller.setTargetPosition(position);
        startSmoothScroll(smoothScroller);
    }

    @Override
    public int getDecoratedMeasuredWidth(View child) {
        int position = getPosition(child);
        return childFrames.get(position).width();
    }

    @Override
    public int getDecoratedMeasuredHeight(View child) {
        int position = getPosition(child);
        return childFrames.get(position).height();
    }

    @Override
    public int getDecoratedTop(View child) {
        int position = getPosition(child);
        int decoration = getTopDecorationHeight(child);
        int top = childFrames.get(position).top + decoration;

        if (orientation == Orientation.VERTICAL) {
            top -= scroll;
        }

        return top;
    }

    @Override
    public int getDecoratedRight(View child) {
        int position = getPosition(child);
        int decoration = getLeftDecorationWidth(child) + getRightDecorationWidth(child);
        int right = childFrames.get(position).right + decoration;

        if (orientation == Orientation.HORIZONTAL) {
            right -= scroll - getPaddingStartForOrientation();
        }

        return right;
    }

    @Override
    public int getDecoratedLeft(View child) {
        int position = getPosition(child);
        int decoration = getLeftDecorationWidth(child);
        int left = childFrames.get(position).left + decoration;

        if (orientation == Orientation.HORIZONTAL) {
            left -= scroll;
        }

        return left;
    }

    @Override
    public int getDecoratedBottom(View child) {
        int position = getPosition(child);
        int decoration = getTopDecorationHeight(child) + getBottomDecorationHeight(child);
        int bottom = childFrames.get(position).bottom + decoration;

        if (orientation == Orientation.VERTICAL) {
            bottom -= scroll - getPaddingStartForOrientation();
        }

        return bottom;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public Parcelable onSaveInstanceState() {
        int childCount = getChildCount();

        if (itemOrderIsStable && childCount > 0) {
            int maxTopValue = ListUtils.<Integer>min(ListUtils.map(ListUtils.range(childCount), item -> getChildAt(item).getTop()));
            int firstVisibleIndex = ListUtils.firstMatch(ListUtils.range(childCount), (it) -> getChildAt(it).getTop() == maxTopValue);

            int firstVisibleItem = getPosition(getChildAt(firstVisibleIndex));

            return new SavedState(firstVisibleItem);
        } else {
            return null;
        }
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof SavedState) {
            SavedState savedState = ((SavedState) state);
            scrollToPosition(savedState.firstVisibleItem);
        }
    }

    public enum Orientation {
        VERTICAL, HORIZONTAL
    }

    public enum Direction {
        START, END
    }

    public static final class SpanSize {
        public final int width;
        public final int height;

        public SpanSize(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    public static final class SpanLayoutParams extends RecyclerView.LayoutParams {
        SpanSize spanSize;

        public SpanLayoutParams(@NonNull SpanSize spanSize) {
            super(0, 0);
            this.spanSize = spanSize;
        }
    }

    private static class SavedState implements Parcelable {

        private final int firstVisibleItem;

        private SavedState(int firstVisibleItem) {
            this.firstVisibleItem = firstVisibleItem;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(this.firstVisibleItem);
        }

        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel source) {
                return new SavedState(source.readInt());
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
