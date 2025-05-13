package org.hismeo.crystallib.util.client.render.shader;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystallib.CrystalLib;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.hismeo.crystallib.util.client.MinecraftUtil.*;

public class EffectUtil {
    public static @NotNull AbstractUniform safeGetUniform(int pass, String name) {
        PostChain postChain = getGameRenderer().currentEffect();
        return safeGetUniform(postChain, pass, name);
    }

    public static @NotNull AbstractUniform safeGetUniform(PostChain postChain, int pass, String name) {
        return safeGetUniforms(postChain, name).get(pass);
    }

    public static @NotNull List<AbstractUniform> safeGetUniforms(PostChain postChain, String name) {
        List<PostPass> passes = postChain.passes;
        List<AbstractUniform> uniforms = List.of();
        for (PostPass pass : passes) {
            uniforms.add(pass.getEffect().safeGetUniform(name));
        }
        return uniforms;
    }

    public static @Nullable AbstractUniform getUniform(int pass, String name) {
        PostChain postChain = getGameRenderer().currentEffect();
        return getUniform(postChain, pass, name);
    }

    public static @Nullable AbstractUniform getUniform(PostChain postChain, int pass, String name) {
        return getUniforms(postChain, name).get(pass);
    }

    public static List<Uniform> getUniforms(PostChain postChain, String name) {
        List<PostPass> passes = postChain.passes;
        List<Uniform> uniforms = new ArrayList<>();
        for (PostPass pass : passes) {
            uniforms.add(pass.getEffect().getUniform(name));
        }
        return uniforms;
    }

    public static @Nullable PostChain getPostChain(ResourceLocation resourceLocation){
        PostChain postChain;
        try {
            postChain = new PostChain(getTextureManager(), getResourceManager(), getMainRenderTarget(), resourceLocation);
            postChain.resize(getWindow().getWidth(), getWindow().getHeight());
            return postChain;
        } catch (IOException exception){
            CrystalLib.LOGGER.warn("Failed to load shader: {}", resourceLocation, exception);
            return null;
        } catch (JsonSyntaxException jsonsyntaxexception) {
            CrystalLib.LOGGER.warn("Failed to parse shader: {}", resourceLocation, jsonsyntaxexception);
            return null;
        }
    }

    public static void setUniform(PostChain postChain, String pName, float value) {
        for (PostPass postpass : postChain.passes) {
            postpass.getEffect().safeGetUniform(pName).set(value);
        }
    }
}
