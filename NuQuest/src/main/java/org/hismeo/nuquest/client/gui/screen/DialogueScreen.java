package org.hismeo.nuquest.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import org.hismeo.nuquest.core.dialogue.context.DialogueDefinition;
import org.jetbrains.annotations.NotNull;

public class DialogueScreen extends Screen {
    private final DialogueDefinition dialogueDefinition;

    public DialogueScreen(DialogueDefinition dialogueDefinition) {
        super(CommonComponents.EMPTY);
        this.dialogueDefinition = dialogueDefinition;
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int dialogueHeight = this.height / 3 * 2;
        guiGraphics.fillGradient(0, dialogueHeight, this.width, this.height, -1073741824, -1073741824);
        for (int i = 0; i < dialogueDefinition.message().length; i++) {
            guiGraphics.drawString(this.font, dialogueDefinition.message()[i], 20, dialogueHeight + 20 + i * this.font.lineHeight, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
