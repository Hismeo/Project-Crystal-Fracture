package org.hismeo.nuquest.core.dialog.context.config;

import org.hismeo.nuquest.core.dialog.context.config.components.button.ActionButtonConfig;
import org.hismeo.nuquest.core.dialog.context.config.components.button.FlipButtonConfig;

public class DialogConfig {
    Boolean pauseScreen;
    BackgroundConfig backgroundConfig;
    TitleConfig titleConfig;
    TextConfig textConfig;
    ImageConfig[] imageConfigs;
    ActionButtonConfig[] actionButtonConfigs;
    FlipButtonConfig flipButtonConfig;

    public DialogConfig() {
        this.pauseScreen = false;
        this.backgroundConfig = null;
        this.titleConfig = null;
        this.textConfig = null;
        this.imageConfigs = null;
        this.actionButtonConfigs = null;
        this.flipButtonConfig = null;
    }

    public DialogConfig(Boolean pauseScreen, BackgroundConfig backgroundConfig, TitleConfig titleConfig, TextConfig textConfig, ImageConfig[] imageConfigs, ActionButtonConfig[] actionButtonConfigs, FlipButtonConfig flipButtonConfig) {
        this.pauseScreen = pauseScreen;
        this.backgroundConfig = backgroundConfig;
        this.titleConfig = titleConfig;
        this.textConfig = textConfig;
        this.imageConfigs = imageConfigs;
        this.actionButtonConfigs = actionButtonConfigs;
        this.flipButtonConfig = flipButtonConfig;
    }

    public DialogConfig replace(DialogConfig dialogConfig) {
        Boolean newPause = dialogConfig.pauseScreen;
        BackgroundConfig newBackground = dialogConfig.backgroundConfig;
        TitleConfig newTitle = dialogConfig.titleConfig;
        TextConfig newText = dialogConfig.textConfig;
        ImageConfig[] newImage = dialogConfig.imageConfigs;
        ActionButtonConfig[] newAction = dialogConfig.actionButtonConfigs;
        FlipButtonConfig newFlip = dialogConfig.flipButtonConfig;
        pauseScreen = newPause;
        if (newBackground != null) backgroundConfig = newBackground;
        if (newTitle != null) titleConfig = newTitle;
        if (newText != null) textConfig = newText;
        if (newImage != null) imageConfigs = newImage;
        if (newAction != null) actionButtonConfigs = newAction;
        if (newFlip != null) flipButtonConfig = newFlip;
        return this;
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

    public FlipButtonConfig getFlipButtonConfig() {
        return flipButtonConfig;
    }
}
