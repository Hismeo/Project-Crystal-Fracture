package org.hismeo.crystallib.api;

import java.util.Arrays;

public interface IEmpty {
    boolean anyEmpty();

    boolean allEmpty();

    default <A> boolean arrayEmpty(A[] array) {
        return array.length == 0
                || Arrays.stream(array).allMatch(e -> empty(e) || (e instanceof IEmpty i && i.allEmpty()));
    }

    default <F> boolean empty(F field) {
        return field == null;
    }

    @SuppressWarnings("unchecked")
    default <F> boolean allEmpty(F... fields) {
        return Arrays.stream(fields).allMatch(f -> empty(f) || (f instanceof IEmpty iEmpty && iEmpty.allEmpty()));
    }

    @SuppressWarnings("unchecked")
    default <F> boolean anyEmpty(F... fields) {
        return Arrays.stream(fields).anyMatch(f -> empty(f) || (f instanceof IEmpty iEmpty && iEmpty.anyEmpty()));
    }
}
