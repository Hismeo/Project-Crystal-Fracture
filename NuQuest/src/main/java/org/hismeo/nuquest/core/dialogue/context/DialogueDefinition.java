package org.hismeo.nuquest.core.dialogue.context;

import org.hismeo.nuquest.core.dialogue.text.TextType;

public record DialogueDefinition(String dialogueId, String[] message, TextType textType) {
    public static final DialogueDefinition EMPTY = new DialogueDefinition("error", new String[]{"如果你看到了这段话，那么你可能达到了错误的对话屏幕。"}, null);
}
