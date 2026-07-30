package org.hismeo.haikalathost.internal.runtime;

import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.hismeo.haikalathost.HaikalatHost;
import org.hismeo.haikalathost.internal.content.HaikalatContentRegistry;
import org.hismeo.haikalathost.internal.diagnostics.HaikalatHostClientCommands;
import org.hismeo.haikalathost.internal.extension.HaikalatExtensionRegistry;
import org.hismeo.haikalathost.internal.resource.HaikalatHostReloadListener;

import java.util.Objects;

/**
 * Connects the single Host runtime to NeoForge's client lifecycle.
 */
public final class ClientLifecycleHooks {
    private final MinecraftHaikalatRuntime runtime;
    private final HaikalatExtensionRegistry extensions;

    public ClientLifecycleHooks() {
        this(
                MinecraftHaikalatRuntime.instance(),
                HaikalatExtensionRegistry.instance());
    }

    ClientLifecycleHooks(
            MinecraftHaikalatRuntime runtime,
            HaikalatExtensionRegistry extensions
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.extensions = Objects.requireNonNull(extensions, "extensions");
    }

    public void register(IEventBus modEventBus) {
        Objects.requireNonNull(modEventBus, "modEventBus")
                .addListener(this::onRegisterReloadListeners);
        modEventBus.addListener(this::onClientSetup);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, this::onFramePre);
        NeoForge.EVENT_BUS.addListener(this::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::onFramePost);
        NeoForge.EVENT_BUS.addListener(this::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(this::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::onGameShuttingDown);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            HaikalatContentRegistry content = HaikalatContentRegistry.instance();
            if (!content.frozen()) {
                ModLoader.postEventWrapContainerInModOrder(content.registrationEvent());
                content.freeze();
                HaikalatHost.LOGGER.info(
                        "Registered {} Haikalat assets across namespaces {}",
                        content.assets().size(),
                        content.namespaces());
            }
            if (!extensions.frozen()) {
                ModLoader.postEventWrapContainerInModOrder(extensions.registrationEvent());
                extensions.freeze();
                HaikalatHost.LOGGER.info(
                        "Registered {} advanced Haikalat render extension(s)",
                        extensions.registrations().size());
            }
        });
    }

    private void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new HaikalatHostReloadListener(runtime));
    }

    private void onFramePre(RenderFrameEvent.Pre event) {
        runtime.onFrameStart();
    }

    private void onRenderLevelStage(RenderLevelStageEvent event) {
        runtime.onRenderLevelStage(event);
    }

    private void onFramePost(RenderFrameEvent.Post event) {
        runtime.onFrameEnd();
    }

    private void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel level) {
            runtime.requestWorldLoad(level);
        }
    }

    private void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level) {
            runtime.requestWorldUnload(level);
        }
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        HaikalatHostClientCommands.register(event, runtime);
    }

    private void onGameShuttingDown(GameShuttingDownEvent event) {
        runtime.close();
    }
}
