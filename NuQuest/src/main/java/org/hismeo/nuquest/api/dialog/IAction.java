package org.hismeo.nuquest.api.dialog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.Screen;
import org.hismeo.crystallib.util.ReflectionUtil;
import org.hismeo.nuquest.core.dialog.context.action.NoneAction;

public interface IAction {
    void action(Screen screen);

    String getAction();

    void parseJsonArray(JsonObject jsonArray);

    static IAction getAction(String name) {
        for (IAction implClass : ReflectionUtil.getImplClass(IAction.class)) {
            if (implClass.getAction().equals(name)) {
                return implClass;
            }
        }
        return new NoneAction();
    }
}
