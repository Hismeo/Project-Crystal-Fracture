package org.hismeo.crystallib.client.util.render.shader;

import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystallib.CrystalLib;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

import static org.hismeo.crystallib.client.util.MinecraftUtil.*;

public class EffectUtil {
    @Nullable
    public static Uniform getUniform(int pass, String name) {
        PostChain postChain = getGameRenderer().currentEffect();
        return getUniform(postChain, pass, name);
    }

    @Nullable
    public static Uniform getUniform(PostChain postChain, int pass, String name) {
        List<PostPass> passes = postChain.passes;
        return passes.get(pass).getEffect().getUniform(name);
    }

    public static PostChain getPostChain(ResourceLocation resourceLocation){
        PostChain postChain;
        try {
            postChain = new PostChain(getMinecraft().textureManager, getMinecraft().getResourceManager(), getMinecraft().getMainRenderTarget(), resourceLocation);
            postChain.resize(getWindow().getWidth(), getWindow().getHeight());
            return postChain;
        } catch (IOException exception){
            CrystalLib.LOGGER.warn("Failed to load shader: {}", resourceLocation, exception);
            return null;
        }
    }
}
