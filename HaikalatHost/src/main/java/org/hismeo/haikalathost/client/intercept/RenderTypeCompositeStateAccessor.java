package org.hismeo.haikalathost.client.intercept;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderType.CompositeState.class)
public interface RenderTypeCompositeStateAccessor {
    @Accessor("textureState")
    RenderStateShard.EmptyTextureStateShard haikalatHost$textureState();

    @Accessor("shaderState")
    RenderStateShard.ShaderStateShard haikalatHost$shaderState();

    @Accessor("transparencyState")
    RenderStateShard.TransparencyStateShard haikalatHost$transparencyState();

    @Accessor("cullState")
    RenderStateShard.CullStateShard haikalatHost$cullState();

    @Accessor("lightmapState")
    RenderStateShard.LightmapStateShard haikalatHost$lightmapState();

    @Accessor("overlayState")
    RenderStateShard.OverlayStateShard haikalatHost$overlayState();
}
