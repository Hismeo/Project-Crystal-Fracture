package org.hismeo.nuquest.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.hismeo.crystallib.util.client.MinecraftUtil;
import org.hismeo.nuquest.api.dialog.IAction;
import org.hismeo.nuquest.api.dialog.ITextEffect;
import org.hismeo.nuquest.client.gui.component.ActionButton;
import org.hismeo.nuquest.core.data.dialog.DialogManager;
import org.hismeo.nuquest.core.dialog.context.DialogActionData;
import org.hismeo.nuquest.core.dialog.context.DialogDefinition;
import org.hismeo.nuquest.core.dialog.context.config.*;
import org.hismeo.nuquest.core.dialog.context.config.components.button.ActionButtonConfig;
import org.hismeo.nuquest.core.dialog.context.config.components.button.ImageButtonConfig;
import org.hismeo.nuquest.core.dialog.context.config.group.ImageGroup;
import org.hismeo.nuquest.core.dialog.context.config.group.SoundGroup;
import org.hismeo.nuquest.core.dialog.context.config.group.TextGroup;
import org.hismeo.nuquest.core.dialog.context.text.DialogText;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.hismeo.crystallib.util.client.MinecraftUtil.getLevel;

@SuppressWarnings("unused")
@OnlyIn(Dist.CLIENT)
public class DialogScreen extends Screen {
    protected final EnumMap<ImagePlaceType, List<ImageGroup>> imageGroupsMap = new EnumMap<>(ImagePlaceType.class);
    protected final List<ActionButton> actionButtons = new ArrayList<>();
    //TODO 将变量的map进行封装
    private final Map<String, Number> numberVarMap = new HashMap<>();
    private final Map<String, String> stringVarMap = new HashMap<>();
    private final String dialogId;
    private final DialogDefinition dialogDefinition;
    private final DialogActionData[] dialogActionDatas;
    private final DialogText[] dialogTexts;
    private final int maxPage;
    protected Button flipButton;
    private DialogConfig dialogConfig;
    private Boolean pauseScreen;
    private BackgroundConfig backgroundConfig;
    private TitleConfig titleConfig;
    private TextConfig[] textConfigs;
    private ImageConfig[] imageConfigs;
    private ActionButtonConfig[] actionButtonConfigs;
    private ImageButtonConfig imageButtonConfig;
    private String title;
    private TextGroup[] originText;
    private SoundGroup soundGroup;
    private ITextEffect textEffect;
    private int page;
    private int initPage = -1;
    private int imageCount = 0;

    public DialogScreen(DialogDefinition dialogDefinition) {
        this(dialogDefinition, 0);
    }

    public DialogScreen(DialogDefinition dialogDefinition, int page) {
        super(CommonComponents.EMPTY);
        this.page = page;
        this.dialogDefinition = dialogDefinition.copy();
        this.maxPage = this.dialogDefinition.dialogTexts().length;
        this.dialogActionDatas = this.dialogDefinition.dialogActionDatas();
        this.dialogId = this.dialogDefinition.dialogId();
        this.dialogTexts = this.dialogDefinition.dialogTexts();
        this.initConfig();
    }

    private void initConfig() {
        final DialogConfig globalConfig = DialogManager.getGlobalDialogConfig();
        final DialogConfig definitionConfig = dialogDefinition.dialogConfig();

        this.dialogConfig = globalConfig.mergeData(definitionConfig);
        this.pauseScreen = dialogConfig.isPauseScreen();
        this.backgroundConfig = dialogConfig.getBackgroundConfig();
        this.titleConfig = dialogConfig.getTitleConfig();
        this.textConfigs = dialogConfig.getTextConfigs();
        this.imageConfigs = dialogConfig.getImageConfigs();
        this.actionButtonConfigs = dialogConfig.getActionButtonConfigs();
        this.imageButtonConfig = dialogConfig.getFlipButtonConfig();
    }

    @Override
    protected void init() {
        this.initPage();
        this.flipButton = this.addWidget(this.imageButtonConfig.getImageButton(this::tryFlip, numberVarMap));

        if (dialogActionDatas != null) {
            this.actionButtons.clear();
            for (int i = 0; i < this.dialogActionDatas.length; i++) {
                ActionButton actionButton = getActionButton(i);
                actionButton.hidden = true;
                this.addWidget(actionButton);
                this.actionButtons.add(actionButton);
            }
        }
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
        imageCount = 0;
        blitByType(ImagePlaceType.FRONT, guiGraphics);
        this.backgroundConfig.drawBackground(guiGraphics, numberVarMap);
        blitByType(ImagePlaceType.AFTER_BACKGROUND, guiGraphics);

        blitByType(ImagePlaceType.NONE, guiGraphics);

        if (title != null) {
            Component title = this.evalStringVar(Component.translatable(this.title));
            this.titleConfig.drawTitle(title, this.font, guiGraphics, numberVarMap);
        }
        blitByType(ImagePlaceType.AFTER_TITLE, guiGraphics);

        for (int index = 0; index < originText.length; index++) {
            TextGroup textGroup = originText[index];
            Component text = this.evalStringVar(Component.translatable(textGroup.text()));
            TextConfig textConfig = textConfigs[Math.min(index, textConfigs.length - 1)];
            textGroup.draw(textConfig, index, text, font, guiGraphics, numberVarMap);
        }


        blitByType(ImagePlaceType.AFTER_TEXT, guiGraphics);

        this.flipButton.render(guiGraphics, mouseX, mouseY, partialTick);
        this.actionButtons.forEach(actionButton -> actionButton.render(guiGraphics, mouseX, mouseY, partialTick));
        blitByType(ImagePlaceType.AFTER_BACKGROUND, guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        blitByType(ImagePlaceType.LAST, guiGraphics);
    }

    private void blitByType(ImagePlaceType type, GuiGraphics guiGraphics) {
        List<ImageGroup> groups = imageGroupsMap.get(type);
        if (groups == null || groups.isEmpty()) return;
        for (ImageGroup group : groups) {
            ImageConfig cfg = imageConfigs[Math.min(imageCount, imageConfigs.length - 1)];
            imageCount++;
            group.blitImage(cfg, guiGraphics, numberVarMap);
        }
    }

    protected void initImage() {
        for (ImageGroup img : dialogTexts[page].imageGroup()) {
            imageGroupsMap.computeIfAbsent(img.imagePlaceType(), k -> new ArrayList<>()).add(img);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return this.pauseScreen;
    }

    @NotNull
    private ActionButton getActionButton(int index) {
        MutableComponent translatable = Component.translatable(this.dialogActionDatas[index].message());
        ActionButtonConfig actionConfig = actionButtonConfigs[Math.min(index, actionButtonConfigs.length - 1)];
        return actionConfig.getActionButton(index,
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
            this.originText = dialogTexts[page].textGroup();
            this.soundGroup = dialogTexts[page].soundGroup();
            this.textEffect = dialogTexts[page].textEffect();
            if (soundGroup != null) soundGroup.playSound(getLevel());
            initPage = page;
        }
    }

    private Component evalStringVar(Component translateKey) {
        AtomicReference<String> replaceKey = new AtomicReference<>();
        stringVarMap.forEach((name, var) -> replaceKey.set(translateKey.getString().replace(name, var)));
        return Component.literal(replaceKey.get());
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    protected void renderMenuBackground(@NotNull GuiGraphics guiGraphics, int x, int y, int width, int height) {
    }
}

