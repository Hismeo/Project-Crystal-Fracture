package org.hismeo.crystallib.api;

import java.util.Arrays;

public interface IEmpty<T extends IEmpty> {
    boolean anyEmpty();

    boolean allEmpty();

    default boolean arrayEmpty(T[] array) {
        return array.length == 0;
    }

    default <F> boolean empty(F field) {
        return field == null;
    }

    default <F> boolean allEmpty(F... fields) {
        return Arrays.stream(fields).allMatch(this::empty);
    }

    default <F> boolean anyEmpty(F... fields) {
        return Arrays.stream(fields).anyMatch(this::empty);
    }
}
