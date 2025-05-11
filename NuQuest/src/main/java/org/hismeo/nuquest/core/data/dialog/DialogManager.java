package org.hismeo.nuquest.core.data.dialog;

import org.hismeo.nuquest.core.dialog.context.DialogDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DialogManager {
    private static final Map<String, DialogDefinition> generalDialogMap = new LinkedHashMap<>();
    private static final Map<String, DialogDefinition> dataDialogueMap = new LinkedHashMap<>();

    public static void register(String id, DialogDefinition dialogue) {
        if (generalDialogMap.putIfAbsent(id, dialogue) != null || dataDialogueMap.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate dialogue ID: " + id);
        }
    }

    static void dataRegister(String id, DialogDefinition dialogue) {
        if (generalDialogMap.containsKey(id)) {
            throw new IllegalArgumentException("ID conflict with code-defined dialogue: " + id);
        }
        dataDialogueMap.put(id, dialogue);
    }

    static void clearDataRegister() {
        dataDialogueMap.clear();
    }

    public static Set<String> getDialogueMapView() {
        Set<String> set = new LinkedHashSet<>();
        set.addAll(generalDialogMap.keySet());
        set.addAll(dataDialogueMap.keySet());
        return Collections.unmodifiableSet(set);
    }

    public static DialogDefinition getValue(@Nullable String id) {
        return generalDialogMap.getOrDefault(id, dataDialogueMap.getOrDefault(id, DialogDefinition.EMPTY));
    }
}
