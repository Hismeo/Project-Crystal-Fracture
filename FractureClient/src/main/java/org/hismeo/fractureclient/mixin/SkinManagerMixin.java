package org.hismeo.fractureclient.mixin;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.SkinManager;
import org.hismeo.fractureclient.client.avatar.skin.SkinManagerCacheRoot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.concurrent.Executor;

/** Retains the exact assetDirectory/skins root supplied to Minecraft's SkinManager. */
@Mixin(SkinManager.class)
public abstract class SkinManagerMixin implements SkinManagerCacheRoot {
    @Unique
    private Path fractureClient$skinCacheRoot;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void fractureClient$captureSkinCacheRoot(
            TextureManager textureManager,
            Path root,
            MinecraftSessionService sessionService,
            Executor executor,
            CallbackInfo callback
    ) {
        fractureClient$skinCacheRoot = root;
    }

    @Override
    public Path fractureClient$skinCacheRoot() {
        return fractureClient$skinCacheRoot;
    }
}
