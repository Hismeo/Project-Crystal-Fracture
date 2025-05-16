package org.hismeo.nuquest.api.dialog;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.Screen;
import org.hismeo.crystallib.util.ReflectionUtil;
import org.hismeo.nuquest.core.dialog.context.action.NoneAction;

import java.util.List;

public interface IAction {
    List<IAction> ACTIONS = ReflectionUtil.getImplClass(IAction.class);
    void action(Screen screen);

    String getAction();

    void parseJson(JsonObject jsonObject);

    static IAction getAction(String name) {
        for (IAction implClass : ACTIONS) {
            if (implClass.getAction().equals(name)) {
                return implClass;
            }
        }
        return new NoneAction();
    }
}
