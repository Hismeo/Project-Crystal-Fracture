package org.hismeo.nuquest.core.dialog.context.config.components.button;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;
import org.hismeo.nuquest.core.dialog.context.config.components.WidgetSpritesConfig;

import java.util.Map;

import static org.hismeo.crystallib.util.JsonUtil.tryGet;

public class FlipButtonConfig extends AbstractButtonConfig {
    WidgetSpritesConfig widgetSpritesConfig;

    public static FlipButtonConfig fromJson(JsonElement flipElement) {
        WidgetSpritesConfig widgetSpritesConfig = new WidgetSpritesConfig("widget/cross_button", "widget/cross_button_highlighted");
        EvalInt x = new EvalInt("@screenwidth-40"), y = new EvalInt("@screenheight-40"),
                width = new EvalInt(20), height = new EvalInt(20);
        if (flipElement != null) {
            JsonObject flipObject = flipElement.getAsJsonObject();
            widgetSpritesConfig = WidgetSpritesConfig.fromJson(tryGet(flipObject, "widgetSpritesConfig"));
            x = EvalInt.fromJson(tryGet(flipObject, "x"), x);
            y = EvalInt.fromJson(tryGet(flipObject, "y"), y);
            width = EvalInt.fromJson(tryGet(flipObject, "width"), width);
            height = EvalInt.fromJson(tryGet(flipObject, "height"), height);
            return new FlipButtonConfig(widgetSpritesConfig, x, y, width, height);
        }
        return null;
    }

    public FlipButtonConfig(WidgetSpritesConfig widgetSpritesConfig, EvalInt x, EvalInt y, EvalInt width, EvalInt height) {
        super(x, y, width, height);
        this.widgetSpritesConfig = widgetSpritesConfig;
    }

    public ImageButton getFlipButton(Button.OnPress onPress, Map<String, Number> varMap) {
        return new ImageButton(x.eval(varMap), y.eval(varMap), width.eval(varMap), height.eval(varMap), widgetSpritesConfig.getWidgetSprites(), onPress);
    }
}
