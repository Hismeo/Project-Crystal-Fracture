package org.hismeo.nuquest.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.nuquest.api.dialog.text.ITextEffect;
import org.hismeo.nuquest.core.dialog.SoundGroup;
import org.hismeo.nuquest.core.dialog.context.DialogDefinition;
import org.hismeo.nuquest.core.dialog.text.DialogText;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static org.hismeo.crystallib.client.util.MinecraftUtil.*;

public class DialogScreen extends Screen {
    protected final List<Button> optionalButton = new ArrayList<>();
    private final DialogDefinition dialogDefinition;
    private final String dialogueId;
    private final DialogText[] dialogTexts;
    private String title;
    private ResourceLocation imagePath;
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
        this.dialogueId = dialogDefinition.dialogueId();
        this.dialogTexts = dialogDefinition.dialogTexts();
    }

    @Override
    protected void init() {
        ImageButton flipButton = this.addRenderableWidget(new ImageButton(this.width - 40, this.height - 40, 20, 20, 0, 0, 20, Button.ACCESSIBILITY_TEXTURE, 32, 64, this::tryFlip));
        optionalButton.add(flipButton);
        this.initPage();
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int dialogueHeight = this.height / 3 * 2;
        guiGraphics.fillGradient(0, dialogueHeight, this.width, this.height, -1073741824, -1073741824);

        //
        guiGraphics.blit(this.imagePath, 0, dialogueHeight - 64, 64, 64, 0, 0, 64, 64, 64, 64);
        if (title != null) {
            guiGraphics.drawString(this.font, Component.translatable(this.title), 10, dialogueHeight + 4, 0xFFFFFFFF);
            guiGraphics.fill(8, dialogueHeight + 4 + 10, 8 + 4 + this.font.width(title), dialogueHeight + 5 + 10, 0xFFFFFFFF);
        }
        for (int i = 0; i < this.splitText.length; i++) {
            guiGraphics.drawString(this.font, Component.translatable(this.splitText[i]), 20, dialogueHeight + 25 + (i * this.font.lineHeight + 4), 0xFFFFFFFF);
        }
        if (!canFlip()) {
            optionalButton.forEach(button -> button.active = false);
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
        if (initPage != page) {
            this.title = dialogTexts[page].title();
            this.imagePath = dialogTexts[page].imagePath();
            this.originText = dialogTexts[page].text();
            this.splitText = originText.split("\n");
            this.soundGroup = dialogTexts[page].soundGroup();
            this.textEffect = dialogTexts[page].textEffect();
            if (soundGroup != null) soundGroup.playSound(getLevel());
            initPage = page;
        }
    }
}

