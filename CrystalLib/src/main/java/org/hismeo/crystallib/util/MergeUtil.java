package org.hismeo.crystallib.util;

import org.hismeo.crystallib.api.IEmpty;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * 通用合并工具类。
 * 提供 null 安全及空值判断，并可合并多种集合和数组类型。
 */
@SuppressWarnings("unused")
public final class MergeUtil {

    /**
     * 返回 newVal，如果 newVal 不为 null 且不是 empty；否则返回 oldVal。
     *
     * @param newVal 优先使用的新值
     * @param oldVal 备用的旧值
     * @param <T>    值的类型
     * @return 如果 newVal 非空则返回 newVal，否则返回 oldVal
     * <p>
     * 示例:
     * <pre>{@code
     * String result = MergeUtil.choose("new", "old"); // 返回 "new"
     * String result2 = MergeUtil.choose(null, "old");   // 返回 "old"
     * }</pre>
     */
    public static <T> T choose(T newVal, T oldVal) {
        return isEmpty(newVal) ? oldVal : newVal;
    }

    /**
     * 返回 newVal，如果 newVal 不为 null 且不是 empty；否则返回通过 defaultSupplier 获取的默认值。
     *
     * @param newVal          优先使用的新值
     * @param defaultSupplier 提供默认值的惰性执行器
     * @param <T>             值的类型
     * @return 如果 newVal 非空则返回 newVal，否则返回 defaultSupplier.get()
     * <p>
     * 示例:
     * <pre>{@code
     * String result = MergeUtil.chooseOrDefault(null, () -> "default"); // 返回 "default"
     * }</pre>
     */
    public static <T> T chooseOrDefault(T newVal, Supplier<T> defaultSupplier) {
        return isEmpty(newVal) ? defaultSupplier.get() : newVal;
    }

    /**
     * 判断一个对象是否为“空”，包括:
     * <ul>
     *     <li>null</li>
     *     <li>实现 IEmpty 并判定 allEmpty()</li>
     *     <li>CharSequence 长度为 0</li>
     *     <li>Collection/Map 大小为 0</li>
     *     <li>数组长度为 0（包括原始类型数组）</li>
     *     <li>Optional.empty()</li>
     * </ul>
     *
     * @param obj 要检查的对象
     * @return 是否为空
     */
    public static boolean isEmpty(Object obj) {
        if (obj == null) {
            return true;
        }
        if (obj instanceof IEmpty emptyObj) {
            return emptyObj.allEmpty();
        }
        if (obj instanceof CharSequence seq) {
            return seq.isEmpty();
        }
        if (obj instanceof Collection<?> col) {
            return col.isEmpty();
        }
        if (obj instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (obj.getClass().isArray()) {
            return Array.getLength(obj) == 0;
        }
        if (obj instanceof Optional<?> opt) {
            return opt.isEmpty();
        }
        return false;
    }

    /**
     * 合并两个 List，保留 oldList 元素，并在末尾追加 newList 中非空元素。
     *
     * @param oldList 原列表，可能为 null
     * @param newList 新列表，可能为 null
     * @param <T>     元素类型
     * @return 合并后的列表
     * <p>
     * 示例:
     * <pre>{@code
     * List<String> a = List.of("A", "B");
     * List<String> b = List.of("C", null);
     * List<String> merged = MergeUtil.mergeList(a, b);
     * // merged = ["A","B","C"]
     * }</pre>
     */
    public static <T> List<T> mergeList(List<T> oldList, List<T> newList) {
        List<T> result = new ArrayList<>(Optional.ofNullable(oldList).orElseGet(ArrayList::new));
        if (newList != null) {
            newList.stream().filter(item -> !isEmpty(item)).forEach(result::add);
        }
        return result;
    }

    /**
     * 合并两个 Set，保留两者中所有非空元素的并集，保持插入顺序。
     *
     * @param oldSet 原集合，可能为 null
     * @param newSet 新集合，可能为 null
     * @param <T>    元素类型
     * @return 合并后的集合
     * <p>
     * 示例:
     * <pre>{@code
     * Set<Integer> a = Set.of(1,2);
     * Set<Integer> b = Set.of(2,3,null);
     * Set<Integer> merged = MergeUtil.mergeSet(a, b);
     * // merged = [1,2,3]
     * }</pre>
     */
    public static <T> Set<T> mergeSet(Set<T> oldSet, Set<T> newSet) {
        Set<T> result = new LinkedHashSet<>(Optional.ofNullable(oldSet).orElseGet(LinkedHashSet::new));
        if (newSet != null) {
            newSet.stream().filter(item -> !isEmpty(item)).forEach(result::add);
        }
        return result;
    }

    /**
     * 合并两个 Map，保留原 Map 中所有键，如果 newMap 中的值非空则覆盖。
     *
     * @param oldMap 原 Map，可能为 null
     * @param newMap 新 Map，可能为 null
     * @param <K>    键类型
     * @param <V>    值类型
     * @return 合并后的 Map
     * <p>
     * 示例:
     * <pre>{@code
     * Map<String,Integer> a = Map.of("x",1);
     * Map<String,Integer> b = Map.of("y",2);
     * Map<String,Integer> merged = MergeUtil.mergeMap(a, b);
     * // merged = {x=1, y=2}
     * }</pre>
     */
    public static <K, V> Map<K, V> mergeMap(Map<K, V> oldMap, Map<K, V> newMap) {
        Map<K, V> result = new LinkedHashMap<>(Optional.ofNullable(oldMap).orElseGet(LinkedHashMap::new));
        if (newMap != null) {
            newMap.forEach((key, newVal) -> {
                if (!isEmpty(newVal)) {
                    result.put(key, newVal);
                }
            });
        }
        return result;
    }

    /**
     * 合并两个字段，如果新值不为空，则使用 mergeFunction 合并；
     * 如果旧值为空，则直接返回新值。
     *
     * @param oldVal        旧值，可能为 null
     * @param newVal        新值，可能为 null
     * @param mergeFunction 合并函数，参数为 (oldVal, newVal)
     * @param <T>           值类型
     * @return 合并后的值
     */
    public static <T> T mergeWithStrategy(T oldVal, T newVal, BiFunction<T, T, T> mergeFunction) {
        if (isEmpty(newVal)) return oldVal;
        if (isEmpty(oldVal)) return newVal;
        return mergeFunction.apply(oldVal, newVal);
    }

    /**
     * 数组合并：用 newArr 的元素覆盖 oldArr 相同索引位置。
     * 结果长度为 max(oldArr.length, newArr.length)，索引 i < newArr.length 且 newArr[i] 非空时取 newArr[i]，否则取 oldArr[i]。
     *
     * @param oldArr 原数组，可能为 null
     * @param newArr 新数组，可能为 null
     * @param <T>    数组元素类型
     * @return 合并后的数组
     */
    public static <T> T[] mergeArray(T[] oldArr, T[] newArr) {
        return mergeArray(oldArr, newArr, (oldLen, newLen) -> Array.newInstance((oldArr != null ? oldArr : newArr).getClass().getComponentType(), Math.max(oldLen, newLen)));
    }

    /**
     * 数组合并：接收 componentType，支持无 oldArr 类型信息场景。
     *
     * @param componentType 数组元素类型
     * @param oldArr        原数组，可能为 null
     * @param newArr        新数组，可能为 null
     * @param <T>           数组元素类型
     * @return 合并后的数组
     */
    public static <T> T[] mergeArray(Class<T> componentType, T[] oldArr, T[] newArr) {
        return mergeArray(oldArr, newArr, (oldLen, newLen) -> Array.newInstance(componentType, Math.max(oldLen, newLen)));
    }

    /**
     * 通用数组构建逻辑，通过传入数组长度生成器。
     *
     * @param oldArr       原数组
     * @param newArr       新数组
     * @param arrayCreator 根据 oldArr.length 和 newArr.length 生成目标数组的函数
     * @param <T>          数组元素类型
     * @return 合并后的数组
     */
    @SuppressWarnings("unchecked")
    private static <T> T[] mergeArray(T[] oldArr, T[] newArr, BiFunction<Integer, Integer, Object> arrayCreator) {
        int oldLen = oldArr != null ? oldArr.length : 0;
        int newLen = newArr != null ? newArr.length : 0;
        int resultLen = Math.max(oldLen, newLen);
        Object raw = arrayCreator.apply(oldLen, newLen);
        T[] result = (T[]) raw;
        if (newArr != null) {
            System.arraycopy(newArr, 0, result, 0, newLen);
        }
        if (oldArr != null && resultLen > newLen) {
            System.arraycopy(oldArr, newLen, result, newLen, resultLen - newLen);
        }
        return result;
    }
}
