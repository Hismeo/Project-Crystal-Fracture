package org.hismeo.nuquest.core.dialog.context.config.group;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;
import org.hismeo.nuquest.core.dialog.context.config.ImageConfig;
import org.hismeo.nuquest.core.dialog.context.config.ImagePlaceType;

import java.util.Map;

/**
 * @param atlasLocation 纹理图集的位置。
 */
public record ImageGroup(ResourceLocation atlasLocation, ImageConfig imageConfig, ImagePlaceType imagePlaceType) {
    public static final ImageGroup EMPTY = new ImageGroup(TextureAtlas.LOCATION_PARTICLES, new ImageConfig(new EvalInt(0), new EvalInt(64), 64, 64, 0, 0, 64, 64, 64, 64), ImagePlaceType.NONE);

    public boolean hasImage() {
        return atlasLocation != null;
    }

    public void blitImage(ImageConfig globalConfig, GuiGraphics guiGraphics, Map<String, Number> varMap) {
        if (hasImage()) {
            if (imageConfig == null) {
                globalConfig.blitImage(atlasLocation, guiGraphics, varMap);
            } else {
                imageConfig.blitImage(atlasLocation, guiGraphics, varMap);
            }
        }
    }
}
