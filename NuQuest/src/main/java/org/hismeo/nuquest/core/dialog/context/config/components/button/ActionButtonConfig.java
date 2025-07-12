package org.hismeo.nuquest.core.dialog.context.config.components.button;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;
import org.hismeo.nuquest.client.gui.component.ActionButton;

import java.util.HashMap;
import java.util.Map;

import static org.hismeo.crystallib.util.JsonUtil.tryGet;

public class ActionButtonConfig extends AbstractButtonConfig{
    public ActionButtonConfig(EvalInt x, EvalInt y, EvalInt width, EvalInt height) {
        super(x, y, width, height);
    }

    public static ActionButtonConfig fromJson(JsonElement actionElement) {
        EvalInt x = new EvalInt("@screenwidth-100"), y = new EvalInt("@screenheight / 3 * 2 - (@index + 1) * 30"),
                width = new EvalInt(100), height = new EvalInt(20);
        if (actionElement != null) {
            JsonObject actionButtonObject = actionElement.getAsJsonObject();
            x = EvalInt.fromJson(tryGet(actionButtonObject, "x"), x);
            y = EvalInt.fromJson(tryGet(actionButtonObject, "y"), y);
            width = EvalInt.fromJson(tryGet(actionButtonObject, "width"), width);
            height = EvalInt.fromJson(tryGet(actionButtonObject, "height"), height);
            return new ActionButtonConfig(x, y, width, height);
        }
        return null;
    }

    public ActionButton getActionButton(int index, Component message, ActionButton.Press press, Map<String, Number> varMap){
        HashMap<String, Number> indexVarMap = new HashMap<>(varMap);
        indexVarMap.put("@index", index);
        return new ActionButton(x.eval(indexVarMap), y.eval(indexVarMap), width.eval(indexVarMap), height.eval(indexVarMap), message, press);
    }
}
