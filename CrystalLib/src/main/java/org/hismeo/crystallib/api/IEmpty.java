package org.hismeo.crystallib.api;

import com.google.common.collect.Maps;
import org.hismeo.crystallib.api.json.expression.IEval;

import java.util.Arrays;
import java.util.HashMap;

public interface IEmpty<T extends IEmpty> {
    boolean anyEmpty();

    boolean allEmpty();

    default boolean arrayEmpty(T[] array) {
        return array.length == 0;
    }

    default <F> boolean fieldEmpty(F field) {
        return field == null;
    }
}
