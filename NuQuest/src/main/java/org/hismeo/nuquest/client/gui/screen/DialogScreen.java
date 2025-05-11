package org.hismeo.nuquest.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.hismeo.nuquest.core.dialog.context.DialogDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DialogScreen extends Screen {
    protected final List<Button> optionalButton = new ArrayList<>();
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
        ImageButton flipButton = this.addRenderableWidget(new ImageButton(this.width - 40, this.height - 40, 20, 20, 0, 0, 20, Button.ACCESSIBILITY_TEXTURE, 32, 64, this::tryFlip));
        optionalButton.add(flipButton);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int dialogueHeight = this.height / 3 * 2;
        guiGraphics.fillGradient(0, dialogueHeight, this.width, this.height, -1073741824, -1073741824);

        String title = dialogDefinition.dialogTexts()[page].title();
        String text = dialogDefinition.dialogTexts()[page].text();
        //  + page * this.font.lineHeight
        guiGraphics.drawString(this.font, Component.translatable(title), 10, dialogueHeight + 4, 0xFFFFFFFF);
        guiGraphics.fill(8, dialogueHeight + 4 + 10, 8 + 4 + this.font.width(title), dialogueHeight + 5 + 10, 0xFFFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable(text), 20, dialogueHeight + 25, 0xFFFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (!canFlip()) {
            optionalButton.forEach(button -> button.active = false);
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

