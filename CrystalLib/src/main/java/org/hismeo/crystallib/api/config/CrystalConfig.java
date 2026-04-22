package org.hismeo.crystallib.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CrystalConfig {
    /** 归属 modId（用于自动扫描过滤）。 */
    String modId();

    /** 配置文件名，不填则使用默认命名。 */
    String fileName() default "";

    /** 存储格式，默认 TOML。 */
    ConfigFormat format() default ConfigFormat.TOML;

    /** 配置作用域（common/client/server）。 */
    ConfigScope scope() default ConfigScope.COMMON;

    /** 注册后是否立即加载配置文件。 */
    boolean autoLoad() default true;
}
