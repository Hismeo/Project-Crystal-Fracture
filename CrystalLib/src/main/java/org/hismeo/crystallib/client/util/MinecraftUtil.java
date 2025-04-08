package org.hismeo.crystallib.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.platform.Window;

public class MinecraftUtil {
    public static Minecraft getMinecraft(){
        return Minecraft.getInstance();
    }

    public static GameRenderer getGameRenderer(){
        return getMinecraft().gameRenderer;
    }

    public static Window getWindow(){
        return getMinecraft().getWindow();
    }
}
