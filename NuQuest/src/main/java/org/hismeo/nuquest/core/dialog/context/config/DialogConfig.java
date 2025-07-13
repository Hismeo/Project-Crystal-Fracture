package org.hismeo.nuquest.core.dialog.context.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.hismeo.crystallib.api.IEmpty;
import org.hismeo.nuquest.core.dialog.context.config.components.button.ActionButtonConfig;
import org.hismeo.nuquest.core.dialog.context.config.components.button.ImageButtonConfig;

import static org.hismeo.crystallib.util.JsonUtil.tryGet;
import static org.hismeo.crystallib.util.JsonUtil.tryGetBoolean;

public class DialogConfig implements IMerge<DialogConfig>, IEmpty<DialogConfig> {
    Boolean pauseScreen;
    BackgroundConfig backgroundConfig;
    TitleConfig titleConfig;
    TextConfig textConfig;
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

    public DialogConfig(Boolean pauseScreen, BackgroundConfig backgroundConfig, TitleConfig titleConfig, TextConfig textConfig, ImageConfig[] imageConfigs, ActionButtonConfig[] actionButtonConfigs, ImageButtonConfig imageButtonConfig) {
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
            Boolean pauseScreen = tryGetBoolean(configObject, "pauseScreen");
            BackgroundConfig backgroundConfig = BackgroundConfig.fromJson(tryGet(configObject, "backgroundConfig"));
            TitleConfig titleConfig = TitleConfig.fromJson(tryGet(configObject, "titleConfig"));
            TextConfig textConfig = TextConfig.fromJson(tryGet(configObject, "textConfig"));

            JsonArray imageConfigArray = configObject.getAsJsonArray("imageConfigs");
            ImageConfig[] imageConfigs = new ImageConfig[0];
            if (imageConfigArray != null) {
                imageConfigs = new ImageConfig[imageConfigArray.size()];
                for (int i = 0; i < imageConfigs.length; i++) {
                    imageConfigs[i] = ImageConfig.fromJson(imageConfigArray.get(i));
                }
            }

            JsonArray actionButtonConfigArray = configObject.getAsJsonArray("actionButtonConfigs");
            ActionButtonConfig[] actionButtonConfigs = new ActionButtonConfig[0];
            if (actionButtonConfigArray != null) {
                actionButtonConfigs = new ActionButtonConfig[actionButtonConfigArray.size()];
                for (int i = 0; i < actionButtonConfigs.length; i++) {
                    actionButtonConfigs[i] = ActionButtonConfig.fromJson(actionButtonConfigArray.get(i));
                }
            }

            ImageButtonConfig imageButtonConfig = ImageButtonConfig.fromJson(tryGet(configObject, "flipButtonConfig"));
            return new DialogConfig(pauseScreen, backgroundConfig, titleConfig, textConfig, imageConfigs, actionButtonConfigs, imageButtonConfig);
        }
        return null;
    }

    @Override
    public DialogConfig mergeData(DialogConfig newData) {
        Boolean newPause = newData.pauseScreen;
        BackgroundConfig newBackground = newData.backgroundConfig;
        TitleConfig newTitle = newData.titleConfig;
        TextConfig newText = newData.textConfig;
        ImageConfig[] newImage = newData.imageConfigs;
        ActionButtonConfig[] newAction = newData.actionButtonConfigs;
        ImageButtonConfig newFlip = newData.imageButtonConfig;
        pauseScreen = newPause;
        if (newBackground != null) backgroundConfig = newBackground;
        if (newTitle != null) titleConfig = newTitle;
        if (newText != null) textConfig = newText;
        if (newImage != null && newImage.length > 0) imageConfigs = newImage;
        if (newAction != null && newAction.length > 0) actionButtonConfigs = newAction;
        if (newFlip != null) imageButtonConfig = newFlip;
        return this;
    }

    @Override
    public boolean anyEmpty() {
        return false;
    }

    @Override
    public boolean allEmpty() {
        return false;
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

    public TextConfig getTextConfig() {
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
