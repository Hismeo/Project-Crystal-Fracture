package org.hismeo.nuquest.core.data.dialog;

import org.hismeo.nuquest.core.dialog.context.DialogDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class DialogManager {
    private static Map<String, DialogDefinition> dialogueMap = new LinkedHashMap<>();
    private static final Set<String> dialogueMapView = Collections.unmodifiableSet(dialogueMap.keySet());

    public void registerDialogue(String id, DialogDefinition dialogDefinition){
        if (dialogueMap.putIfAbsent(id, dialogDefinition) != null) {
            throw new IllegalArgumentException("Duplicate registration " + id);
        }
    }

    public static Set<String> getDialogueMapView() {
        return dialogueMapView;
    }

    public static DialogDefinition getValue(@Nullable String id){
        return dialogueMap.getOrDefault(id, DialogDefinition.EMPTY);
    }
}
