package org.hismeo.crystallib.api.config;

import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.hismeo.crystallib.CrystalLib;
import org.hismeo.crystallib.api.config.impl.ConfigEngine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class CrystalConfigApi {
    private static final Map<Class<?>, ConfigHandle<?>> REGISTRY = new ConcurrentHashMap<>();
    private static final Map<Class<?>, ConfigTypeHandler<?>> TYPE_HANDLERS = new ConcurrentHashMap<>();

    private CrystalConfigApi() {}

    /**
     * 手动注册单个配置类。
     *
     * @param configClass 配置类（需标注 {@link CrystalConfig}）
     * @return 对应的配置句柄
     * @param <T> 配置类型
     */
    @SuppressWarnings("unchecked")
    public static <T> ConfigHandle<T> register(Class<T> configClass) {
        ConfigHandle<?> existing = REGISTRY.get(configClass);
        if (existing != null) return (ConfigHandle<T>) existing;

        CrystalConfig config = requireConfigAnnotation(configClass);
        ConfigHandle<T> handle = ConfigEngine.createHandle(configClass, config, TYPE_HANDLERS);
        REGISTRY.put(configClass, handle);
        if (config.autoLoad()) handle.load();
        return handle;
    }

    /**
     * 自动扫描并注册指定 modId 的配置类。
     *
     * @param modId 模组 id
     * @return 已注册的配置句柄列表
     */
    public static List<ConfigHandle<?>> autoRegister(String modId) {
        List<ConfigHandle<?>> result = new ArrayList<>();
        for (ModFileScanData scanData : ModList.get().getAllScanData()) {
            for (ModFileScanData.ClassData classData : scanData.getClasses()) {
                try {
                    Class<?> clazz = Class.forName(classData.clazz().getClassName(), false, CrystalConfigApi.class.getClassLoader());
                    CrystalConfig config = clazz.getAnnotation(CrystalConfig.class);
                    if (config == null || !Objects.equals(config.modId(), modId)) continue;
                    result.add(register(clazz));
                } catch (Throwable e) {
                    CrystalLib.LOGGER.debug("Skip config class {}: {}", classData.clazz().getClassName(), e.getMessage());
                }
            }
        }
        return result;
    }

    /**
     * 注册自定义类型处理器。
     *
     * @param type 类型
     * @param handler 编解码处理器
     * @param <T> 类型参数
     */
    public static <T> void registerTypeHandler(Class<T> type, ConfigTypeHandler<T> handler) {
        TYPE_HANDLERS.put(type, handler);
    }

    /**
     * 移除自定义类型处理器。
     *
     * @param type 类型
     */
    public static void unregisterTypeHandler(Class<?> type) {
        TYPE_HANDLERS.remove(type);
    }

    /**
     * 获取当前已注册配置的只读集合。
     *
     * @return 只读视图
     */
    public static Collection<ConfigHandle<?>> registered() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /**
     * 热重载全部已注册配置。
     *
     * @return 成功重载数量
     */
    public static int reloadAll() {
        int success = 0;
        for (ConfigHandle<?> handle : REGISTRY.values()) {
            try {
                handle.load();
                success++;
            } catch (Throwable e) {
                CrystalLib.LOGGER.error("Reload config failed: {}", handle.path(), e);
            }
        }
        return success;
    }

    private static <T> CrystalConfig requireConfigAnnotation(Class<T> configClass) {
        CrystalConfig config = configClass.getAnnotation(CrystalConfig.class);
        if (config == null) {
            throw new IllegalArgumentException("@CrystalConfig is required: " + configClass.getName());
        }
        if (config.modId().isBlank()) {
            throw new IllegalArgumentException("modId cannot be blank: " + configClass.getName());
        }
        return config;
    }
}
