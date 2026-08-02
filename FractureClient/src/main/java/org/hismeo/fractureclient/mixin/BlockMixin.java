package org.hismeo.fractureclient.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.hismeo.fractureclient.client.control.BlockCullController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class BlockMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void fractureClient$exposeFaceBesideCutaway(
            BlockState state,
            BlockGetter level,
            BlockPos offset,
            Direction face,
            BlockPos neighborPos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (BlockCullController.shouldExposeFaceDuringCompilation(neighborPos)) {
            cir.setReturnValue(true);
        }
    }
}
