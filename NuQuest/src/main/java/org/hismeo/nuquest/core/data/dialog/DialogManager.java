package org.hismeo.nuquest.core.data.dialog;

import org.hismeo.nuquest.core.dialog.context.DialogDefinition;
import org.hismeo.nuquest.core.dialog.context.config.DialogConfig;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DialogManager {
    private static final Map<String, DialogDefinition> generalDialogMap = new LinkedHashMap<>();
    private static final Map<String, DialogDefinition> dataDialogMap = new LinkedHashMap<>();
    private static final DialogConfig globalDialogConfig = new DialogConfig();

    public static void register(String id, DialogDefinition dialog) {
        if (generalDialogMap.putIfAbsent(id, dialog) != null || dataDialogMap.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate dialog ID: " + id);
        }
    }

    static void dataRegister(String id, DialogDefinition dialog) {
        if (generalDialogMap.containsKey(id)) {
            throw new IllegalArgumentException("ID conflict with code-defined dialog: " + id);
        }
        dataDialogMap.put(id, dialog);
    }

    static void clearDataRegister() {
        dataDialogMap.clear();
    }

    static void replaceConfig(DialogConfig dialogConfig) {
        globalDialogConfig.replace(dialogConfig);
    }

    public static DialogConfig getGlobalDialogConfig() {
        return globalDialogConfig;
    }

    public static Set<String> getDialogMapView() {
        Set<String> set = new LinkedHashSet<>();
        set.addAll(generalDialogMap.keySet());
        set.addAll(dataDialogMap.keySet());
        return Collections.unmodifiableSet(set);
    }

    public static DialogDefinition getValue(@Nullable String id) {
        return generalDialogMap.getOrDefault(id, dataDialogMap.getOrDefault(id, DialogDefinition.EMPTY));
    }
}
