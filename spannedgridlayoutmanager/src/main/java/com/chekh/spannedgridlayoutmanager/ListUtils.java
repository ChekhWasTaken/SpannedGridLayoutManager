package com.chekh.spannedgridlayoutmanager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class ListUtils {
    public static <T> T first(List<T> list) {
        return list.get(0);
    }

    public static <T> T last(List<T> list) {
        return list.get(list.size() - 1);
    }

    public static <T> T firstMatch(List<T> list, Predicate<T> predicate) {
        for (T it : list) {
            if (predicate.predicate(it)) return it;
        }

        throw new NoSuchElementException("Collection contains no element matching the predicate.");
    }

    public static <T> T firstMatchOrNull(List<T> list, Predicate<T> predicate) {
        for (T it : list) {
            if (predicate.predicate(it)) return it;
        }

        return null;
    }

    public static <T> List<T> filter(List<T> list, Predicate<T> predicate) {
        List<T> filtered = new ArrayList<>();

        for (T it : list) {
            if (predicate.predicate(it)) filtered.add(it);
        }

        return filtered;
    }

    public static <T, R> List<R> map(List<T> list, Transform<T, R> transform) {
        List<R> destination = new ArrayList<>();

        for (T it : list) {
            destination.add(transform.transform(it));
        }

        return destination;
    }

    public static <T extends Comparable<T>> T min(List<T> list) {
        Iterator<T> iterator = list.iterator();

        if (!iterator.hasNext()) return null;

        T min = iterator.next();

        while (iterator.hasNext()) {
            T e = iterator.next();
            if (min.compareTo(e) > 0) min = e;
        }

        return min;
    }

    public static List<Integer> range(int to) {
        List<Integer> list = new ArrayList<>(to);

        for (int i = 0; i < to; i++) {
            list.add(i);
        }

        return list;
    }

    public interface Predicate<T> {
        boolean predicate(T item);
    }

    public interface Transform<T, R> {
        R transform(T item);
    }
}
