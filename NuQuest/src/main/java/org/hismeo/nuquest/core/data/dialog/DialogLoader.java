package org.hismeo.nuquest.core.data.dialog;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;
import org.hismeo.nuquest.NuQuest;
import org.hismeo.nuquest.api.dialog.ITextEffect;
import org.hismeo.nuquest.core.dialog.ImageGroup;
import org.hismeo.nuquest.core.dialog.SoundGroup;
import org.hismeo.nuquest.core.dialog.context.DialogActionData;
import org.hismeo.nuquest.core.dialog.context.DialogDefinition;
import org.hismeo.nuquest.api.dialog.IAction;
import org.hismeo.nuquest.core.dialog.context.text.DialogText;
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
            ResourceLocation key = entry.getKey();
            JsonElement element = entry.getValue();

            try {
                JsonObject jsonObject = element.getAsJsonObject();
                String dialoguePath = jsonObject.get("dialogueId").getAsString();
                String dialogueId = "%s:%s".formatted(key.getNamespace(), dialoguePath);
                JsonArray textsArray = jsonObject.getAsJsonArray("dialogTexts");
                JsonArray actionsDatasArray = jsonObject.getAsJsonArray("dialogActionDatas");

                List<DialogText> texts = new ArrayList<>();
                for (JsonElement textElement : textsArray) {
                    DialogText text = getDialogText(textElement);
                    texts.add(text);
                }

                List<DialogActionData> actionDatas = new ArrayList<>();
                for (JsonElement textElement : actionsDatasArray) {
                    DialogActionData actionData = getDialogActionData(textElement);
                    actionDatas.add(actionData);
                }

                DialogDefinition definition = new DialogDefinition(dialogueId, texts.toArray(DialogText[]::new), actionDatas.toArray(DialogActionData[]::new));
                DialogManager.dataRegister(dialogueId, definition);
            } catch (NullPointerException e) {
                NuQuest.LOGGER.error("[DialogReloadListener] Failed to load dialogue: {} - {}", key, e.getMessage(), e);
            }
        }
        NuQuest.LOGGER.debug("[DialogReloadListener] Loaded {} dialogues.", DialogManager.getDialogueMapView().size());
    }

    private static @NotNull DialogText getDialogText(JsonElement textElement) {
        JsonObject textObject = textElement.getAsJsonObject();
        String title = tryGetString(textObject, "title");
        ImageGroup imageGroup = getImageGroup(tryGet(textObject, "imageGroup"));
        String text = tryGetString(textObject, "text");
        SoundGroup soundGroup = getSoundGroup(tryGet(textObject, "soundGroup"));
        JsonObject effectObject = textObject.get("textEffect").getAsJsonObject();
        ITextEffect textEffect = ITextEffect.getEffect(tryGetString(effectObject, "name"));
        JsonElement params = tryGet(textObject, "params");
        if (params != null) { textEffect.parseJson(params.getAsJsonObject()); }
        return new DialogText(title, imageGroup, text, soundGroup, textEffect);
    }

    private static DialogActionData getDialogActionData(JsonElement actionDataElement) {
        JsonObject actionDataObject = actionDataElement.getAsJsonObject();
        String message = tryGetString(actionDataObject, "message");
        // TODO: 行为组
        JsonObject actionObject = actionDataObject.get("action").getAsJsonObject();
        IAction action = IAction.getAction(tryGetString(actionObject, "name"));
        JsonElement params = tryGet(actionObject, "params");
        if (params != null) { action.parseJson(params.getAsJsonObject()); }
        return new DialogActionData(message, action);
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
                y = EvalInt.fromJson(imageObject.get("y"), y);
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
