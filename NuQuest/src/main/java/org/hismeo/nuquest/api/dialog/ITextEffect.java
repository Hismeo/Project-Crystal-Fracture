package org.hismeo.nuquest.api.dialog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.hismeo.crystallib.util.ReflectionUtil;
import org.hismeo.nuquest.core.dialog.context.text.effect.NoneEffect;


public interface ITextEffect {
    void effectApply(GuiGraphics guiGraphics, Component text, float partialTick, int textHeight, int textWeight);

    String getEffect();

    void parseJsonArray(JsonObject jsonArray);
    static ITextEffect getEffect(String name) {
        for (ITextEffect implClass : ReflectionUtil.getImplClass(ITextEffect.class)) {
            if (implClass.getEffect().equals(name)) {
                return implClass;
            }
        }
        return new NoneEffect();
    }
}
