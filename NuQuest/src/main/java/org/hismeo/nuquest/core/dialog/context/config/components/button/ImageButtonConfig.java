package org.hismeo.nuquest.core.dialog.context.config.components.button;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;
import org.hismeo.nuquest.core.dialog.context.config.components.WidgetSpritesConfig;

import java.util.Map;

import static org.hismeo.crystallib.util.JsonUtil.tryGet;

public class ImageButtonConfig extends AbstractButtonConfig<ImageButtonConfig> {
    WidgetSpritesConfig widgetSpritesConfig;

    public ImageButtonConfig(WidgetSpritesConfig widgetSpritesConfig, EvalInt x, EvalInt y, EvalInt width, EvalInt height) {
        super(x, y, width, height);
        this.widgetSpritesConfig = widgetSpritesConfig;
    }

    public static ImageButtonConfig fromJson(JsonElement flipElement) {
        WidgetSpritesConfig widgetSpritesConfig = new WidgetSpritesConfig("widget/cross_button", "widget/cross_button_highlighted");
        EvalInt x = new EvalInt("@screenwidth-40"), y = new EvalInt("@screenheight-40"),
                width = new EvalInt(20), height = new EvalInt(20);
        if (flipElement != null) {
            JsonObject imageObject = flipElement.getAsJsonObject();
            widgetSpritesConfig = WidgetSpritesConfig.fromJson(tryGet(imageObject, "sprites_config"));
            x = EvalInt.fromJson(tryGet(imageObject, "x"));
            y = EvalInt.fromJson(tryGet(imageObject, "y"));
            width = EvalInt.fromJson(tryGet(imageObject, "width"));
            height = EvalInt.fromJson(tryGet(imageObject, "height"));
            return new ImageButtonConfig(widgetSpritesConfig, x, y, width, height);
        }
        return null;
    }

    public ImageButton getImageButton(Button.OnPress onPress, Map<String, Number> varMap) {
        return new ImageButton(x.eval(varMap), y.eval(varMap), width.eval(varMap), height.eval(varMap), widgetSpritesConfig.getWidgetSprites(), onPress);
    }

    @Override
    public boolean anyEmpty() {
        return anyEmpty(x, y, width, height, widgetSpritesConfig);
    }

    @Override
    public boolean allEmpty() {
        return allEmpty(x, y, width, height, widgetSpritesConfig);
    }

    @Override
    public ImageButtonConfig mergeData(ImageButtonConfig newData) {
        if (newData == null || newData.allEmpty()) return this;
        return new ImageButtonConfig(
                mergeWithStrategy(widgetSpritesConfig, newData.widgetSpritesConfig, WidgetSpritesConfig::mergeData),
                choose(newData.x, x),
                choose(newData.y, y),
                choose(newData.width, width),
                choose(newData.height, height)
        );
    }
}
