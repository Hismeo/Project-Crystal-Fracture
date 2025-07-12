package org.hismeo.nuquest.api.dialog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.hismeo.crystallib.util.ReflectionUtil;
import org.hismeo.nuquest.core.dialog.context.text.effect.NoneEffect;

import java.util.List;

import static org.hismeo.crystallib.util.JsonUtil.tryGet;
import static org.hismeo.crystallib.util.JsonUtil.tryGetString;


public interface ITextEffect {
    List<ITextEffect> EFFECTS = ReflectionUtil.getImplClass(ITextEffect.class);

    void effectApply(GuiGraphics guiGraphics, Component text, float partialTick, int textHeight, int textWeight);

    //TODO
    String getEffect();

    void parseJson(JsonObject jsonObject);

    static ITextEffect getEffect(String name) {
        for (ITextEffect implClass : EFFECTS) {
            if (implClass.getEffect().equals(name)) {
                return implClass;
            }
        }
        return new NoneEffect();
    }

    static ITextEffect fromJson(JsonElement jsonElement) {
        ITextEffect effect = new NoneEffect();
        if (jsonElement != null) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            effect = ITextEffect.getEffect(tryGetString(jsonObject, "name"));
            JsonElement params = tryGet(jsonObject, "params");
            if (params != null) {
                effect.parseJson(params.getAsJsonObject());
            }
        }
        return effect;
    }
}
