package org.hismeo.nuquest.core.dialog.context.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.Screen;
import org.hismeo.nuquest.api.dialog.IAction;

public class CommandAction implements IAction {
    private String command;

    public CommandAction(String command) {
        this.command = command;
    }

    @Override
    public void action(Screen screen) {

    }

    @Override
    public String getAction() {
        return "command";
    }

    @Override
    public void parseJsonArray(JsonObject jsonArray) {

    }
}
