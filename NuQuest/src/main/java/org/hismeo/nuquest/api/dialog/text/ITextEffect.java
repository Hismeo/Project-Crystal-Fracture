package org.hismeo.nuquest.api.dialog.text;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public interface ITextEffect {
    void effectApply(GuiGraphics guiGraphics, Component text, float partialTick, int textHeight, int textWeight);
    String getEffect();
}
