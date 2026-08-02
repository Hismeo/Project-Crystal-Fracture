package org.hismeo.fractureclient.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.hismeo.fractureclient.client.control.ExplicitRoomController;
import org.hismeo.fractureclient.client.control.RoomCullScanner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void fractureClient$invalidateRoomSnapshot(
            BlockPos pos,
            BlockState oldState,
            BlockState newState,
            int flags,
            CallbackInfo ci
    ) {
        if (oldState != newState) {
            ClientLevel level = (ClientLevel)(Object)this;
            RoomCullScanner.markDirty(level, pos);
            ExplicitRoomController.markDirty(level, pos);
        }
    }
}
