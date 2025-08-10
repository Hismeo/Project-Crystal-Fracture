package org.hismeo.crystallib.client.util.render.shader;

import net.minecraft.client.renderer.PostChain;
import org.hismeo.crystallib.client.render.EffectType;

import static org.hismeo.crystallib.client.util.render.shader.EffectUtil.*;
import static org.hismeo.crystallib.client.util.MinecraftUtil.*;

public class BlurUtil {
    public static PostChain blurEffect;

    public static void closeBlur(){
        if (blurEffect != null){
            blurEffect.close();
        }
    }

    public static void loadBlurEffect() {
        if (blurEffect != null) {
            blurEffect.close();
        }

        blurEffect = getPostChain(EffectType.FADE_IN_BLUR);
        blurEffect.resize(getWindow().getWidth(), getWindow().getHeight());
    }

    public static void processBlurEffect(float pPartialTick) {
        if (blurEffect != null) {
            setUniform(blurEffect, "Radius", 2f);
            blurEffect.process(pPartialTick);
        }
    }
}
