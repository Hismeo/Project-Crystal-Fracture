package org.hismeo.nuquest.core.dialog;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;

import java.util.Map;

/**
 * @param atlasLocation 纹理图集的位置。
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
public record ImageGroup(ResourceLocation atlasLocation, EvalInt x, EvalInt y, int width,
                         int height, float uOffset,
                         float vOffset, int uWidth, int vHeight, int textureWidth, int textureHeight) {
    public static final ImageGroup EMPTY = new ImageGroup(TextureAtlas.LOCATION_PARTICLES, new EvalInt(0), new EvalInt(64), 64, 64, 0, 0, 64, 64, 64, 64);

    public boolean hasImage() {
        return atlasLocation != null;
    }

    public void blitImage(GuiGraphics guiGraphics, Map<String, Number> varMap) {
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
}
