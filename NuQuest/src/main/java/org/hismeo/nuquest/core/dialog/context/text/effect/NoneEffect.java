package org.hismeo.nuquest.core.dialog.context.text.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.hismeo.nuquest.api.dialog.ITextEffect;

public class NoneEffect implements ITextEffect {
    @Override
    public void effectApply(GuiGraphics guiGraphics, Component text, float partialTick, int textHeight, int textWeight) {}

    @Override
    public String getEffect() {return "none";}

    @Override
    public void parseJson(JsonObject jsonObject) {}
}
