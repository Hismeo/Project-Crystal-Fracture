package org.hismeo.nuquest.core.dialog.text.effect;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.hismeo.nuquest.api.dialog.text.ITextEffect;

public record TypewriterEffect(float speed) implements ITextEffect {
    @Override
    public void effectApply(GuiGraphics guiGraphics, Component text, float partialTick, int textHeight, int textWeight) {

    }

    @Override
    public String getEffect() {
        return "typewriter";
    }
}
