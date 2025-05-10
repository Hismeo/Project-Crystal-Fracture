package org.hismeo.nuquest.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.hismeo.nuquest.core.dialog.context.DialogDefinition;
import org.jetbrains.annotations.NotNull;

public class DialogScreen extends Screen {
    private final DialogDefinition dialogDefinition;
    private final int maxPage;
    private int page;

    public DialogScreen(DialogDefinition dialogDefinition) {
        this(dialogDefinition, 0);
    }

    public DialogScreen(DialogDefinition dialogDefinition, int page) {
        super(CommonComponents.EMPTY);
        this.dialogDefinition = dialogDefinition;
        this.maxPage = dialogDefinition.dialogTexts().length;
        this.page = page;
    }

    @Override
    protected void init() {
        ImageButton flipButton = new ImageButton(this.width - 40, this.height - 40, 20, 20, 0, 0, 20, Button.ACCESSIBILITY_TEXTURE, 32, 64, this::tryFlip);
        this.addWidget(flipButton);
        if (canFlip()){
            this.addRenderableOnly(flipButton);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int dialogueHeight = this.height / 3 * 2;
        guiGraphics.fillGradient(0, dialogueHeight, this.width, this.height, -1073741824, -1073741824);

        String text = dialogDefinition.dialogTexts()[page].text();
        guiGraphics.drawString(this.font, Component.literal(text), 20, dialogueHeight + 20 + page * this.font.lineHeight, 0xFFFFFFFF);
        if (canFlip()) {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected void tryFlip(Button button) {
        if (canFlip()) this.page++;
    }

    protected boolean canFlip() {
        return this.page < this.maxPage - 1;
    }
}

