package com.chekh.spannedgridlayoutmanager;

import android.graphics.Rect;
import android.support.annotation.NonNull;

public class RectUtils {
    public static boolean isAdjacentTo(@NonNull Rect rect, Rect other) {
        return (other.right == rect.left
                || other.top == rect.bottom
                || other.left == rect.right
                || other.bottom == rect.top);
    }

    public static boolean intersects(@NonNull Rect rect, Rect other) {
        return rect.intersects(other.left, other.top, other.right, other.bottom);
    }
}
