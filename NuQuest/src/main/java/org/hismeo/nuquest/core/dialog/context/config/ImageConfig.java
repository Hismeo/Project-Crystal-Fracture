package org.hismeo.nuquest.core.dialog.context.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;
import org.hismeo.nuquest.core.IData;

import java.util.Map;

import static org.hismeo.crystallib.util.JsonUtil.tryGetFloat;
import static org.hismeo.crystallib.util.JsonUtil.tryGetInt;

/**
 * @param x             绘制区域左上角的 x 坐标。
 * @param y             绘制区域左上角的 y 坐标。
 * @param width         要绘制部分的宽度。
 * @param height        要绘制部分的高度。
 * @param uOffset       水平纹理坐标偏移量。
 * @param vOffset       垂直纹理坐标偏移量。
 * @param uWidth        绘制部分在纹理坐标中的宽度。
 * @param vHeight       绘制部分在纹理坐标中的高度。
 * @param textureWidth  纹理的总宽度。
 * @param textureHeight 纹理的总高度。
 */
public record ImageConfig(EvalInt x, EvalInt y, EvalInt width, EvalInt height, Float uOffset, Float vOffset, Integer uWidth,
                          Integer vHeight, Integer textureWidth, Integer textureHeight) implements IData<ImageConfig> {
    public static ImageConfig fromJson(JsonElement imageConfigElement) {
        EvalInt x, y = new EvalInt("(screenheight / 3 * 2) - 64");
        EvalInt width = new EvalInt(64), height = new EvalInt(64);
        Float uOffset, vOffset;
        Integer uWidth = 64, vHeight = 64;
        Integer textureWidth = 64, textureHeight = 64;
        if (imageConfigElement != null){
            JsonObject imageConfigObject = imageConfigElement.getAsJsonObject();
            x = EvalInt.fromJson(imageConfigObject.get("x"));
            y = EvalInt.fromJson(imageConfigObject.get("y"));
            width = EvalInt.fromJson(imageConfigObject.get("width"));
            height = EvalInt.fromJson(imageConfigObject.get("height"));
            uOffset = tryGetFloat(imageConfigObject, "u_offset");
            vOffset = tryGetFloat(imageConfigObject, "v_offset");
            uWidth = tryGetInt(imageConfigObject, "u_width");
            vHeight = tryGetInt(imageConfigObject, "v_height");
            textureWidth = tryGetInt(imageConfigObject, "texture_width");
            textureHeight = tryGetInt(imageConfigObject, "texture_height");
            return new ImageConfig(x, y, width, height, uOffset, vOffset, uWidth, vHeight, textureWidth, textureHeight);
        }
        return null;
    }

    public void blitImage(ResourceLocation atlasLocation, GuiGraphics guiGraphics, Map<String, Number> varMap) {
        guiGraphics.blit(atlasLocation,
                x.eval(varMap),
                y.eval(varMap),
                width.eval(varMap),
                height.eval(varMap),
                uOffset,
                vOffset,
                uWidth,
                vHeight,
                textureWidth,
                textureHeight
        );
    }

    public ImageConfig copy() {
        return new ImageConfig(x, y, width, height, uOffset, vOffset, uWidth, vHeight, textureWidth, textureHeight);
    }

    @Override
    public boolean anyEmpty() {
        return anyEmpty(x, y, width, height, uOffset, vOffset, uWidth, vHeight, textureWidth, textureHeight);
    }

    @Override
    public boolean allEmpty() {
        return allEmpty(x, y, width, height, uOffset, vOffset, uWidth, vHeight, textureWidth, textureHeight);
    }

    @Override
    public ImageConfig mergeData(ImageConfig newData) {
        if (newData == null || newData.allEmpty()) return this;
        return new ImageConfig(
                choose(newData.x, x),
                choose(newData.y, y),
                choose(newData.width, width),
                choose(newData.height, height),
                choose(newData.uOffset, uOffset),
                choose(newData.vOffset, vOffset),
                choose(newData.uWidth, uWidth),
                choose(newData.vHeight, vHeight),
                choose(newData.textureHeight, textureWidth),
                choose(newData.textureHeight, textureHeight)
        );
    }
}
