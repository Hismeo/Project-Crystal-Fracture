package org.hismeo.nuquest.core.dialog.context.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.hismeo.crystallib.api.IEmpty;
import org.hismeo.nuquest.core.IData;
import org.hismeo.nuquest.core.dialog.context.config.components.button.ActionButtonConfig;
import org.hismeo.nuquest.core.dialog.context.config.components.button.ImageButtonConfig;

import static org.hismeo.crystallib.util.JsonUtil.*;

public class DialogConfig implements IData<DialogConfig> {
    Boolean pauseScreen;
    BackgroundConfig backgroundConfig;
    TitleConfig titleConfig;
    TextConfig[] textConfig;
    ImageConfig[] imageConfigs;
    ActionButtonConfig[] actionButtonConfigs;
    ImageButtonConfig imageButtonConfig;

    public DialogConfig() {
        this.pauseScreen = null;
        this.backgroundConfig = null;
        this.titleConfig = null;
        this.textConfig = null;
        this.imageConfigs = null;
        this.actionButtonConfigs = null;
        this.imageButtonConfig = null;
    }

    public DialogConfig(Boolean pauseScreen, BackgroundConfig backgroundConfig, TitleConfig titleConfig, TextConfig[] textConfig, ImageConfig[] imageConfigs, ActionButtonConfig[] actionButtonConfigs, ImageButtonConfig imageButtonConfig) {
        this.pauseScreen = pauseScreen;
        this.backgroundConfig = backgroundConfig;
        this.titleConfig = titleConfig;
        this.textConfig = textConfig;
        this.imageConfigs = imageConfigs;
        this.actionButtonConfigs = actionButtonConfigs;
        this.imageButtonConfig = imageButtonConfig;
    }

    public static DialogConfig fromJson(JsonElement configElement) {
        if (configElement != null) {
            JsonObject configObject = configElement.getAsJsonObject();
            Boolean pauseScreen = tryGetBoolean(configObject, "pause_screen");
            BackgroundConfig backgroundConfig = BackgroundConfig.fromJson(tryGet(configObject, "background_config"));
            TitleConfig titleConfig = TitleConfig.fromJson(tryGet(configObject, "title_config"));

            TextConfig[] textConfigs = readConfigArray(configObject, "text_configs", TextConfig::fromJson, TextConfig[]::new);
            ImageConfig[] imageConfigs = readConfigArray(configObject, "image_configs", ImageConfig::fromJson, ImageConfig[]::new);
            ActionButtonConfig[] actionButtonConfigs = readConfigArray(configObject, "action_button_configs", ActionButtonConfig::fromJson, ActionButtonConfig[]::new);


            ImageButtonConfig imageButtonConfig = ImageButtonConfig.fromJson(tryGet(configObject, "flip_button_config"));
            return new DialogConfig(pauseScreen, backgroundConfig, titleConfig, textConfigs, imageConfigs, actionButtonConfigs, imageButtonConfig);
        }
        return null;
    }

    public DialogConfig copy() {
        return new DialogConfig(pauseScreen, backgroundConfig, titleConfig, textConfig, imageConfigs, actionButtonConfigs, imageButtonConfig);
    }

    @Override
    public DialogConfig mergeData(DialogConfig newData) {
        if (newData == null || newData.allEmpty()) return this;
        pauseScreen = choose(newData.pauseScreen, pauseScreen);
        backgroundConfig = mergeWithStrategy(backgroundConfig, newData.backgroundConfig, BackgroundConfig::mergeData);
        titleConfig = mergeWithStrategy(titleConfig, newData.titleConfig, TitleConfig::mergeData);
        textConfig = mergeArray(textConfig, newData.textConfig);
        imageConfigs = mergeArray(imageConfigs, newData.imageConfigs);
        actionButtonConfigs = mergeArray(actionButtonConfigs, newData.actionButtonConfigs);
        imageButtonConfig = mergeWithStrategy(imageButtonConfig, newData.imageButtonConfig, ImageButtonConfig::mergeData);
        return this;
    }

    @Override
    public boolean anyEmpty() {
        return anyEmpty(
                pauseScreen,
                backgroundConfig,
                titleConfig,
                textConfig,
                imageButtonConfig)
                || !arrayEmpty(imageConfigs)
                || !arrayEmpty(actionButtonConfigs);
    }

    @Override
    public boolean allEmpty() {
        return allEmpty(
                pauseScreen,
                backgroundConfig,
                titleConfig,
                textConfig,
                imageButtonConfig)
                && arrayEmpty(imageConfigs)
                && arrayEmpty(actionButtonConfigs);
    }

    public Boolean isPauseScreen() {
        return pauseScreen;
    }

    public BackgroundConfig getBackgroundConfig() {
        return backgroundConfig;
    }

    public TitleConfig getTitleConfig() {
        return titleConfig;
    }

    public TextConfig[] getTextConfigs() {
        return textConfig;
    }

    public ImageConfig[] getImageConfigs() {
        return imageConfigs;
    }

    public ActionButtonConfig[] getActionButtonConfigs() {
        return actionButtonConfigs;
    }

    public ImageButtonConfig getFlipButtonConfig() {
        return imageButtonConfig;
    }
}
