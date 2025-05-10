package org.hismeo.nuquest.core.dialogue.text.effect;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public interface ITextEffect {
    void effectApply(GuiGraphics guiGraphics, float partialTick, Component component);
    String getEffect();
}
