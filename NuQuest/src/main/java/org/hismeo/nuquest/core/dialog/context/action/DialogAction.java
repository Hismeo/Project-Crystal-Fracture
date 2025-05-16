package org.hismeo.nuquest.core.dialog.context.action;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.Screen;
import org.hismeo.crystallib.util.client.MinecraftUtil;
import org.hismeo.nuquest.api.dialog.IAction;
import org.hismeo.nuquest.client.gui.screen.DialogScreen;
import org.hismeo.nuquest.core.data.dialog.DialogManager;
import org.hismeo.nuquest.core.dialog.context.DialogDefinition;

@SuppressWarnings("unused")
public class DialogAction implements IAction {
    private String dialogId;

    public DialogAction() {}

    public DialogAction(String dialogId) {
        this.dialogId = dialogId;
    }

    @Override
    public void action(Screen screen) {
        DialogDefinition definition = DialogManager.getValue(this.dialogId);
        MinecraftUtil.setScreen(new DialogScreen(definition));
    }

    @Override
    public String getAction() {
        return "dialog";
    }

    @Override
    public void parseJson(JsonObject jsonObject) {
        if (jsonObject.has("dialogId")) {
            this.dialogId = jsonObject.get("dialogId").getAsString();
        }
    }
}
