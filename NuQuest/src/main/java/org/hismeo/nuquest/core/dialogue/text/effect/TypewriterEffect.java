package org.hismeo.nuquest.core.dialogue.text.effect;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class TypewriterEffect implements ITextEffect{
    @Override
    public void effectApply(GuiGraphics guiGraphics, float partialTick, Component component) {

    }

    @Override
    public String getEffect() {
        return "typewriter";
    }
}
