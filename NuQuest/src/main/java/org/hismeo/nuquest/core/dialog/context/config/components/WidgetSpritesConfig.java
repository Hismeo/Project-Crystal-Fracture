package org.hismeo.nuquest.core.dialog.context.config.components;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.ResourceLocation;

import static org.hismeo.crystallib.util.JsonUtil.tryGetString;

public record WidgetSpritesConfig(String enabled, String disabled, String enabledFocused, String disabledFocused) {
    public WidgetSpritesConfig(String unFocused, String focused) {
        this(unFocused, unFocused, focused, focused);
    }

    public static WidgetSpritesConfig fromJson(JsonElement spritesElement) {
        String enabled = null, disabled = null, enabledFocused = null, disabledFocused = null;
        if (spritesElement != null) {
            JsonObject spritesObject = spritesElement.getAsJsonObject();
            enabled = tryGetString(spritesObject, "enabled");
            disabled = tryGetString(spritesObject, "disabled");
            enabledFocused = tryGetString(spritesObject, "enabledFocused");
            disabledFocused = tryGetString(spritesObject, "disabledFocused");
            if (disabled == null) disabled = enabled;
            if (disabledFocused == null) disabledFocused = enabledFocused;
            return new WidgetSpritesConfig(enabled, disabled, enabledFocused, disabledFocused);
        }
        return null;
    }

    public WidgetSprites getWidgetSprites() {
        return new WidgetSprites(ResourceLocation.parse(enabled), ResourceLocation.parse(disabled), ResourceLocation.parse(enabledFocused), ResourceLocation.parse(disabledFocused));
    }
}
