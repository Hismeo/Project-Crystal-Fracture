package org.hismeo.nuquest.core.dialog.context.config;

import org.hismeo.nuquest.core.dialog.context.config.components.button.ActionButtonConfig;
import org.hismeo.nuquest.core.dialog.context.config.components.button.FlipButtonConfig;

public record DialogConfig(boolean pauseScreen, BackgroundConfig backgroundConfig, TitleConfig titleConfig, TextConfig textConfig, ImageConfig[] imageConfigs, ActionButtonConfig[] actionButtonConfigs, FlipButtonConfig flipButtonConfig) {

}
