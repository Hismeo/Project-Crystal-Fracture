package org.hismeo.fractureclient.mixin.remove_stuff;

import net.minecraft.client.tutorial.Tutorial;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Tutorial.class)
public class TutorialMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    public void noTutorial(CallbackInfo ci) {ci.cancel();}
}
