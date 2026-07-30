package org.hismeo.haikalathost.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Continues the Haikalat loading presentation after the early window has been handed to Minecraft.
 *
 * <p>This class intentionally extends {@link LoadingOverlay}: Minecraft uses that type as a marker
 * while coordinating resource reloads. The early-window service injects a JDK-only drawing
 * callback, so this game-layer class has no binary dependency on service-layer implementation
 * types. The callback draws on the context and framebuffer currently owned by Minecraft.</p>
 */
public final class HaikalatLoadingOverlay extends LoadingOverlay {
    private static final long FADE_OUT_MILLIS = 1_000L;

    private final Minecraft minecraft;
    private final ReloadInstance reload;
    private final Consumer<Optional<Throwable>> onFinish;
    private final Consumer<float[]> loadingFrameRenderer;

    private float currentProgress;
    private long fadeOutStart = -1L;

    public HaikalatLoadingOverlay(Minecraft minecraft, ReloadInstance reload,
                                  Consumer<Optional<Throwable>> onFinish, boolean fadeIn,
                                  Consumer<float[]> loadingFrameRenderer) {
        super(minecraft, reload, onFinish, fadeIn);
        this.minecraft = minecraft;
        this.reload = reload;
        this.onFinish = onFinish;
        this.loadingFrameRenderer =
                Objects.requireNonNull(loadingFrameRenderer, "loadingFrameRenderer");
    }

    /**
     * Reflection-friendly bridge used by FractureWindowLoader across the service/game module boundary.
     */
    public static Supplier<LoadingOverlay> loadingOverlay(Supplier<Minecraft> minecraft,
                                                           Supplier<ReloadInstance> reload,
                                                           Consumer<Optional<Throwable>> onFinish,
                                                           boolean fadeIn,
                                                           Consumer<float[]> loadingFrameRenderer) {
        return () -> new HaikalatLoadingOverlay(
                minecraft.get(), reload.get(), onFinish, fadeIn, loadingFrameRenderer);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.flush();

        currentProgress = Mth.clamp(
                currentProgress * 0.95F + reload.getActualProgress() * 0.05F,
                0.0F,
                1.0F);
        loadingFrameRenderer.accept(new float[] {
                minecraft.getWindow().getWidth(),
                minecraft.getWindow().getHeight(),
                currentProgress
        });

        long now = Util.getMillis();
        if (fadeOutStart < 0L && reload.isDone()) {
            fadeOutStart = now;
            finishReload();
        }
        if (fadeOutStart >= 0L && now - fadeOutStart >= FADE_OUT_MILLIS) {
            minecraft.setOverlay(null);
        }
    }

    private void finishReload() {
        try {
            reload.checkExceptions();
            onFinish.accept(Optional.empty());
        } catch (Throwable throwable) {
            onFinish.accept(Optional.of(throwable));
        }
    }

}
