package org.hismeo.nuquest.core.dialog.context.text;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.hismeo.nuquest.api.dialog.ITextEffect;
import org.hismeo.nuquest.core.dialog.context.config.group.ImageGroup;
import org.hismeo.nuquest.core.dialog.context.config.group.SoundGroup;
import org.hismeo.nuquest.core.dialog.context.config.group.TextGroup;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static org.hismeo.crystallib.util.JsonUtil.tryGet;
import static org.hismeo.crystallib.util.JsonUtil.tryGetString;

public record DialogText(String title, ImageGroup[] imageGroup, TextGroup[] textGroup, SoundGroup soundGroup, ITextEffect textEffect) {
    public static @NotNull DialogText fromJson(JsonElement textElement) {
        JsonObject textObject = textElement.getAsJsonObject();
        String title = tryGetString(textObject, "title");
        JsonElement imageGroupElement = tryGet(textObject, "image_group");
        List<ImageGroup> imageGroups = new ArrayList<>();
        if (imageGroupElement != null) {
            if (imageGroupElement.isJsonObject()) {
                imageGroups.add(ImageGroup.fromJson(imageGroupElement));
            } else {
                JsonArray imageGroupArray = imageGroupElement.getAsJsonArray();
                for (int i = 0; i < imageGroupArray.size(); i++) {
                    imageGroups.add(ImageGroup.fromJson(imageGroupArray.get(i)));
                }
            }
        }

        JsonElement textGroupElement = tryGet(textObject, "text_group");
        List<TextGroup> textGroups = new ArrayList<>();
        if (textGroupElement != null) {
            if (!textGroupElement.isJsonArray()) {
                textGroups.add(TextGroup.fromJson(textGroupElement));
            } else {
                JsonArray textGroupArray = textGroupElement.getAsJsonArray();
                for (int i = 0; i < textGroupArray.size(); i++) {
                    textGroups.add(TextGroup.fromJson(textGroupArray.get(i)));
                }
            }
        }

        SoundGroup soundGroup = SoundGroup.fromJson(tryGet(textObject, "sound_group"));
        JsonElement effectElement = tryGet(textObject, "textEffect");
        ITextEffect textEffect = ITextEffect.fromJson(effectElement);
        return new DialogText(title, imageGroups.toArray(ImageGroup[]::new), textGroups.toArray(TextGroup[]::new), soundGroup, textEffect);
    }
}
