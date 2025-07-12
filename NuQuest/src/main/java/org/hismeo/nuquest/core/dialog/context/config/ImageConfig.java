package org.hismeo.nuquest.core.dialog.context.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystallib.api.IEmpty;
import org.hismeo.crystallib.api.IMerge;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;

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
public record ImageConfig(EvalInt x, EvalInt y, int width, int height, float uOffset, float vOffset, int uWidth,
                          int vHeight, int textureWidth, int textureHeight) implements IMerge<ImageConfig>, IEmpty<ImageConfig> {
    public static ImageConfig fromJson(JsonElement imageConfigElement) {
        EvalInt x, y = new EvalInt("(screenheight / 3 * 2) - 64");
        int width = 64, height = 64;
        float uOffset, vOffset;
        int uWidth = 64, vHeight = 64;
        int textureWidth = 64, textureHeight = 64;
        if (imageConfigElement != null){
            JsonObject imageConfigObject = imageConfigElement.getAsJsonObject();
            x = EvalInt.fromJson(imageConfigObject.get("x"));
            y = EvalInt.fromJson(imageConfigObject.get("y"), y);
            width = tryGetInt(imageConfigObject, "width", width);
            height = tryGetInt(imageConfigObject, "height", height);
            uOffset = tryGetFloat(imageConfigObject, "uOffset");
            vOffset = tryGetFloat(imageConfigObject, "vOffset");
            uWidth = tryGetInt(imageConfigObject, "uWidth", uWidth);
            vHeight = tryGetInt(imageConfigObject, "vHeight", vHeight);
            textureWidth = tryGetInt(imageConfigObject, "textureWidth", textureWidth);
            textureHeight = tryGetInt(imageConfigObject, "textureHeight", textureHeight);
            return new ImageConfig(x, y, width, height, uOffset, vOffset, uWidth, vHeight, textureWidth, textureHeight);
        }
        return null;
    }

    public void blitImage(ResourceLocation atlasLocation, GuiGraphics guiGraphics, Map<String, Number> varMap) {
        guiGraphics.blit(atlasLocation,
                x.eval(varMap),
                y.eval(varMap),
                width,
                height,
                uOffset,
                vOffset,
                uWidth,
                vHeight,
                textureWidth,
                textureHeight
        );
    }

    @Override
    public boolean anyEmpty() {
        return false;
    }

    @Override
    public boolean allEmpty() {
        return false;
    }

    @Override
    public ImageConfig mergeData(ImageConfig newData) {
        return null;
    }
}
