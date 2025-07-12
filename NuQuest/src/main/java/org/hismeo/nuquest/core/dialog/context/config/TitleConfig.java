package org.hismeo.nuquest.core.dialog.context.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;

import java.util.HashMap;
import java.util.Map;

import static org.hismeo.crystallib.util.JsonUtil.*;

public record TitleConfig(EvalInt x, EvalInt y, int color, boolean useUnderline, UnderlineConfig underlineConfig) {
    public static TitleConfig fromJson(JsonElement titleElement) {
        EvalInt x = new EvalInt(10), y = new EvalInt("@screenheight / 3 * 2 + 4");
        int color = 0xFFFFFFFF;
        boolean useUnderline = true;
        TitleConfig.UnderlineConfig underlineConfig = null;
        if (titleElement != null) {
            JsonObject titleObject = titleElement.getAsJsonObject();
            x = EvalInt.fromJson(tryGet(titleObject, "x"), x);
            y = EvalInt.fromJson(tryGet(titleObject, "y"), y);
            color = tryGetInt(titleObject, "color", color);
            useUnderline = tryGetBoolean(titleObject, "useUnderline", useUnderline);
            underlineConfig = UnderlineConfig.fromJson(tryGet(titleObject, "underlineConfig"));
            return new TitleConfig(x, y, color, useUnderline, underlineConfig);
        }
        return null;
    }

    public void drawTitle(String title, Font font, GuiGraphics guiGraphics, Map<String, Number> varMap) {
        HashMap<String, Number> textVarMap = new HashMap<>(varMap);
        textVarMap.put("@textwidth", font.width(title));
        guiGraphics.drawString(font, title, x.eval(varMap), y.eval(varMap), color);
        if (useUnderline && underlineConfig != null) underlineConfig.drawLine(guiGraphics, textVarMap);
    }

    public record UnderlineConfig(EvalInt minX, EvalInt minY, EvalInt maxX, EvalInt maxY, int color) {
        public static UnderlineConfig fromJson(JsonElement underlineElement) {
            EvalInt minX = new EvalInt(8), minY = new EvalInt("@screenheight / 3 * 2 + 14"),
                    maxX = new EvalInt("@textwidth + 12"), maxY = new EvalInt("@screenheight / 3 * 2 + 15");
            int color = 0xFFFFFFFF;
            if (underlineElement != null) {
                JsonObject underlineObject = underlineElement.getAsJsonObject();
                minX = EvalInt.fromJson(tryGet(underlineObject, "minX"));
                minY = EvalInt.fromJson(tryGet(underlineObject, "minY"), minY);
                maxX = EvalInt.fromJson(tryGet(underlineObject, "maxX"), maxX);
                maxY = EvalInt.fromJson(tryGet(underlineObject, "maxY"), maxY);
                color = tryGetInt(underlineObject, "color", color);
                return new TitleConfig.UnderlineConfig(minX, minY, maxX, maxY, color);
            }
            return null;
        }

        public void drawLine(GuiGraphics guiGraphics, Map<String, Number> varMap) {
            guiGraphics.fill(minX.eval(varMap), minY.eval(varMap), maxX.eval(varMap), maxY.eval(varMap), color);
        }
    }
}
