package org.hismeo.nuquest.core.dialog.context.action;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.Screen;
import org.hismeo.nuquest.api.dialog.IAction;
import org.hismeo.nuquest.common.network.c2s.CommandPacketC2S;

@SuppressWarnings("unused")
public class CommandAction implements IAction {
    private String command;

    public CommandAction() {
    }

    public CommandAction(String command) {
        this.command = command;
    }

    @Override
    public void action(Screen screen) {
        CommandPacketC2S.handleCommand(this.command);
    }

    @Override
    public String getAction() {
        return "command";
    }

    @Override
    public void parseJson(JsonObject jsonObject) {
        if (jsonObject.has("command")) {
            this.command = jsonObject.get("command").getAsString();
        }
    }
}
