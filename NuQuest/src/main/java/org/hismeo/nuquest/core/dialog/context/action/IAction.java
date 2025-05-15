package org.hismeo.nuquest.core.dialog.context.action;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import org.hismeo.crystallib.util.ReflectionUtil;

public interface IAction {
    void action(Screen screen);

    String getAction();

    static IAction getAction(String name) {
        for (IAction implClass : ReflectionUtil.getImplClass(IAction.class)) {
            if (implClass.getAction().equals(name)) {
                return implClass;
            }
        }
        return new NoneAction();
    }
}
