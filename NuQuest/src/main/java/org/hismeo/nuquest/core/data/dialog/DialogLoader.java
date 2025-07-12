package org.hismeo.nuquest.core.data.dialog;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.hismeo.nuquest.NuQuest;
import org.hismeo.nuquest.core.dialog.context.config.*;
import org.hismeo.nuquest.core.dialog.context.DialogActionData;
import org.hismeo.nuquest.core.dialog.context.DialogDefinition;
import org.hismeo.nuquest.core.dialog.context.text.DialogText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static org.hismeo.crystallib.util.JsonUtil.*;

public class DialogLoader extends SimpleJsonResourceReloadListener {
    protected static final Gson GSON = new GsonBuilder().create();

    public DialogLoader() {
        super(GSON, "nu_quest/dialog");
    }

    protected DialogLoader(Gson gson, String directory) {
        super(gson, directory);
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> object, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        DialogManager.clearDataRegister();
        for (var entry : object.entrySet()) {
            ResourceLocation key = entry.getKey();
            if (key.getPath().equals("dialog_config")) continue;
            JsonElement element = entry.getValue();

            try {
                JsonObject jsonObject = element.getAsJsonObject();
                String dialogPath = jsonObject.get("dialog_id").getAsString();
                String dialogId = "%s:%s".formatted(key.getNamespace(), dialogPath);

                DialogText[] texts = texts(jsonObject);
                DialogActionData[] actionDatas = actionDatas(jsonObject);
                DialogConfig dialogConfig = DialogConfig.fromJson(tryGet(jsonObject, "dialog_config"));

                DialogDefinition definition = new DialogDefinition(dialogId, texts, actionDatas, dialogConfig);
                DialogManager.dataRegister(dialogId, definition);
            } catch (NullPointerException e) {
                NuQuest.LOGGER.error("[DialogReloadListener] Failed to load dialog: {} - {}", key, e.getMessage(), e);
            }
        }
        NuQuest.LOGGER.info("[DialogReloadListener] Loaded {} dialogs.", DialogManager.getDialogMapView().size());
    }

    private @NotNull DialogText[] texts(JsonObject jsonObject) {
        JsonArray textsArray = jsonObject.getAsJsonArray("dialog_texts");
        DialogText[] texts = new DialogText[textsArray.size()];
        for (int i = 0; i < textsArray.size(); i++) {
            texts[i] = DialogText.fromJson(textsArray.get(i));
        }
        return texts;
    }

    private @Nullable DialogActionData[] actionDatas(JsonObject jsonObject){
        if (jsonObject != null) {
            JsonArray actionsDatasArray = jsonObject.getAsJsonArray("dialog_action_datas");
            DialogActionData[] actionDatas = new DialogActionData[actionsDatasArray.size()];
            for (int i = 0; i < actionsDatasArray.size(); i++) {
                actionDatas[i] = DialogActionData.fromJson(actionsDatasArray.get(i));
            }
            return actionDatas;
        }
        return null;
    }
}
