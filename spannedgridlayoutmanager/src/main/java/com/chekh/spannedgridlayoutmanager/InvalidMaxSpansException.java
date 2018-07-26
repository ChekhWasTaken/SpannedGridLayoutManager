package com.chekh.spannedgridlayoutmanager;

class InvalidMaxSpansException extends RuntimeException {
    InvalidMaxSpansException(int maxSpanSize) {
        super("Invalid layout spans: " + maxSpanSize + ". Span size must be at least 1.");
    }
}
