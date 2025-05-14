package org.hismeo.nuquest.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.hismeo.nuquest.api.dialog.text.ITextEffect;
import org.hismeo.nuquest.core.dialog.ImageGroup;
import org.hismeo.nuquest.core.dialog.SoundGroup;
import org.hismeo.nuquest.core.dialog.context.DialogActionData;
import org.hismeo.nuquest.core.dialog.context.DialogDefinition;
import org.hismeo.nuquest.core.dialog.text.DialogText;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hismeo.crystallib.util.client.MinecraftUtil.getLevel;

public class DialogScreen extends Screen {
    private final Map<String, Number> varMap = new HashMap<>();
    protected Button flipButton;
    protected final List<Button> actionButton = new ArrayList<>();
    private final String dialogueId;
    private final DialogDefinition dialogDefinition;
    private final DialogActionData[] dialogActionDatas;
    private final DialogText[] dialogTexts;
    private String title;
    private ImageGroup imageGroup;
    private String originText;
    private String[] splitText;
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
        this.dialogActionDatas = dialogDefinition.dialogActionData();
        this.dialogueId = dialogDefinition.dialogueId();
        this.dialogTexts = dialogDefinition.dialogTexts();
    }

    @Override
    protected void init() {
        this.flipButton = this.addRenderableWidget(new ImageButton(this.width - 40,
                this.height - 40,
                20, 20,
                0, 0,
                20, Button.ACCESSIBILITY_TEXTURE,
                32, 64,
                this::tryFlip)
        );

        if (dialogActionDatas != null) {
            for (int i = 0; i < this.dialogActionDatas.length; i++) {
                this.addRenderableWidget(new Button.Builder(Component.literal(this.dialogActionDatas[i].message()), button -> {
                }).pos(0, 0).build());
            }
        }

        this.initPage();
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int dialogueHeight = this.height / 3 * 2;
        guiGraphics.fillGradient(0, dialogueHeight, this.width, this.height, -1073741824, -1073741824);

        if (this.imageGroup != null && this.imageGroup.hasImage()) {
            this.imageGroup.blitImage(guiGraphics, varMap);
        }

        if (title != null) {
            guiGraphics.drawString(this.font, Component.translatable(this.title), 10, dialogueHeight + 4, 0xFFFFFFFF);
            guiGraphics.fill(8, dialogueHeight + 4 + 10, 8 + 4 + this.font.width(title), dialogueHeight + 5 + 10, 0xFFFFFFFF);
        }
        for (int i = 0; i < this.splitText.length; i++) {
            guiGraphics.drawString(this.font, Component.translatable(this.splitText[i]), 20, dialogueHeight + 25 + (i * this.font.lineHeight + 4), 0xFFFFFFFF);
        }
        if (!canFlip()) {
            flipButton.active = false;
        } else {

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
            this.imageGroup = dialogTexts[page].imageGroup();
            this.originText = dialogTexts[page].text();
            this.splitText = originText.split("\n");
            this.soundGroup = dialogTexts[page].soundGroup();
            this.textEffect = dialogTexts[page].textEffect();
            if (soundGroup != null) soundGroup.playSound(getLevel());
            initPage = page;
        }
    }
}

