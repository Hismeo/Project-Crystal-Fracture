package org.hismeo.nuquest.core.dialog.context.text.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.hismeo.nuquest.api.dialog.ITextEffect;

@SuppressWarnings("unused")
public class TypewriterEffect implements ITextEffect {
    private float speed = 1.0f;

    public TypewriterEffect(){}

    public TypewriterEffect(float speed){
        this.speed = speed;
    }

    @Override
    public void effectApply(GuiGraphics guiGraphics, Component text, float partialTick, int textHeight, int textWeight) {

    }

    @Override
    public String getEffect() {
        return "typewriter";
    }

    @Override
    public void parseJson(JsonObject jsonObject) {
        if (jsonObject.has("speed")) {
            this.speed = jsonObject.get("speed").getAsFloat();
        }
    }
}
