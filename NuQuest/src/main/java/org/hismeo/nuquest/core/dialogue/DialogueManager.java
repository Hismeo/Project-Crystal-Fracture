package org.hismeo.nuquest.core.dialogue;

import org.hismeo.nuquest.core.dialogue.context.DialogueDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class DialogueManager {
    private static Map<String, DialogueDefinition> dialogueMap = new LinkedHashMap<>();
    private static final Set<String> dialogueMapView = Collections.unmodifiableSet(dialogueMap.keySet());

    public void registerDialogue(String id, DialogueDefinition dialogueDefinition){
        if (dialogueMap.putIfAbsent(id, dialogueDefinition) != null) {
            throw new IllegalArgumentException("Duplicate registration " + id);
        }
    }

    public static Set<String> getDialogueMapView() {
        return dialogueMapView;
    }

    public static DialogueDefinition getValue(@Nullable String id){
        return dialogueMap.getOrDefault(id, DialogueDefinition.EMPTY);
    }
}
