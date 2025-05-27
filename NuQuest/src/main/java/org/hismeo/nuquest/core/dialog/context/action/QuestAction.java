package org.hismeo.nuquest.core.dialog.context.action;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.Screen;
import org.hismeo.nuquest.api.dialog.IAction;

@SuppressWarnings("unused")
public class QuestAction implements IAction {
    @Override
    public void action(Screen screen) {

    }

    @Override
    public String getAction() {
        return "quest";
    }

    @Override
    public void parseJson(JsonObject jsonObject) {

    }
}
