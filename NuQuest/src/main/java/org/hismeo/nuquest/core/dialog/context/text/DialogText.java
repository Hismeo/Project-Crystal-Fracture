package org.hismeo.nuquest.core.dialog.context.text;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.hismeo.nuquest.api.dialog.ITextEffect;
import org.hismeo.nuquest.core.dialog.context.config.group.ImageGroup;
import org.hismeo.nuquest.core.dialog.context.config.group.SoundGroup;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static org.hismeo.crystallib.util.JsonUtil.tryGet;
import static org.hismeo.crystallib.util.JsonUtil.tryGetString;

public record DialogText(String title, ImageGroup[] imageGroup, String text, SoundGroup soundGroup, ITextEffect textEffect) {
    public static @NotNull DialogText fromJson(JsonElement textElement) {
        JsonObject textObject = textElement.getAsJsonObject();
        String title = tryGetString(textObject, "title");
        JsonElement imageGroupElement = tryGet(textObject, "imageGroup");
        List<ImageGroup> imageGroups = new ArrayList<>();
        if (imageGroupElement != null) {
            if (imageGroupElement.isJsonObject()) {
                imageGroups.add(ImageGroup.formJson(imageGroupElement));
            } else {
                JsonArray imageGroupArray = imageGroupElement.getAsJsonArray();
                for (int i = 0; i < imageGroupArray.size(); i++) {
                    imageGroups.add(ImageGroup.formJson(imageGroupArray.get(i)));
                }
            }
        }
        String text = tryGetString(textObject, "text");
        SoundGroup soundGroup = SoundGroup.fromJson(tryGet(textObject, "soundGroup"));
        JsonElement effectElement = tryGet(textObject, "textEffect");
        ITextEffect textEffect = ITextEffect.fromJson(effectElement);
        return new DialogText(title, imageGroups.toArray(ImageGroup[]::new), text, soundGroup, textEffect);
    }
}
