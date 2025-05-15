package org.hismeo.nuquest.core.dialog.context.text.effect;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.hismeo.nuquest.api.dialog.text.ITextEffect;

public class TypewriterEffect implements ITextEffect {
    private final float speed;
    public TypewriterEffect(){
        this.speed = 1.0f;
    }
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
}
