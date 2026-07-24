package org.hismeo.haikalathost.client.intercept;

import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderStateShard.BooleanStateShard.class)
public interface RenderStateBooleanAccessor {
    @Accessor("enabled")
    boolean haikalatHost$enabled();
}
