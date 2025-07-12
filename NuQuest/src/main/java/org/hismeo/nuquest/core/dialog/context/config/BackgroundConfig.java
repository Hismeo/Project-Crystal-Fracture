package org.hismeo.nuquest.core.dialog.context.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import org.hismeo.crystallib.api.IEmpty;
import org.hismeo.crystallib.api.IMerge;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;

import java.util.Map;

import static org.hismeo.crystallib.util.JsonUtil.tryGet;
import static org.hismeo.crystallib.util.JsonUtil.tryGetInt;

public record BackgroundConfig(EvalInt x, EvalInt y, EvalInt width, EvalInt height,
                               Integer colorFrom, Integer colorTo) implements IMerge<BackgroundConfig>, IEmpty<BackgroundConfig> {
    public static BackgroundConfig fromJson(JsonElement backgroundElement) {
        EvalInt x, y, width, height;
        Integer colorFrom = null, colorTo = null;
        if (backgroundElement != null) {
            JsonObject backgroundObject = backgroundElement.getAsJsonObject();
            x = EvalInt.fromJson(tryGet(backgroundObject, "x"));
            y = EvalInt.fromJson(tryGet(backgroundObject, "y"));
            width = EvalInt.fromJson(tryGet(backgroundObject, "width"));
            height = EvalInt.fromJson(tryGet(backgroundObject, "height"));
            colorFrom = tryGetInt(backgroundObject, "colorFrom");
            colorTo = tryGetInt(backgroundObject, "colorTo");
            return new BackgroundConfig(x, y, width, height, colorFrom, colorTo);
        }
        return null;
    }

    public void drawBackground(GuiGraphics guiGraphics, Map<String, Number> varMap) {
        guiGraphics.fillGradient(x.eval(varMap), y.eval(varMap), width.eval(varMap), height.eval(varMap), colorFrom, colorTo);
    }

    @Override
    public BackgroundConfig mergeData(BackgroundConfig newData) {
        return null;
    }

    @Override
    public boolean anyEmpty() {
        return fieldEmpty(x) ||
                fieldEmpty(y) ||
                fieldEmpty(width) ||
                fieldEmpty(height) ||
                fieldEmpty(colorFrom) ||
                fieldEmpty(colorTo);
    }

    @Override
    public boolean allEmpty() {
        return fieldEmpty(x) &&
                fieldEmpty(y) &&
                fieldEmpty(width) &&
                fieldEmpty(height) &&
                fieldEmpty(colorFrom) &&
                fieldEmpty(colorTo);
    }
}
