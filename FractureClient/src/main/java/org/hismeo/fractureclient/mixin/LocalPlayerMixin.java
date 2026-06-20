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
        return this.isPlayerMove(input);
    }

    @Override
    public float maxUpStep() {
        return 1.25f;
    }

    @Override
    public void moveRelative(float amount, @NotNull Vec3 relative) {
        this.moveKeyMove((LocalPlayer) (Object) this, minecraft, amount, relative);
    }

    @Override
    public boolean isSprinting() {
        return this.isPlayerMove(input) && super.isSprinting();
    }
}
