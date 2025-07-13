package org.hismeo.crystallib.util;

import org.hismeo.crystallib.api.IEmpty;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class MergeUtil {

    /**
     * 返回 newVal，如果 newVal 不为 null 且不是 empty；否则返回 oldVal。
     */
    public static <T> T choose(T newVal, T oldVal) {
        return isEmpty(newVal) ? oldVal : newVal;
    }

    /**
     * 判断一个对象是否为“空”，包括 null 或实现 IEmpty 并判定 allEmpty。
     */
    public static boolean isEmpty(Object obj) {
        if (obj == null) return true;
        if (obj instanceof IEmpty<?> emptyObj) {
            return emptyObj.allEmpty();
        }
        return false;
    }

    /**
     * 合并两个 Map，保留原 Map 中所有键，如果 newMap 中的值非空则覆盖。
     */
    public static <K, V> Map<K, V> mergeMap(Map<K, V> oldMap, Map<K, V> newMap) {
        Map<K, V> result = new HashMap<>(oldMap);
        for (Map.Entry<K, V> entry : newMap.entrySet()) {
            V newVal = entry.getValue();
            if (!isEmpty(newVal)) {
                result.put(entry.getKey(), newVal);
            }
        }
        return result;
    }

    /**
     * 合并两个字段，如果新值不为空，则使用 mergeFunction。
     */
    public static <T> T mergeWithStrategy(T oldVal, T newVal, BiFunction<T, T, T> mergeFunction) {
        if (isEmpty(newVal)) return oldVal;
        if (isEmpty(oldVal)) return newVal;
        return mergeFunction.apply(oldVal, newVal);
    }
}
