package com.dataiku.wt1;

import java.util.LinkedList;

public class LimitedQueue<E> extends LinkedList<E> {
    private static final long serialVersionUID = 1L;
    private int maxSize;

    public LimitedQueue(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public boolean add(E o) {
        super.add(o);
        while (size() > maxSize) { super.remove(); }
        return true;
    }
}