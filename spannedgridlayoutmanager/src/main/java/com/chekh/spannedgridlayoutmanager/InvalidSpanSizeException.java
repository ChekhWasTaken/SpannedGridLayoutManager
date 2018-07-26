package com.chekh.spannedgridlayoutmanager;

class InvalidSpanSizeException extends RuntimeException {
    InvalidSpanSizeException(int errorSize, int maxSpanSize) {
        super("Invalid item span size: " + errorSize + ". Span size must be in the range: (1..." + maxSpanSize + ")");
    }
}
