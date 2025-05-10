package org.hismeo.nuquest.core.dialog.context;

import org.hismeo.nuquest.core.dialog.text.DialogText;
import org.hismeo.nuquest.core.dialog.text.effect.NoneEffect;

public record DialogDefinition(String dialogueId, DialogText[] dialogTexts) {
    public static final DialogDefinition EMPTY = new DialogDefinition("error", new DialogText[]{new DialogText("如果你看到了这段话\n那么你可能达到了错误的对话屏幕。", null, new NoneEffect()), new DialogText("对的", null, new NoneEffect())});
}
