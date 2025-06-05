package org.hismeo.nuquest.client.gui.screen;

import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
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

import static org.hismeo.crystallib.util.client.MinecraftUtil.getLevel;

@OnlyIn(Dist.CLIENT)
public class DialogScreen extends Screen {
    private static final WidgetSprites CROSS_BUTTON_SPRITES = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("widget/cross_button"), ResourceLocation.withDefaultNamespace("widget/cross_button_highlighted")
    );
    private final Map<String, Number> varMap = new HashMap<>();
    protected Button flipButton;
    protected final List<ActionButton> actionButtons = new ArrayList<>();
    private final String dialogueId;
    private final DialogDefinition dialogDefinition;
    private final DialogActionData[] dialogActionDatas;
    private final DialogText[] dialogTexts;
    private String title;
    private ImageGroup[] imageGroups;
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
        this.dialogActionDatas = dialogDefinition.dialogActionDatas();
        this.dialogueId = dialogDefinition.dialogueId();
        this.dialogTexts = dialogDefinition.dialogTexts();
    }

    @Override
    protected void init() {
        this.flipButton = this.addRenderableWidget(new ImageButton(this.width - 40,
                this.height - 40,
                20, 20,
                CROSS_BUTTON_SPRITES,
                this::tryFlip)
        );

        if (dialogActionDatas != null) {
            this.actionButtons.clear();
            for (int i = 0; i < this.dialogActionDatas.length; i++) {
                ActionButton actionButton = getActionButton(i);
                actionButton.hidden = true;
                this.addRenderableWidget(actionButton);
                this.actionButtons.add(actionButton);
            }
        }

        this.initPage();
    }

    private @NotNull ActionButton getActionButton(int i) {
        return new ActionButton(this.width - 100, this.height / 3 * 2 - (i + 1) * 30,
                100, 20,
                Component.translatable(this.dialogActionDatas[i].message()),
                () -> {
                    for (IAction action : this.dialogActionDatas[i].action()) {
                        action.action(this);
                    }
                }
        );
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
        int dialogueHeight = this.height / 3 * 2;
        guiGraphics.fillGradient(0, dialogueHeight, this.width, this.height, -1073741824, -1073741824);

        if (this.imageGroups != null) {
            for (ImageGroup imageGroup : this.imageGroups) {
                if (imageGroup.hasImage()) imageGroup.blitImage(guiGraphics, varMap);
            }
        }

        if (title != null) {
            MutableComponent translatable = Component.translatable(this.title);
            String string = translatable.getString().replace("playername", MinecraftUtil.getPlayer().getScoreboardName());
            guiGraphics.drawString(this.font, string, 10, dialogueHeight + 4, 0xFFFFFFFF);
            guiGraphics.fill(8, dialogueHeight + 4 + 10, 8 + 4 + this.font.width(string), dialogueHeight + 5 + 10, 0xFFFFFFFF);
        }
        for (int i = 0; i < this.splitText.size(); i++) {
            guiGraphics.drawString(this.font, Component.translatable(this.splitText.get(i)), 20, dialogueHeight + 25 + (i * this.font.lineHeight + 4), 0xFFFFFFFF);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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
        varMap.put("screenwidth", this.width);
        varMap.put("screenheight", this.height);
        if (initPage != page) {
            this.title = dialogTexts[page].title();
            this.imageGroups = dialogTexts[page].imageGroup();
            this.originText = dialogTexts[page].text();
            this.soundGroup = dialogTexts[page].soundGroup();
            this.textEffect = dialogTexts[page].textEffect();
            if (soundGroup != null) soundGroup.playSound(getLevel());
            initPage = page;
        }
        this.splitText = this.splitString(originText);
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

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    protected void renderMenuBackground(GuiGraphics guiGraphics, int x, int y, int width, int height) {
    }
}

