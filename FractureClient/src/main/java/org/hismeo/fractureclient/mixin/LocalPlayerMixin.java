package org.hismeo.fractureclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.hismeo.fractureclient.client.control.CameraModeController;
import org.hismeo.fractureclient.client.impl.mixin.LocalPlayerImpl;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends Player implements LocalPlayerImpl {
    @Shadow @Final protected Minecraft minecraft;

    @Shadow public Input input;

    public LocalPlayerMixin(Level level, BlockPos pos, float yRot, GameProfile gameProfile) {
        super(level, pos, yRot, gameProfile);
    }

    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/Input;hasForwardImpulse()Z"))
    public boolean alwaysForward(Input instance, Operation<Boolean> original) {
        return CameraModeController.isOrthographic()
                ? this.isPlayerMove(input)
                : original.call(instance);
    }

    @Override
    public float maxUpStep() {
        return CameraModeController.isOrthographic() ? 1.25F : super.maxUpStep();
    }

    @Override
    public void moveRelative(float amount, @NotNull Vec3 relative) {
        if (CameraModeController.isOrthographic()) {
            this.moveKeyMove((LocalPlayer) (Object) this, minecraft, amount, relative);
        } else {
            super.moveRelative(amount, relative);
        }
    }

    @Override
    public boolean isSprinting() {
        return CameraModeController.isOrthographic()
                ? this.isPlayerMove(input) && super.isSprinting()
                : super.isSprinting();
    }
}
