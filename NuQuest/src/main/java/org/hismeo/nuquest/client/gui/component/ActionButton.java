package org.hismeo.nuquest.client.gui.component;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.hismeo.nuquest.NuQuest;
import org.jetbrains.annotations.NotNull;

public class ActionButton extends AbstractWidget {
    protected final Press press;
    public boolean hidden;

    public ActionButton(int x, int y, int width, int height, Component message, Press press) {
        super(x, y, width, height, message);
        this.press = press;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (hidden) return;
        Minecraft minecraft = Minecraft.getInstance();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, this.alpha);
        RenderSystem.enableBlend();
        guiGraphics.blit(new ResourceLocation(NuQuest.MODID, "textures/gui/dialog.png"), this.getX(), this.getY(), 0, 0, this.getWidth(), this.getHeight());
        RenderSystem.disableBlend();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        this.renderScrollingString(guiGraphics, minecraft.font, 2, this.getFGColor() | Mth.ceil(this.alpha * 255.0F) << 24);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        if (hidden){
            return false;
        }
        return super.clicked(mouseX, mouseY);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.press.press();
    }

    @FunctionalInterface
    public interface Press {
        void press();
    }
}
