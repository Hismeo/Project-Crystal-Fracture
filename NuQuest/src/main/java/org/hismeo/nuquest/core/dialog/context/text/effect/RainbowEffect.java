package org.hismeo.nuquest.core.dialog.context.text.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.hismeo.nuquest.api.dialog.ITextEffect;

@SuppressWarnings("unused")
public class RainbowEffect implements ITextEffect {
    private float speed = 1.0f;
    public RainbowEffect() {}

    public RainbowEffect(float speed) {
        this.speed = speed;
    }

    @Override
    public void effectApply(GuiGraphics guiGraphics, Component text, float partialTick, int textHeight, int textWeight) {

    }

    @Override
    public String getEffect() {
        return "rainbow";
    }

    @Override
    public void parseJson(JsonObject jsonObject) {

    }
}
