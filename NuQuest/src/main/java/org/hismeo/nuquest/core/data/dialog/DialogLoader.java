package org.hismeo.nuquest.core.data.dialog;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.hismeo.nuquest.NuQuest;
import org.hismeo.nuquest.api.dialog.text.ITextEffect;
import org.hismeo.nuquest.core.dialog.SoundGroup;
import org.hismeo.nuquest.core.dialog.context.DialogDefinition;
import org.hismeo.nuquest.core.dialog.text.DialogText;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        String image = tryGetString(dialogTextObject, "image");
        String text = tryGetString(dialogTextObject, "text");

        SoundGroup soundGroup = getSoundGroup(tryGet(dialogTextObject, "soundEvent"));
        ITextEffect textEffect = ITextEffect.getEffect(tryGetString(dialogTextObject, "textEffect"));

        return new DialogText(title, image == null ? null : new ResourceLocation(image), text, soundGroup, textEffect);
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
                volume = tryGetFloat(soundObject, "volume");
                pitch = tryGetFloat(soundObject, "pitch");
            }
            return new SoundGroup(soundId, volume, pitch);
        }
        return null;
    }

    private static float tryGetFloat(JsonObject jsonObject, String name) {
        return jsonObject.get(name) == null ? 0.0f : jsonObject.get(name).getAsFloat();
    }

    private static String tryGetString(JsonObject jsonObject, String name) {
        return jsonObject.get(name) == null ? null : jsonObject.get(name).getAsString();
    }

    private static JsonElement tryGet(JsonObject jsonObject, String name) {
        if (jsonObject.has(name)) {
            return jsonObject.get(name);
        }
        return null;
    }
}
