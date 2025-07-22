package org.hismeo.nuquest.core.dialog.context.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;
import org.hismeo.nuquest.core.IData;

import java.util.Map;

import static org.hismeo.crystallib.util.JsonUtil.tryGet;
import static org.hismeo.crystallib.util.JsonUtil.tryGetInt;

public record TextConfig(EvalInt x, EvalInt y, EvalInt lineWidth, Integer color) implements IData<TextConfig> {
    public static TextConfig fromJson(JsonElement textElement) {
        EvalInt x = new EvalInt(20), y = new EvalInt("@screenheight + 29 + @index * 9"), lineWidth;
        Integer color = 0xFFFFFFFF;
        if (textElement != null) {
            JsonObject textObject = textElement.getAsJsonObject();
            x = EvalInt.fromJson(tryGet(textObject, "x"));
            y = EvalInt.fromJson(tryGet(textObject, "y"));
            lineWidth = EvalInt.fromJson(tryGet(textObject, "line_width"));
            color = tryGetInt(textObject, "color");
            return new TextConfig(x, y, lineWidth, color);
        }
        return null;
    }

    public TextConfig copy() {
        return new TextConfig(x, y, lineWidth, color);
    }

    public void drawWordWrap(Component text, Font font, GuiGraphics guiGraphics, Map<String, Number> varMap) {
        guiGraphics.drawWordWrap(font, text, x.eval(varMap), y.eval(varMap), lineWidth.eval(varMap), color);
    }

    @Override
    public TextConfig mergeData(TextConfig newData) {
        if (newData == null || newData.allEmpty()) return this;
        return new TextConfig(
                choose(newData.x, x),
                choose(newData.y, y),
                choose(newData.lineWidth, lineWidth),
                choose(newData.color, color)
        );
    }

    @Override
    public boolean anyEmpty() {
        return anyEmpty(x, y, lineWidth, color);
    }

    @Override
    public boolean allEmpty() {
        return allEmpty(x, y, lineWidth, color);
    }
}
