package org.hismeo.nuquest.core.dialog.context.config;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;

import java.util.HashMap;
import java.util.Map;

public record TextConfig(EvalInt x, EvalInt y, int color) {
    public void drawString(int index, String text, Font font, GuiGraphics guiGraphics, Map<String, Number> varMap){
        HashMap<String, Number> indexVarMap = new HashMap<>(varMap);
        indexVarMap.put("@index", index);
        guiGraphics.drawString(font, text, x.eval(indexVarMap), y.eval(indexVarMap), color);
    }
}
