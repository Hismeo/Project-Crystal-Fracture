package org.hismeo.crystallib.api.config;

import java.nio.file.Path;

public final class ConfigHandle<T> {
    private final Class<T> configClass;
    private final T instance;
    private final Path path;
    private final Runnable loadAction;
    private final Runnable saveAction;

    public ConfigHandle(Class<T> configClass, T instance, Path path, Runnable loadAction, Runnable saveAction) {
        this.configClass = configClass;
        this.instance = instance;
        this.path = path;
        this.loadAction = loadAction;
        this.saveAction = saveAction;
    }

    public Class<T> configClass() {
        return configClass;
    }

    public T instance() {
        return instance;
    }

    public Path path() {
        return path;
    }

    /** 从文件加载并回填到配置字段。 */
    public void load() {
        loadAction.run();
    }

    /** 将当前字段值写回配置文件。 */
    public void save() {
        saveAction.run();
    }
}
