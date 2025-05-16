package org.hismeo.nuquest.core.dialog.context.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.Screen;
import org.hismeo.nuquest.api.dialog.IAction;

public class NoneAction implements IAction {
    @Override
    public void action(Screen screen) {
        screen.onClose();
    }

    @Override
    public String getAction() {
        return "none";
    }

    @Override
    public void parseJson(JsonObject jsonObject) {}
}
