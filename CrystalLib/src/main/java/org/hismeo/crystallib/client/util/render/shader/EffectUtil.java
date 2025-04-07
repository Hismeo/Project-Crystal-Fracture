package org.hismeo.crystallib.client.util.render.shader;

import com.mojang.blaze3d.shaders.Uniform;

import static org.hismeo.crystallib.client.util.MinecraftUtil.*;

public class EffectUtil {
    public static Uniform getUniforms(int pass, String name){
        return getGameRenderer().currentEffect().passes.get(pass).getEffect().getUniform(name);
    }
}
