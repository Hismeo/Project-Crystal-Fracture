package org.hismeo.nuquest.core.dialog.context.config.group;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;
import org.hismeo.nuquest.core.IData;
import org.hismeo.nuquest.core.dialog.context.config.ImageConfig;
import org.hismeo.nuquest.core.dialog.context.config.ImagePlaceType;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static org.hismeo.crystallib.util.JsonUtil.tryGet;
import static org.hismeo.crystallib.util.JsonUtil.tryGetString;

/**
 * @param atlasLocation 纹理图集的位置。
 */
public record ImageGroup(@Nullable ResourceLocation atlasLocation, ImageConfig imageConfig, ImagePlaceType imagePlaceType) implements IData<ImageGroup> {
    @SuppressWarnings("deprecation")
    public static final ImageGroup EMPTY = new ImageGroup(TextureAtlas.LOCATION_PARTICLES, new ImageConfig(new EvalInt(0), new EvalInt(64), new EvalInt(64), new EvalInt(64), 0f, 0f, 64, 64, 64, 64), ImagePlaceType.NONE);

    public static ImageGroup fromJson(JsonElement imageElement) {
        ResourceLocation atlasLocation = null;
        ImageConfig imageConfig = null;
        String imagePlaceType = "NONE";
        if (imageElement != null) {
            if (imageElement.isJsonPrimitive()) {
                atlasLocation = ResourceLocation.tryParse(imageElement.getAsString());
            } else if (imageElement.isJsonObject()) {
                JsonObject imageObject = imageElement.getAsJsonObject();
                atlasLocation = ResourceLocation.tryParse(imageObject.get("image").getAsString());
                JsonElement imageConfigElement = tryGet(imageObject, "image_config");
                if (imageConfigElement != null) {
                    imageConfig = ImageConfig.fromJson(imageConfigElement);
                }
                imagePlaceType = tryGetString(imageObject, "image_place_type", imagePlaceType);
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
                globalConfig.copy().mergeData(imageConfig).blitImage(atlasLocation, guiGraphics, varMap);
            }
        }
    }

    @Override
    public ImageGroup mergeData(ImageGroup newData) {
        if (newData == null || newData.allEmpty()) return this;
        return new ImageGroup(
                choose(newData.atlasLocation, atlasLocation),
                mergeWithStrategy(imageConfig, newData.imageConfig, ImageConfig::mergeData),
                choose(newData.imagePlaceType, imagePlaceType)
        );
    }

    @Override
    public boolean anyEmpty() {
        return anyEmpty(atlasLocation, imageConfig, imagePlaceType);
    }

    @Override
    public boolean allEmpty() {
        return anyEmpty(atlasLocation, imageConfig, imagePlaceType);
    }
}
