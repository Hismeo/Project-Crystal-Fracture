package org.hismeo.nuquest.core.dialog.context.config.group;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;
import org.hismeo.nuquest.core.dialog.context.config.ImageConfig;

import java.util.Map;

/**
 * @param atlasLocation 纹理图集的位置。
 */
public record ImageGroup(ResourceLocation atlasLocation, ImageConfig imageConfig) {
    public static final ImageGroup EMPTY = new ImageGroup(TextureAtlas.LOCATION_PARTICLES, new ImageConfig(new EvalInt(0), new EvalInt(64), 64, 64, 0, 0, 64, 64, 64, 64));

    public boolean hasImage() {
        return atlasLocation != null;
    }

    public void blitImage(GuiGraphics guiGraphics, Map<String, Number> varMap) {
        imageConfig.blitImage(atlasLocation, guiGraphics, varMap);
    }
}
