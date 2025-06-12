package org.hismeo.nuquest.core.dialog.context.config;

import net.minecraft.client.gui.GuiGraphics;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;

import java.util.Map;

public record BackgroundConfig(EvalInt x, EvalInt y, EvalInt width, EvalInt height, int colorFrom, int colorTo) {
    public void drawBackground(GuiGraphics guiGraphics, Map<String, Number> varMap){
        guiGraphics.fillGradient(x.eval(varMap), y.eval(varMap), width.eval(varMap), height.eval(varMap), colorFrom, colorTo);
    }
}
