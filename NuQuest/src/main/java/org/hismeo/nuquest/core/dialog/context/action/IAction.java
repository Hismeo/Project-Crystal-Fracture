package org.hismeo.nuquest.core.dialog.context.action;

import net.minecraft.client.gui.screens.Screen;

public interface IAction {
    void action(Screen screen);
    String getAction();
}
