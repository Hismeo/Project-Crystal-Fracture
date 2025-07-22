package org.hismeo.nuquest.core;

import org.hismeo.crystallib.api.IEmpty;
import org.hismeo.crystallib.util.MergeUtil;

import java.util.function.BiFunction;

@SuppressWarnings("unused")
public interface IData<T> extends IEmpty {
    T mergeData(T newData);

    default <F> F choose(F newVal, F oldVal){
        return MergeUtil.choose(newVal, oldVal);
    }

    default <F> F[] mergeArray(F[] oldArr, F[] newArr) {
        return mergeArrayData(oldArr, MergeUtil.mergeArray(oldArr, newArr));
    }

    default <F> F[] mergeArray(Class<F> componentType, F[] oldArr, F[] newArr){
        return mergeArrayData(oldArr, MergeUtil.mergeArray(componentType, oldArr, newArr));
    }

    @SuppressWarnings("unchecked")
    default <F> F[] mergeArrayData(F[] oldArr, F[] newArr) {
        if (oldArr == null || oldArr.length == 0) return newArr;
        for (int i = 0; i < newArr.length; i++) {
            F newVal = newArr[i];
            F oldVal = oldArr[Math.min(i, oldArr.length - 1)];
            if (newVal instanceof IData<?> newData && oldVal instanceof IData<?> oldData) {
                F result = ((IData<F>) oldData).mergeData((F) newData);
                newArr[i] = result;
            }
        }
        return newArr;
    }

    default <F> F mergeWithStrategy(F oldVal, F newVal, BiFunction<F, F, F> mergeFunction) {
        return MergeUtil.mergeWithStrategy(oldVal, newVal, mergeFunction);
    }
}
