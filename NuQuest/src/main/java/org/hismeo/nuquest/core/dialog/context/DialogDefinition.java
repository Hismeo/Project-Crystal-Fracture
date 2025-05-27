package org.hismeo.nuquest.core.dialog.context;

import org.hismeo.nuquest.api.dialog.IAction;
import org.hismeo.nuquest.core.dialog.ImageGroup;
import org.hismeo.nuquest.core.dialog.context.action.CloseAction;
import org.hismeo.nuquest.core.dialog.context.text.DialogText;
import org.hismeo.nuquest.core.dialog.context.text.effect.NoneEffect;

public record DialogDefinition(String dialogueId, DialogText[] dialogTexts, DialogActionData[] dialogActionDatas) {
    public static final DialogDefinition EMPTY = new DialogDefinition("error", new DialogText[]{new DialogText(null, ImageGroup.EMPTY, "如果你看到了这段话\n那么你可能达到了错误的对话屏幕。", null, new NoneEffect())}, new DialogActionData[]{new DialogActionData("关闭", new IAction[]{new CloseAction()})});
}
