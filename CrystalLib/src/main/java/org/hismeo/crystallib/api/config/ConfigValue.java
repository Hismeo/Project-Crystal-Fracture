package org.hismeo.crystallib.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigValue {
    /** 配置键名，不填默认使用字段名。 */
    String key() default "";

    /** 兼容旧键名，按顺序回退读取。 */
    String[] aliases() default {};

    /** 字段注释（TOML 会写入注释，其它格式忽略）。 */
    String comment() default "";

    /** 是否允许为 null。 */
    boolean allowNull() default false;

    /** 字符串是否禁止空白（trim 后为空视为无效）。 */
    boolean notBlank() default false;

    /** 字符串正则约束，不填表示不校验。 */
    String regex() default "";

    /** 数值字段最小值。 */
    double min() default -Double.MAX_VALUE;

    /** 数值字段最大值。 */
    double max() default Double.MAX_VALUE;

    /** 长度/大小最小值（String/Collection/Map/Array）。 */
    int minSize() default 0;

    /** 长度/大小最大值（String/Collection/Map/Array）。 */
    int maxSize() default Integer.MAX_VALUE;
}
