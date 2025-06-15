package org.hismeo.nuquest.core.dialog.context.config;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;

import java.util.Map;

public record TitleConfig(EvalInt x, EvalInt y, int color, boolean useUnderline, UnderlineConfig underlineConfig) {
    public void drawTitle(String title, Font font, GuiGraphics guiGraphics, Map<String, Number> varMap) {
        varMap.put("@textwidth", font.width(title));
        guiGraphics.drawString(font, title, x.eval(varMap), y.eval(varMap), color);
        if (useUnderline && underlineConfig != null) underlineConfig.drawLine(guiGraphics, varMap);
    }

    public record UnderlineConfig(EvalInt minX, EvalInt minY, EvalInt maxX, EvalInt maxY, int color) {
        public void drawLine(GuiGraphics guiGraphics, Map<String, Number> varMap) {
            guiGraphics.fill(minX.eval(varMap), minY.eval(varMap), maxX.eval(varMap), maxY.eval(varMap), color);
        }
    }
}
