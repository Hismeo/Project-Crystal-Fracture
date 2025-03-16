package org.hismeo.crystallib.util;

public class Group<F, S, T> {
    private final F first;
    private final S second;
    private final T third;
    public Group(F first, S second, T third){
        this.first = first;
        this.second = second;
        this.third = third;
    }

    public F getFirst() {
        return first;
    }

    public S getSecond() {
        return second;
    }

    public T getThird() {
        return third;
    }
}
