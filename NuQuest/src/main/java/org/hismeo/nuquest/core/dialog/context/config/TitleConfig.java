package org.hismeo.nuquest.core.dialog.context.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;
import org.hismeo.nuquest.core.IData;

import java.util.HashMap;
import java.util.Map;

import static org.hismeo.crystallib.util.JsonUtil.*;

public record TitleConfig(EvalInt x, EvalInt y, Integer color, Boolean useUnderline, UnderlineConfig underlineConfig) implements IData<TitleConfig>{
    public static TitleConfig fromJson(JsonElement titleElement) {
        EvalInt x = new EvalInt(10), y = new EvalInt("@screenheight / 3 * 2 + 4");
        Integer color = 0xFFFFFFFF;
        boolean useUnderline = true;
        TitleConfig.UnderlineConfig underlineConfig = null;
        if (titleElement != null) {
            JsonObject titleObject = titleElement.getAsJsonObject();
            x = EvalInt.fromJson(tryGet(titleObject, "x"));
            y = EvalInt.fromJson(tryGet(titleObject, "y"));
            color = tryGetInt(titleObject, "color");
            useUnderline = tryGetBoolean(titleObject, "use_underline");
            underlineConfig = UnderlineConfig.fromJson(tryGet(titleObject, "underline_config"));
            return new TitleConfig(x, y, color, useUnderline, underlineConfig);
        }
        return null;
    }

    public void drawTitle(Component title, Font font, GuiGraphics guiGraphics, Map<String, Number> varMap) {
        HashMap<String, Number> textVarMap = new HashMap<>(varMap);
        textVarMap.put("@textwidth", font.width(title));
        guiGraphics.drawString(font, title, x.eval(varMap), y.eval(varMap), color);
        if (useUnderline && underlineConfig != null) underlineConfig.drawLine(guiGraphics, textVarMap);
    }

    @Override
    public TitleConfig mergeData(TitleConfig newData) {
        if (newData == null || newData.allEmpty()) return this;
        return new TitleConfig(
                choose(newData.x, x),
                choose(newData.y, y),
                choose(newData.color, color),
                choose(newData.useUnderline, useUnderline),
                mergeWithStrategy(underlineConfig, newData.underlineConfig, UnderlineConfig::mergeData)
        );
    }

    @Override
    public boolean anyEmpty() {
        return anyEmpty(x, y, color, useUnderline, underlineConfig);
    }

    @Override
    public boolean allEmpty() {
        return allEmpty(x, y, color, useUnderline, underlineConfig);
    }

    public record UnderlineConfig(EvalInt minX, EvalInt minY, EvalInt maxX, EvalInt maxY, Integer color) implements IData<UnderlineConfig> {
        public static UnderlineConfig fromJson(JsonElement underlineElement) {
            EvalInt minX = new EvalInt(8), minY = new EvalInt("@screenheight / 3 * 2 + 14"),
                    maxX = new EvalInt("@textwidth + 12"), maxY = new EvalInt("@screenheight / 3 * 2 + 15");
            Integer color = 0xFFFFFFFF;
            if (underlineElement != null) {
                JsonObject underlineObject = underlineElement.getAsJsonObject();
                minX = EvalInt.fromJson(tryGet(underlineObject, "min_x"));
                minY = EvalInt.fromJson(tryGet(underlineObject, "min_y"));
                maxX = EvalInt.fromJson(tryGet(underlineObject, "max_x"));
                maxY = EvalInt.fromJson(tryGet(underlineObject, "max_y"));
                color = tryGetInt(underlineObject, "color");
                return new TitleConfig.UnderlineConfig(minX, minY, maxX, maxY, color);
            }
            return null;
        }

        public void drawLine(GuiGraphics guiGraphics, Map<String, Number> varMap) {
            guiGraphics.fill(minX.eval(varMap), minY.eval(varMap), maxX.eval(varMap), maxY.eval(varMap), color);
        }

        @Override
        public UnderlineConfig mergeData(UnderlineConfig newData) {
            if (newData == null || newData.allEmpty()) return this;
            return new UnderlineConfig(
                    choose(newData.minX, minX),
                    choose(newData.minY, minY),
                    choose(newData.maxX, maxX),
                    choose(newData.maxY, maxY),
                    choose(newData.color, color)
            );
        }

        @Override
        public boolean anyEmpty() {
            return anyEmpty(minX, minY, maxX, maxY, color);
        }

        @Override
        public boolean allEmpty() {
            return allEmpty(minX, minY, maxX, maxY, color);
        }
    }
}
