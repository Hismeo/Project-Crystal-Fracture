package org.hismeo.fractureclient.client.render.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TitleScreen extends Screen {
    protected TitleScreen() {
        //TODO langkey dategen
        super(Component.translatable("screen.fracture_client.title_screen"));
    }
}
