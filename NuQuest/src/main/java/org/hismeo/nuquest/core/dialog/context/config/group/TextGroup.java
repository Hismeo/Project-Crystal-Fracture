package org.hismeo.nuquest.core.dialog.context.config.group;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.hismeo.nuquest.core.dialog.context.config.TextConfig;

import java.util.HashMap;
import java.util.Map;

import static org.hismeo.crystallib.util.JsonUtil.tryGet;
import static org.hismeo.crystallib.util.JsonUtil.tryGetString;

public record TextGroup(String text, TextConfig textConfig) {
    public static TextGroup fromJson(JsonElement textElement) {
        String text;
        TextConfig textConfig = null;
        if (textElement.isJsonPrimitive()) {
            text = textElement.getAsString();
        } else {
            JsonObject textObject = textElement.getAsJsonObject();
            text = tryGetString(textObject, "text");
            textConfig = TextConfig.fromJson(tryGet(textObject, "text_config"));
        }
        return new TextGroup(text, textConfig);
    }

    public void draw(TextConfig globalConfig, int index, Component text, Font font, GuiGraphics guiGraphics, Map<String, Number> varMap){
        HashMap<String, Number> indexVarMap = new HashMap<>(varMap);
        indexVarMap.put("@index", index);
        if (textConfig == null) {
            globalConfig.drawWordWrap(text, font, guiGraphics, indexVarMap);
        } else {
            globalConfig.copy().mergeData(textConfig).drawWordWrap(text, font, guiGraphics, indexVarMap);
        }
    }
}