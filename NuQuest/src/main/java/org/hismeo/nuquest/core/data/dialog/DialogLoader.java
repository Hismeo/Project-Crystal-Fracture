package org.hismeo.nuquest.core.data.dialog;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;
import org.hismeo.nuquest.NuQuest;
import org.hismeo.nuquest.api.dialog.ITextEffect;
import org.hismeo.nuquest.core.dialog.context.config.*;
import org.hismeo.nuquest.core.dialog.context.config.components.WidgetSpritesConfig;
import org.hismeo.nuquest.core.dialog.context.config.components.button.ActionButtonConfig;
import org.hismeo.nuquest.core.dialog.context.config.components.button.FlipButtonConfig;
import org.hismeo.nuquest.core.dialog.context.config.group.ImageGroup;
import org.hismeo.nuquest.core.dialog.context.config.group.SoundGroup;
import org.hismeo.nuquest.core.dialog.context.DialogActionData;
import org.hismeo.nuquest.core.dialog.context.DialogDefinition;
import org.hismeo.nuquest.api.dialog.IAction;
import org.hismeo.nuquest.core.dialog.context.text.DialogText;
import org.hismeo.nuquest.core.dialog.context.text.effect.NoneEffect;
import org.jetbrains.annotations.NotNull;

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
            if (key.getPath().equals("dialog_config")) continue;
            JsonElement element = entry.getValue();

            try {
                JsonObject jsonObject = element.getAsJsonObject();
                String dialogPath = jsonObject.get("dialogId").getAsString();
                String dialogId = "%s:%s".formatted(key.getNamespace(), dialogPath);
                JsonArray textsArray = jsonObject.getAsJsonArray("dialogTexts");
                JsonArray actionsDatasArray = jsonObject.getAsJsonArray("dialogActionDatas");
                DialogText[] texts = new DialogText[textsArray.size()];
                DialogActionData[] actionDatas = new DialogActionData[actionsDatasArray.size()];

                for (int i = 0; i < textsArray.size(); i++) {
                    texts[i] = getDialogText(textsArray.get(i));
                }

                for (int i = 0; i < actionsDatasArray.size(); i++) {
                    actionDatas[i] = getDialogActionData(actionsDatasArray.get(i));
                }
                JsonElement configElement = tryGet(jsonObject, "dialogConfig");
                DialogConfig dialogConfig = null;
                if (configElement != null){
                    dialogConfig = getDialogConfig(configElement);
                }
                DialogDefinition definition = new DialogDefinition(dialogId, texts, actionDatas, dialogConfig);
                DialogManager.dataRegister(dialogId, definition);
            } catch (NullPointerException e) {
                NuQuest.LOGGER.error("[DialogReloadListener] Failed to load dialog: {} - {}", key, e.getMessage(), e);
            }
        }
        NuQuest.LOGGER.debug("[DialogReloadListener] Loaded {} dialogs.", DialogManager.getDialogMapView().size());
    }

    private static @NotNull DialogText getDialogText(JsonElement textElement) {
        JsonObject textObject = textElement.getAsJsonObject();
        String title = tryGetString(textObject, "title");
        JsonElement imageGroupElement = tryGet(textObject, "imageGroup");
        ImageGroup[] imageGroups = new ImageGroup[]{};
        if (imageGroupElement != null) {
            if (imageGroupElement.isJsonObject()) {
                imageGroups = new ImageGroup[]{getImageGroup(imageGroupElement)};
            } else {
                JsonArray imageGroupArray = imageGroupElement.getAsJsonArray();
                ImageGroup[] temporaryImageGroups = new ImageGroup[imageGroupArray.size()];
                for (int i = 0; i < imageGroupArray.size(); i++) {
                    temporaryImageGroups[i] = getImageGroup(imageGroupArray.get(i));
                }
                imageGroups = temporaryImageGroups;
            }
        }
        String text = tryGetString(textObject, "text");
        SoundGroup soundGroup = getSoundGroup(tryGet(textObject, "soundGroup"));
        JsonElement effectElement = tryGet(textObject, "textEffect");
        ITextEffect textEffect = getTextEffect(effectElement);
        return new DialogText(title, imageGroups, text, soundGroup, textEffect);
    }

    private static DialogActionData getDialogActionData(JsonElement actionDataElement) {
        JsonObject actionDataObject = actionDataElement.getAsJsonObject();
        String message = tryGetString(actionDataObject, "message");

        JsonArray actionsArray = actionDataObject.getAsJsonArray("actions");
        IAction[] actions = new IAction[actionsArray.size()];
        for (int i = 0; i < actionsArray.size(); i++) {
            JsonObject jsonObject = actionsArray.get(i).getAsJsonObject();
            actions[i] = IAction.getAction(tryGetString(jsonObject, "name"));
            JsonElement params = tryGet(jsonObject, "params");
            if (params != null) {
                actions[i].parseJson(params.getAsJsonObject());
            }
        }
        return new DialogActionData(message, actions);
    }

    static DialogConfig getDialogConfig(JsonElement configElement) {
        JsonObject configObject = configElement.getAsJsonObject();
        boolean pauseScreen = tryGetBoolean(configObject, "pauseScreen", false);
        BackgroundConfig backgroundConfig = getBackgroundConfig(tryGet(configObject, "backgroundConfig"));
        TitleConfig titleConfig = getTitleConfig(tryGet(configObject, "titleConfig"));
        TextConfig textConfig = getTextConfig(tryGet(configObject, "textConfig"));
        JsonArray imageConfigArray = configObject.getAsJsonArray("imageConfigs");
        ImageConfig[] imageConfigs = new ImageConfig[imageConfigArray.size()];
        for (int i = 0; i < imageConfigs.length; i++) {
            imageConfigs[i] = getImageConfig(imageConfigArray.get(i));
        }
        JsonArray actionButtonConfigArray = configObject.getAsJsonArray("actionButtonConfigs");
        ActionButtonConfig[] actionButtonConfigs = new ActionButtonConfig[actionButtonConfigArray.size()];
        for (int i = 0; i < actionButtonConfigs.length; i++) {
            actionButtonConfigs[i] = getActionButtonConfig(actionButtonConfigArray.get(i));
        }
        FlipButtonConfig flipButtonConfig = getFlipButtonConfig(tryGet(configObject, "flipButtonConfig"));
        return new DialogConfig(pauseScreen, backgroundConfig, titleConfig, textConfig, imageConfigs, actionButtonConfigs, flipButtonConfig);
    }

    private static BackgroundConfig getBackgroundConfig(JsonElement backgroundElement) {
        EvalInt x = new EvalInt(0), y = new EvalInt("@screenheight / 3 * 2"), width = new EvalInt("@screenwidth"), height = new EvalInt("@screenheight");
        int colorFrom = -1073741824, colorTo = -1073741824;
        if (backgroundElement != null) {
            JsonObject backgroundObject = backgroundElement.getAsJsonObject();
            x = EvalInt.fromJson(tryGet(backgroundObject, "x"));
            y = EvalInt.fromJson(tryGet(backgroundObject, "y"), y);
            width = EvalInt.fromJson(tryGet(backgroundObject, "width"), width);
            height = EvalInt.fromJson(tryGet(backgroundObject, "height"), height);
            colorFrom = tryGetInt(backgroundObject, "colorFrom", colorFrom);
            colorTo = tryGetInt(backgroundObject, "colorTo", colorTo);
        }
        return new BackgroundConfig(x, y, width, height, colorFrom, colorTo);
    }

    private static TitleConfig getTitleConfig(JsonElement titleElement) {
        EvalInt x = new EvalInt(10), y = new EvalInt("@screenheight / 3 * 2 + 4");
        int color = 0xFFFFFFFF;
        boolean useUnderline = true;
        TitleConfig.UnderlineConfig underlineConfig = null;
        if (titleElement != null) {
            JsonObject titleObject = titleElement.getAsJsonObject();
            x = EvalInt.fromJson(tryGet(titleObject, "x"), x);
            y = EvalInt.fromJson(tryGet(titleObject, "y"), y);
            color = tryGetInt(titleObject, "color", color);
            useUnderline = tryGetBoolean(titleObject, "useUnderline", useUnderline);
            underlineConfig = getUnderlineConfig(tryGet(titleObject, "underlineConfig"));
        }
        return new TitleConfig(x, y, color, useUnderline, underlineConfig);
    }

    private static TitleConfig.UnderlineConfig getUnderlineConfig(JsonElement underlineElement) {
        EvalInt minX = new EvalInt(8), minY = new EvalInt("@screenheight / 3 * 2 + 14"),
                maxX = new EvalInt("@textwidth + 12"), maxY = new EvalInt("@screenheight / 3 * 2 + 15");
        int color = 0xFFFFFFFF;
        if (underlineElement != null) {
            JsonObject underlineObject = underlineElement.getAsJsonObject();
            minX = EvalInt.fromJson(tryGet(underlineObject, "minX"));
            minY = EvalInt.fromJson(tryGet(underlineObject, "minY"), minY);
            maxX = EvalInt.fromJson(tryGet(underlineObject, "maxX"), maxX);
            maxY = EvalInt.fromJson(tryGet(underlineObject, "maxY"), maxY);
            color = tryGetInt(underlineObject, "color", color);
        }
        return new TitleConfig.UnderlineConfig(minX, minY, maxX, maxY, color);
    }

    private static TextConfig getTextConfig(JsonElement textElement) {
        EvalInt x = new EvalInt(20), y = new EvalInt("@screenheight + 29 + @index * 9");
        int color = 0xFFFFFFFF;
        if (textElement != null) {
            JsonObject textObject = textElement.getAsJsonObject();
            x = EvalInt.fromJson(tryGet(textObject, "x"), x);
            y = EvalInt.fromJson(tryGet(textObject, "y"), y);
            color = tryGetInt(textObject, "color", color);
        }
        return new TextConfig(x, y, color);
    }

    private static ActionButtonConfig getActionButtonConfig(JsonElement actionElement) {
        EvalInt x = new EvalInt("@screenwidth-100"), y = new EvalInt("@screenheight / 3 * 2 - (@index + 1) * 30"),
                width = new EvalInt(100), height = new EvalInt(20);
        if (actionElement != null) {
            JsonObject actionButtonObject = actionElement.getAsJsonObject();
            x = EvalInt.fromJson(tryGet(actionButtonObject, "x"), x);
            y = EvalInt.fromJson(tryGet(actionButtonObject, "y"), y);
            width = EvalInt.fromJson(tryGet(actionButtonObject, "width"), width);
            height = EvalInt.fromJson(tryGet(actionButtonObject, "height"), height);
        }
        return new ActionButtonConfig(x, y, width, height);
    }

    private static FlipButtonConfig getFlipButtonConfig(JsonElement flipElement) {
        WidgetSpritesConfig widgetSpritesConfig = new WidgetSpritesConfig("widget/cross_button", "widget/cross_button_highlighted");
        EvalInt x = new EvalInt("@screenwidth-40"), y = new EvalInt("@screenheight-40"),
                width = new EvalInt(20), height = new EvalInt(20);
        if (flipElement != null) {
            JsonObject flipObject = flipElement.getAsJsonObject();
            widgetSpritesConfig = getWidgetSpritesConfig(tryGet(flipObject, "widgetSpritesConfig"));
            x = EvalInt.fromJson(tryGet(flipObject, "x"), x);
            y = EvalInt.fromJson(tryGet(flipObject, "y"), y);
            width = EvalInt.fromJson(tryGet(flipObject, "width"), width);
            height = EvalInt.fromJson(tryGet(flipObject, "height"), height);
        }
        return new FlipButtonConfig(widgetSpritesConfig, x, y, width, height);
    }

    private static WidgetSpritesConfig getWidgetSpritesConfig(JsonElement spritesElement) {
        String enabled = null, disabled = null, enabledFocused = null, disabledFocused = null;
        if (spritesElement != null) {
            JsonObject spritesObject = spritesElement.getAsJsonObject();
            enabled = tryGetString(spritesObject, "enabled");
            disabled = tryGetString(spritesObject, "disabled");
            enabledFocused = tryGetString(spritesObject, "enabledFocused");
            disabledFocused = tryGetString(spritesObject, "disabledFocused");
            if (disabled == null) disabled = enabled;
            if (disabledFocused == null) disabledFocused = enabledFocused;
        }
        return new WidgetSpritesConfig(enabled, disabled, enabledFocused, disabledFocused);
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

    private static ITextEffect getTextEffect(JsonElement jsonElement) {
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

    private static ImageGroup getImageGroup(JsonElement imageElement) {
        ResourceLocation atlasLocation = null;
        ImageConfig imageConfig = null;
        String imagePlaceType = "NONE";
        if (imageElement != null) {
            if (imageElement.isJsonPrimitive()) {
                atlasLocation = ResourceLocation.tryParse(imageElement.getAsString());
            } else if (imageElement.isJsonObject()) {
                JsonObject imageObject = imageElement.getAsJsonObject();
                atlasLocation = ResourceLocation.tryParse(imageObject.get("image").getAsString());
                JsonElement imageConfigElement = tryGet(imageObject, "imageConfig");
                if (imageConfigElement != null) {
                    imageConfig = getImageConfig(imageConfigElement);
                }
                imagePlaceType = tryGetString(imageObject, "imagePlaceType", imagePlaceType);
            }
            return new ImageGroup(atlasLocation, imageConfig, ImagePlaceType.valueOf(imagePlaceType));
        }
        return null;
    }

    private static ImageConfig getImageConfig(JsonElement imageConfigElement) {
        EvalInt x, y = new EvalInt("(screenheight / 3 * 2) - 64");
        int width = 64, height = 64;
        float uOffset, vOffset;
        int uWidth = 64, vHeight = 64;
        int textureWidth = 64, textureHeight = 64;
        JsonObject imageConfigObject = imageConfigElement.getAsJsonObject();
        x = EvalInt.fromJson(imageConfigObject.get("x"));
        y = EvalInt.fromJson(imageConfigObject.get("y"), y);
        width = tryGetInt(imageConfigObject, "width", width);
        height = tryGetInt(imageConfigObject, "height", height);
        uOffset = tryGetFloat(imageConfigObject, "uOffset");
        vOffset = tryGetFloat(imageConfigObject, "vOffset");
        uWidth = tryGetInt(imageConfigObject, "uWidth", uWidth);
        vHeight = tryGetInt(imageConfigObject, "vHeight", vHeight);
        textureWidth = tryGetInt(imageConfigObject, "textureWidth", textureWidth);
        textureHeight = tryGetInt(imageConfigObject, "textureHeight", textureHeight);
        return new ImageConfig(x, y, width, height, uOffset, vOffset, uWidth, vHeight, textureWidth, textureHeight);
    }
}
