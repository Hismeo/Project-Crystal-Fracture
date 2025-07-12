package org.hismeo.nuquest.core.dialog.context.config.group;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;
import org.hismeo.nuquest.core.dialog.context.config.ImageConfig;
import org.hismeo.nuquest.core.dialog.context.config.ImagePlaceType;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static org.hismeo.crystallib.util.JsonUtil.tryGet;
import static org.hismeo.crystallib.util.JsonUtil.tryGetString;

/**
 * @param atlasLocation 纹理图集的位置。
 */
public record ImageGroup(@Nullable ResourceLocation atlasLocation, ImageConfig imageConfig, ImagePlaceType imagePlaceType) {
    @SuppressWarnings("deprecation")
    public static final ImageGroup EMPTY = new ImageGroup(TextureAtlas.LOCATION_PARTICLES, new ImageConfig(new EvalInt(0), new EvalInt(64), 64, 64, 0, 0, 64, 64, 64, 64), ImagePlaceType.NONE);

    public static ImageGroup formJson(JsonElement imageElement) {
        ResourceLocation atlasLocation = null;
        ImageConfig imageConfig = null;
        String imagePlaceType = "NONE";
        if (imageElement != null) {
            if (imageElement.isJsonPrimitive()) {
                atlasLocation = ResourceLocation.tryParse(imageElement.getAsString());
            } else if (imageElement.isJsonObject()) {
                JsonObject imageObject = imageElement.getAsJsonObject();
                atlasLocation = ResourceLocation.tryParse(imageObject.get("image").getAsString());
                JsonElement imageConfigElement = tryGet(imageObject, "imageConfig");
                if (imageConfigElement != null) {
                    imageConfig = ImageConfig.fromJson(imageConfigElement);
                }
                imagePlaceType = tryGetString(imageObject, "imagePlaceType", imagePlaceType);
            }
            return new ImageGroup(atlasLocation, imageConfig, ImagePlaceType.valueOf(imagePlaceType));
        }
        return null;
    }

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
