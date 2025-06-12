package org.hismeo.nuquest.core.dialog.context.config.components.button;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;
import org.hismeo.nuquest.core.dialog.context.config.components.WidgetSpritesConfig;

import java.util.Map;

public class FlipButtonConfig extends AbstractButtonConfig {
    WidgetSpritesConfig widgetSpritesConfig;

    public FlipButtonConfig(WidgetSpritesConfig widgetSpritesConfig, EvalInt x, EvalInt y, EvalInt width, EvalInt height) {
        super(x, y, width, height);
        this.widgetSpritesConfig = widgetSpritesConfig;
    }

    public ImageButton getFlipButton(Button.OnPress onPress, Map<String, Number> varMap) {
        return new ImageButton(x.eval(varMap), y.eval(varMap), width.eval(varMap), height.eval(varMap), widgetSpritesConfig.getWidgetSprites(), onPress);
    }
}
