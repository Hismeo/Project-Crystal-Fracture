package org.hismeo.nuquest.core.dialog.context.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.Screen;
import org.hismeo.nuquest.api.dialog.IAction;

public class DialogAction implements IAction {
    private String dialogId;

    public DialogAction(String dialogId) {
        this.dialogId = dialogId;
    }

    @Override
    public void action(Screen screen) {

    }

    @Override
    public String getAction() {
        return "dialog";
    }

    @Override
    public void parseJsonArray(JsonObject jsonArray) {

    }
}
