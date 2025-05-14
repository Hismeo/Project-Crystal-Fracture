package org.hismeo.nuquest.core.dialog.context.action;

import net.minecraft.client.gui.screens.Screen;

public class NoneAction implements IAction{
    @Override
    public void action(Screen screen) {
        screen.onClose();
    }

    @Override
    public String getAction() {
        return "none";
    }
}
