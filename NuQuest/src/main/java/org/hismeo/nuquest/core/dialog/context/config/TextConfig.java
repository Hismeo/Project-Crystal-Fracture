package org.hismeo.nuquest.core.dialog.context.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;

import java.util.HashMap;
import java.util.Map;

import static org.hismeo.crystallib.util.JsonUtil.tryGet;
import static org.hismeo.crystallib.util.JsonUtil.tryGetInt;

public record TextConfig(EvalInt x, EvalInt y, int color) {
    public static TextConfig fromJson(JsonElement textElement) {
        EvalInt x = new EvalInt(20), y = new EvalInt("@screenheight + 29 + @index * 9");
        int color = 0xFFFFFFFF;
        if (textElement != null) {
            JsonObject textObject = textElement.getAsJsonObject();
            x = EvalInt.fromJson(tryGet(textObject, "x"), x);
            y = EvalInt.fromJson(tryGet(textObject, "y"), y);
            color = tryGetInt(textObject, "color", color);
            return new TextConfig(x, y, color);
        }
        return null;
    }

    public void drawString(int index, String text, Font font, GuiGraphics guiGraphics, Map<String, Number> varMap){
        HashMap<String, Number> indexVarMap = new HashMap<>(varMap);
        indexVarMap.put("@index", index);
        guiGraphics.drawString(font, text, x.eval(indexVarMap), y.eval(indexVarMap), color);
    }
}
