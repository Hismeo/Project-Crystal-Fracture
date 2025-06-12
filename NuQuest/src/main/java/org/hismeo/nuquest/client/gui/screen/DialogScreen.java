package org.hismeo.nuquest.client.gui.screen;

import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.hismeo.crystallib.util.client.MinecraftUtil;
import org.hismeo.nuquest.api.dialog.IAction;
import org.hismeo.nuquest.api.dialog.ITextEffect;
import org.hismeo.nuquest.client.gui.component.ActionButton;
import org.hismeo.nuquest.core.data.dialog.DialogManager;
import org.hismeo.nuquest.core.dialog.context.config.*;
import org.hismeo.nuquest.core.dialog.context.config.components.button.ActionButtonConfig;
import org.hismeo.nuquest.core.dialog.context.config.components.button.FlipButtonConfig;
import org.hismeo.nuquest.core.dialog.context.config.group.ImageGroup;
import org.hismeo.nuquest.core.dialog.context.config.group.SoundGroup;
import org.hismeo.nuquest.core.dialog.context.DialogActionData;
import org.hismeo.nuquest.core.dialog.context.DialogDefinition;
import org.hismeo.nuquest.core.dialog.context.text.DialogText;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.hismeo.crystallib.util.client.MinecraftUtil.getLevel;

@SuppressWarnings("unused")
@OnlyIn(Dist.CLIENT)
public class DialogScreen extends Screen {
    // TODO: filp按钮纹理替换
    private static final WidgetSprites CROSS_BUTTON_SPRITES = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("widget/cross_button"), ResourceLocation.withDefaultNamespace("widget/cross_button_highlighted")
    );
    private final Map<String, Number> numberVarMap = new HashMap<>();
    private final Map<String, String> stringVarMap = new HashMap<>();
    protected Button flipButton;
    protected final List<ActionButton> actionButtons = new ArrayList<>();
    private final String dialogId;
    private final DialogDefinition dialogDefinition;
    private final DialogConfig dialogConfig;
    private final boolean pauseScreen;
    private final BackgroundConfig backgroundConfig;
    private final TitleConfig titleConfig;
    private final TextConfig textConfig;
    private final ImageConfig[] imageConfigs;
    private final ActionButtonConfig[] actionButtonConfigs;
    private final FlipButtonConfig flipButtonConfig;
    private final DialogActionData[] dialogActionDatas;
    private final DialogText[] dialogTexts;
    private String title;
    private ImageGroup[] imageGroups;
    private ImageGroup[] imageFront;
    private ImageGroup[] imageAfterBackground;
    private ImageGroup[] imageNone;
    private ImageGroup[] imageAfterTitle;
    private ImageGroup[] imageAfterText;
    private ImageGroup[] imageAfterButton;
    private ImageGroup[] imageLast;
    private String originText;
    private List<String> splitText;
    private SoundGroup soundGroup;
    private ITextEffect textEffect;
    private final int maxPage;
    private int page;
    private int initPage = -1;

    public DialogScreen(DialogDefinition dialogDefinition) {
        this(dialogDefinition, 0);
    }

    public DialogScreen(DialogDefinition dialogDefinition, int page) {
        super(CommonComponents.EMPTY);
        this.page = page;
        this.maxPage = dialogDefinition.dialogTexts().length;
        this.dialogDefinition = dialogDefinition;
        if (dialogDefinition.dialogConfig() != null) {
            this.dialogConfig = dialogDefinition.dialogConfig();
        } else {
            this.dialogConfig = DialogManager.getGlobalDialogConfig();
        }
        this.pauseScreen = dialogConfig.pauseScreen();
        this.backgroundConfig = dialogConfig.backgroundConfig();
        this.titleConfig = dialogConfig.titleConfig();
        this.textConfig = dialogConfig.textConfig();
        this.imageConfigs = dialogConfig.imageConfigs();
        this.actionButtonConfigs = dialogConfig.actionButtonConfigs();
        this.flipButtonConfig = dialogConfig.flipButtonConfig();
        this.dialogActionDatas = dialogDefinition.dialogActionDatas();
        this.dialogId = dialogDefinition.dialogId();
        this.dialogTexts = dialogDefinition.dialogTexts();
    }

    @Override
    protected void init() {
        this.flipButton = this.addWidget(this.flipButtonConfig.getFlipButton(this::tryFlip, numberVarMap));

        if (dialogActionDatas != null) {
            this.actionButtons.clear();
            for (int i = 0; i < this.dialogActionDatas.length; i++) {
                ActionButton actionButton = getActionButton(i);
                actionButton.hidden = true;
                this.addWidget(actionButton);
                this.actionButtons.add(actionButton);
            }
        }

        this.initPage();
    }

    @Override
    public void tick() {
        if (!canFlip()) {
            flipButton.visible = false;
            actionButtons.forEach(button -> button.hidden = false);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        arrayBlitImage(this.imageFront, guiGraphics);
        this.backgroundConfig.drawBackground(guiGraphics, numberVarMap);
        arrayBlitImage(this.imageAfterBackground, guiGraphics);

        arrayBlitImage(this.imageNone, guiGraphics);

        if (title != null) {
            String title = this.evalStringVar(Component.translatable(this.title));
            this.titleConfig.drawTitle(title, this.font, guiGraphics, numberVarMap);
        }
        arrayBlitImage(this.imageAfterTitle, guiGraphics);

        for (int i = 0; i < this.splitText.size(); i++) {
            String text = this.evalStringVar(Component.translatable(this.splitText.get(i)));
            this.textConfig.drawString(i, text, this.font, guiGraphics, numberVarMap);
        }
        arrayBlitImage(this.imageAfterText, guiGraphics);

        this.flipButton.render(guiGraphics, mouseX, mouseY, partialTick);
        this.actionButtons.forEach(actionButton -> actionButton.render(guiGraphics, mouseX, mouseY, partialTick));
        arrayBlitImage(this.imageAfterButton, guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        arrayBlitImage(this.imageLast, guiGraphics);
    }

    private void arrayBlitImage(ImageGroup[] imageGroups, GuiGraphics guiGraphics) {
        for (int i = 0; i < imageGroups.length; i++) {
            imageGroups[i].blitImage(this.imageConfigs[i], guiGraphics, this.numberVarMap);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return this.pauseScreen;
    }

    @NotNull
    private ActionButton getActionButton(int index) {
        MutableComponent translatable = Component.translatable(this.dialogActionDatas[index].message());
        return this.actionButtonConfigs[index].getActionButton(index,
                translatable,
                () -> {
                    for (IAction action : this.dialogActionDatas[index].action()) {
                        action.action(this);
                    }
                },
                numberVarMap);
    }

    protected void tryFlip(Button button) {
        if (canFlip()) {
            this.page++;
            this.initPage();
        }
    }

    protected boolean canFlip() {
        return this.page < this.maxPage - 1;
    }

    protected void initPage() {
        numberVarMap.put("@screenwidth", this.width);
        numberVarMap.put("@screenheight", this.height);

        stringVarMap.put("@playername", MinecraftUtil.getPlayer().getScoreboardName());
        if (initPage != page) {
            this.title = dialogTexts[page].title();
            this.initImage();
            this.originText = dialogTexts[page].text();
            this.soundGroup = dialogTexts[page].soundGroup();
            this.textEffect = dialogTexts[page].textEffect();
            if (soundGroup != null) soundGroup.playSound(getLevel());
            initPage = page;
        }
        this.splitText = this.splitString(originText);
    }

    protected void initImage() {
        List<ImageGroup> frontList = new ArrayList<>(), backgroundList = new ArrayList<>(), noneList = new ArrayList<>(),
                titleList = new ArrayList<>(), textList = new ArrayList<>(), buttonList = new ArrayList<>(), lastList = new ArrayList<>();
        this.imageGroups = dialogTexts[page].imageGroup();
        for (ImageGroup imageGroup : imageGroups) {
            switch (imageGroup.imagePlaceType()) {
                case FRONT -> frontList.add(imageGroup);
                case AFTER_BACKGROUND -> backgroundList.add(imageGroup);
                case NONE -> noneList.add(imageGroup);
                case AFTER_TITLE -> titleList.add(imageGroup);
                case AFTER_TEXT -> textList.add(imageGroup);
                case AFTER_BUTTON -> buttonList.add(imageGroup);
                case LAST -> lastList.add(imageGroup);
            }
        }
        this.imageFront = frontList.toArray(ImageGroup[]::new);
        this.imageAfterBackground = backgroundList.toArray(ImageGroup[]::new);
        this.imageNone = noneList.toArray(ImageGroup[]::new);
        this.imageAfterTitle = titleList.toArray(ImageGroup[]::new);
        this.imageAfterText = textList.toArray(ImageGroup[]::new);
        this.imageAfterButton = buttonList.toArray(ImageGroup[]::new);
        this.imageLast = lastList.toArray(ImageGroup[]::new);
    }

    private List<String> splitString(String string) {
        List<String> splits = new ArrayList<>();
        List<String> lineBreaksSplits = List.of(string.split("\n"));
        StringSplitter fontSplitter = this.font.getSplitter();
        for (String split : lineBreaksSplits) {
            fontSplitter.splitLines(split, this.width - 40, Style.EMPTY).forEach(
                    formattedText -> splits.add(formattedText.getString())
            );
        }
        return splits;
    }

    private String evalStringVar(Component translateKey) {
        AtomicReference<String> replaceKey = new AtomicReference<>();
        stringVarMap.forEach((name, var) -> replaceKey.set(translateKey.getString().replace(name, var)));
        return replaceKey.get();
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    protected void renderMenuBackground(@NotNull GuiGraphics guiGraphics, int x, int y, int width, int height) {
    }
}

