package org.hismeo.haikalathost.client.intercept;

import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.RenderType$CompositeRenderType")
public interface CompositeRenderTypeAccessor {
    @Accessor("state")
    RenderType.CompositeState haikalatHost$state();
}
