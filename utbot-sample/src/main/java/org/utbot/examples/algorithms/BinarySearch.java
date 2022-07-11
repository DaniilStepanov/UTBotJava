package org.utbot.examples.algorithms;

import java.util.ArrayList;

public class BinarySearch {

    public boolean testFunc(ArrayList<ArrayList<Long>> array) {
        for (int i = 0; i < array.size() - 1; i++) {
            if (array.get(i).get(i) > array.get(i + 1).get(i + 1)) {
                return true;
            }
        }
        return false;
    }

    public boolean isUnsorted(long[] array) {
        for (int i = 0; i < array.length - 1; i++) {
            if (array[i] > array[i + 1]) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("IfStatementMissingBreakInLoop")
    public int leftBinSearch(long[] array, long key) {
        if (isUnsorted(array)) {
            throw new IllegalArgumentException();
        }

        int left = -1;
        int right = array.length;
        boolean found = false;
        while (left < right - 1) {
            int middle = (left + right) / 2;
            if (array[middle] == key) {
                found = true;
            }
            if (array[middle] < key) {
                left = middle;
            } else {
                right = middle;
            }
        }
        if (found) {
            return right + 1;
        } else {
            return -1;
        }
    }

    @SuppressWarnings("IfStatementMissingBreakInLoop")
    public int rightBinSearch(long[] array, long key) {
        if (isUnsorted(array)) {
            throw new IllegalArgumentException();
        }

        int left = -1;
        int right = array.length;
        boolean found = false;

        while (left < right - 1) {
            int middle = (left + right) / 2;
            if (array[middle] == key) {
                found = true;
            }
            if (array[middle] <= key) {
                left = middle;
            } else {
                right = middle;
            }
        }

        if (found) {
            return right;
        } else {
            return -1;
        }
    }

    public int defaultBinarySearch(long[] array, long key) {
        if (isUnsorted(array)) {
            throw new IllegalArgumentException();
        }

        int low = 0;
        int high = array.length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midVal = array[mid];

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return mid; // key found
        }
        return -(low + 1);  // key not found.
    }
}
