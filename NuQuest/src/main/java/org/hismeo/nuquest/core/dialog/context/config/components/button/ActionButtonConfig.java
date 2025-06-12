package org.hismeo.nuquest.core.dialog.context.config.components.button;

import net.minecraft.network.chat.Component;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;
import org.hismeo.nuquest.client.gui.component.ActionButton;

import java.util.HashMap;
import java.util.Map;

public class ActionButtonConfig extends AbstractButtonConfig{
    public ActionButtonConfig(EvalInt x, EvalInt y, EvalInt width, EvalInt height) {
        super(x, y, width, height);
    }

    public ActionButton getActionButton(int index, Component message, ActionButton.Press press, Map<String, Number> varMap){
        HashMap<String, Number> indexVarMap = new HashMap<>(varMap);
        indexVarMap.put("@index", index);
        return new ActionButton(x.eval(indexVarMap), y.eval(indexVarMap), width.eval(indexVarMap), height.eval(indexVarMap), message, press);
    }
}
