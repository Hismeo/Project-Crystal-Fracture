package org.hismeo.nuquest.core.dialog.context.config.components;

import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.ResourceLocation;

public record WidgetSpritesConfig(String enabled, String disabled, String enabledFocused, String disabledFocused) {
    public WidgetSpritesConfig(String unFocused, String focused){
        this(unFocused, unFocused, focused, focused);
    }

    public WidgetSprites getWidgetSprites(){
        return new WidgetSprites(ResourceLocation.parse(enabled), ResourceLocation.parse(disabled), ResourceLocation.parse(enabledFocused), ResourceLocation.parse(disabledFocused));
    }
}
