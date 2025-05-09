package org.hismeo.crystallib.client.util;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.packs.resources.ResourceManager;

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

    public static TextureManager getTextureManager(){
        return getMinecraft().getTextureManager();
    }

    public static RenderTarget getMainRenderTarget(){
        return getMinecraft().getMainRenderTarget();
    }

    public static ResourceManager getResourceManager(){
        return getMinecraft().getResourceManager();
    }

    public static float getPartialTick(){
        return getMinecraft().getPartialTick();
    }

    public static ClientLevel getLevel(){
        return getMinecraft().level;
    }

    public static LocalPlayer getPlayer(){
        return getMinecraft().player;
    }

    public static IntegratedServer getSingleServer(){
        return getMinecraft().getSingleplayerServer();
    }
}
