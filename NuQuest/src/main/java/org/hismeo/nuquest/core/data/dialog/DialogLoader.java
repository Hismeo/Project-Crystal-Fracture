package org.hismeo.nuquest.core.data.dialog;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;
import org.hismeo.nuquest.NuQuest;
import org.hismeo.nuquest.api.dialog.text.ITextEffect;
import org.hismeo.nuquest.core.dialog.ImageGroup;
import org.hismeo.nuquest.core.dialog.SoundGroup;
import org.hismeo.nuquest.core.dialog.context.DialogDefinition;
import org.hismeo.nuquest.core.dialog.text.DialogText;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hismeo.crystallib.util.JsonUtil.*;

public class DialogLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public DialogLoader() {
        super(GSON, "nu_quest/dialog");
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> object, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        DialogManager.clearDataRegister();
        for (var entry : object.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement element = entry.getValue();

            try {
                JsonObject jsonObject = element.getAsJsonObject();
                String dialogueId = jsonObject.get("dialogueId").getAsString();
                JsonArray dialogTextsArray = jsonObject.getAsJsonArray("dialogTexts");

                List<DialogText> dialogTexts = new ArrayList<>();

                for (JsonElement dialogTextElement : dialogTextsArray) {
                    DialogText dialogText = getDialogText(dialogTextElement);
                    dialogTexts.add(dialogText);
                }

                DialogDefinition dialogDefinition = new DialogDefinition(dialogueId, dialogTexts.toArray(DialogText[]::new));
                DialogManager.dataRegister(id.toString(), dialogDefinition);
            } catch (NullPointerException e) {
                NuQuest.LOGGER.error("[DialogReloadListener] Failed to load dialogue: {} - {}", id, e.getMessage(), e);
            }
        }
        NuQuest.LOGGER.debug("[DialogReloadListener] Loaded {} dialogues.", DialogManager.getDialogueMapView().size());
    }

    private static @NotNull DialogText getDialogText(JsonElement dialogTextElement) {
        JsonObject dialogTextObject = dialogTextElement.getAsJsonObject();
        String title = tryGetString(dialogTextObject, "title");
        ImageGroup imageGroup = getImageGroup(tryGet(dialogTextObject, "imageGroup"));
        String text = tryGetString(dialogTextObject, "text");
        SoundGroup soundGroup = getSoundGroup(tryGet(dialogTextObject, "soundGroup"));
        ITextEffect textEffect = ITextEffect.getEffect(tryGetString(dialogTextObject, "textEffect"));

        return new DialogText(title, imageGroup, text, soundGroup, textEffect);
    }

    private static SoundGroup getSoundGroup(JsonElement soundElement) {
        String soundId = null;
        float volume = 1.0f;
        float pitch = 1.0f;
        if (soundElement != null) {
            if (soundElement.isJsonPrimitive()) {
                soundId = soundElement.getAsString();
            } else if (soundElement.isJsonObject()) {
                JsonObject soundObject = soundElement.getAsJsonObject();
                soundId = soundObject.get("sound").getAsString();
                volume = tryGetFloat(soundObject, "volume", volume);
                pitch = tryGetFloat(soundObject, "pitch", pitch);
            }
            return new SoundGroup(soundId, volume, pitch);
        }
        return null;
    }

    private static ImageGroup getImageGroup(JsonElement imageElement) {
        ResourceLocation atlasLocation = null;
        EvalInt x = new EvalInt(0), y = new EvalInt("(screenheight / 3 * 2) - 64");
        int width = 64, height = 64;
        float uOffset = 0, vOffset = 0;
        int uWidth = 64, vHeight = 64;
        int textureWidth = 64, textureHeight = 64;
        if (imageElement != null) {
            if (imageElement.isJsonPrimitive()) {
                atlasLocation = new ResourceLocation(imageElement.getAsString());
            } else if (imageElement.isJsonObject()) {
                JsonObject imageObject = imageElement.getAsJsonObject();
                atlasLocation = new ResourceLocation(imageObject.get("image").getAsString());
                x = EvalInt.fromJson(imageObject.get("x"));
                y = EvalInt.fromJson(imageObject.get("y"));
                width = tryGetInt(imageObject, "width", width);
                height = tryGetInt(imageObject, "height", height);
                uOffset = tryGetFloat(imageObject, "uOffset");
                vOffset = tryGetFloat(imageObject, "vOffset");
                uWidth = tryGetInt(imageObject, "uWidth", uWidth);
                vHeight = tryGetInt(imageObject, "vHeight", vHeight);
                textureWidth = tryGetInt(imageObject, "textureWidth", textureWidth);
                textureHeight = tryGetInt(imageObject, "textureHeight", textureHeight);
            }
            return new ImageGroup(atlasLocation, x, y, width, height, uOffset, vOffset, uWidth, vHeight, textureWidth, textureHeight);
        }
        return null;
    }
}
