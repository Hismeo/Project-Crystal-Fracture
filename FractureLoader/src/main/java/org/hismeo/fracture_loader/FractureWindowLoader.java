/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package org.hismeo.fracture_loader;

import joptsimple.OptionParser;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider;
import org.hismeo.fracture_loader.render.HaikalatLoadingFrameRenderer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.IntBuffer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.GL_TRUE;
import static org.hismeo.fracture_loader.render.HaikalatLoadingFrameRenderer.FramebufferPolicy.BIND_DEFAULT;

/**
 * Haikalat-backed implementation of NeoForge's early window provider.
 *
 * <p>The window and its OpenGL context stay on the main thread. This is both simpler than a
 * background renderer and required for reliable context hand-off on macOS. Haikalat owns the
 * render command stream, while this class owns GLFW so it can perform Minecraft's window hand-off.
 * OpenGL 4.6 is a hard requirement; unsupported systems are rejected before loading continues.</p>
 */
public final class FractureWindowLoader implements ImmediateWindowProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("fracturewindowloader");
    private static final String HAIKALAT_OVERLAY_CLASS =
            "org.hismeo.haikalathost.client.HaikalatLoadingOverlay";
    private static final int REQUIRED_GL_MAJOR = 4;
    private static final int REQUIRED_GL_MINOR = 6;
    private static final long FRAME_INTERVAL_NANOS = 50_000_000L;

    private String mcVersion;
    private String neoForgeVersion;
    private int winWidth;
    private int winHeight;
    private int winX;
    private int winY;
    private int framebufferWidth;
    private int framebufferHeight;
    private boolean maximized;
    private boolean handedOff;
    private long window;
    private long nextFrameNanos;
    private String glVersion = "4.6";
    private HaikalatLoadingFrameRenderer loadingFrameRenderer;
    private Method loadingOverlay;

    @Override
    public String name() {
        return "fracturewindowloader";
    }

    @Override
    public Runnable initialize(String[] arguments) {
        OptionParser parser = new OptionParser();
        var mcVersionOption = parser.accepts("fml.mcVersion").withRequiredArg().ofType(String.class);
        var neoForgeVersionOption = parser.accepts("fml.neoForgeVersion").withRequiredArg().ofType(String.class);
        var widthOption = parser.accepts("width").withRequiredArg().ofType(Integer.class)
                .defaultsTo(FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_WIDTH));
        var heightOption = parser.accepts("height").withRequiredArg().ofType(Integer.class)
                .defaultsTo(FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_HEIGHT));
        var maximizedOption = parser.accepts("earlywindow.maximized");
        parser.allowsUnrecognizedOptions();

        var parsed = parser.parse(arguments);
        mcVersion = parsed.valueOf(mcVersionOption);
        neoForgeVersion = parsed.valueOf(neoForgeVersionOption);
        winWidth = Math.max(320, parsed.valueOf(widthOption));
        winHeight = Math.max(240, parsed.valueOf(heightOption));
        maximized = parsed.has(maximizedOption)
                || FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_MAXIMIZED);

        FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_WIDTH, winWidth);
        FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_HEIGHT, winHeight);
        StartupNotificationManager.modLoaderConsumer().ifPresent(
                consumer -> consumer.accept("Fracture Loader / NeoForge " + neoForgeVersion));

        createWindow();
        renderFrame();
        return this::periodicTick;
    }

    private void createWindow() {
        long glfwInitBegin = System.nanoTime();
        if (!glfwInit()) {
            failWindowCreation("We are unable to initialize the graphics system.\nglfwInit failed.");
        }
        long glfwInitDuration = System.nanoTime() - glfwInitBegin;
        if (glfwInitDuration > 1_000_000_000L) {
            LOGGER.warn("glfwInit took {} seconds", glfwInitDuration / 1.0e9);
        }
        getLastGlfwError().ifPresent(error -> LOGGER.debug("Suppressing stale GLFW error: {}", error));

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
        glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_NATIVE_CONTEXT_API);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, REQUIRED_GL_MAJOR);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, REQUIRED_GL_MINOR);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_TRUE);

        if (mcVersion != null) {
            String vanillaWindowTitle = "Minecraft* " + mcVersion;
            glfwWindowHintString(GLFW_X11_CLASS_NAME, vanillaWindowTitle);
            glfwWindowHintString(GLFW_X11_INSTANCE_NAME, vanillaWindowTitle);
        }

        long primaryMonitor = glfwGetPrimaryMonitor();
        GLFWVidMode videoMode = primaryMonitor == MemoryUtil.NULL ? null : glfwGetVideoMode(primaryMonitor);
        if (videoMode == null) {
            failWindowCreation("Failed to locate the primary monitor or its current display mode.");
        }

        LOGGER.info("Requesting required OpenGL 4.6 core profile for the Fracture early window");
        window = glfwCreateWindow(winWidth, winHeight, "Minecraft: Fracture Loading...",
                MemoryUtil.NULL, MemoryUtil.NULL);

        if (window == MemoryUtil.NULL) {
            String details = getLastGlfwError().orElse("No GLFW error details were provided.");
            failWindowCreation("OpenGL 4.6 is required, but a 4.6 core-profile context could not be created.\n"
                    + details);
        }

        int actualMajor = glfwGetWindowAttrib(window, GLFW_CONTEXT_VERSION_MAJOR);
        int actualMinor = glfwGetWindowAttrib(window, GLFW_CONTEXT_VERSION_MINOR);
        glVersion = actualMajor + "." + actualMinor;
        if (actualMajor < REQUIRED_GL_MAJOR
                || actualMajor == REQUIRED_GL_MAJOR && actualMinor < REQUIRED_GL_MINOR) {
            glfwDestroyWindow(window);
            window = MemoryUtil.NULL;
            failWindowCreation("OpenGL 4.6 is required, but the graphics driver provided OpenGL "
                    + glVersion + ".");
        }
        LOGGER.info("OpenGL {} requirement satisfied", glVersion);

        if (maximized) {
            glfwMaximizeWindow(window);
        } else {
            centerWindow(primaryMonitor, videoMode);
        }

//        glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
        glfwSetFramebufferSizeCallback(window, this::onFramebufferResize);
        glfwSetWindowSizeCallback(window, this::onWindowResize);
        glfwSetWindowPosCallback(window, this::onWindowMove);
        refreshWindowMetrics();

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        GL.createCapabilities();
        loadingFrameRenderer = new HaikalatLoadingFrameRenderer();
        nextFrameNanos = System.nanoTime();

        LOGGER.info("Haikalat early renderer initialized: {} {}",
                org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER),
                org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION));
        glfwShowWindow(window);
        glfwPollEvents();
    }

    private void centerWindow(long monitor, GLFWVidMode videoMode) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer monitorX = stack.mallocInt(1);
            IntBuffer monitorY = stack.mallocInt(1);
            glfwGetMonitorPos(monitor, monitorX, monitorY);
            winX = monitorX.get(0) + Math.max(0, (videoMode.width() - winWidth) / 2);
            winY = monitorY.get(0) + Math.max(0, (videoMode.height() - winHeight) / 2);
            glfwSetWindowPos(window, winX, winY);
        }
    }

    private void refreshWindowMetrics() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer first = stack.mallocInt(1);
            IntBuffer second = stack.mallocInt(1);
            glfwGetWindowSize(window, first, second);
            winWidth = first.get(0);
            winHeight = second.get(0);
            glfwGetWindowPos(window, first, second);
            winX = first.get(0);
            winY = second.get(0);
            glfwGetFramebufferSize(window, first, second);
            framebufferWidth = first.get(0);
            framebufferHeight = second.get(0);
        }
    }

    private void renderFrame() {
        if (handedOff || window == MemoryUtil.NULL || framebufferWidth <= 0 || framebufferHeight <= 0) {
            return;
        }

        long now = System.nanoTime();
        if (now < nextFrameNanos) {
            return;
        }
        nextFrameNanos = now + FRAME_INTERVAL_NANOS;

        List<ProgressMeter> progressMeters = StartupNotificationManager.getCurrentProgress();
        int shownMeters = Math.min(3, progressMeters.size());
        float[] progressValues = new float[shownMeters];
        for (int index = 0; index < shownMeters; index++) {
            ProgressMeter meter = progressMeters.get(index);
            progressValues[index] = meter.steps() == 0
                    ? loadingFrameRenderer.indeterminateProgress()
                    : meter.progress();
        }
        loadingFrameRenderer.render(
                framebufferWidth, framebufferHeight, BIND_DEFAULT, progressValues);
        glfwSwapBuffers(window);
    }

    private void onFramebufferResize(long callbackWindow, int width, int height) {
        if (callbackWindow == window && width > 0 && height > 0) {
            framebufferWidth = width;
            framebufferHeight = height;
        }
    }

    private void onWindowResize(long callbackWindow, int width, int height) {
        if (callbackWindow == window && width > 0 && height > 0) {
            winWidth = width;
            winHeight = height;
        }
    }

    private void onWindowMove(long callbackWindow, int x, int y) {
        if (callbackWindow == window) {
            winX = x;
            winY = y;
        }
    }

    @Override
    public void updateFramebufferSize(IntConsumer width, IntConsumer height) {
        width.accept(framebufferWidth);
        height.accept(framebufferHeight);
    }

    @Override
    public long setupMinecraftWindow(IntSupplier width, IntSupplier height, Supplier<String> title,
                                     LongSupplier monitor) {
        if (window == MemoryUtil.NULL) {
            throw new IllegalStateException("Fracture early window has not been initialized");
        }

        renderFrame();
        handedOff = true;
        glfwMakeContextCurrent(window);
        glfwSetWindowTitle(window, title.get());
        glfwSwapInterval(0);
        releaseCallbacks();
        loadingFrameRenderer.invalidateState();
        return window;
    }

    private void releaseCallbacks() {
        var framebufferCallback = glfwSetFramebufferSizeCallback(window, null);
        if (framebufferCallback != null) framebufferCallback.free();
        var sizeCallback = glfwSetWindowSizeCallback(window, null);
        if (sizeCallback != null) sizeCallback.free();
        var positionCallback = glfwSetWindowPosCallback(window, null);
        if (positionCallback != null) positionCallback.free();
    }

    @Override
    public boolean positionWindow(Optional<Object> monitor, IntConsumer widthSetter,
                                  IntConsumer heightSetter, IntConsumer xSetter, IntConsumer ySetter) {
        widthSetter.accept(winWidth);
        heightSetter.accept(winHeight);
        xSetter.accept(winX);
        ySetter.accept(winY);
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> ri,
                                          Consumer<Optional<Throwable>> ex, boolean fade) {
        if (loadingOverlay == null) {
            throw new IllegalStateException("The Minecraft game layer has not supplied its loading overlay");
        }
        try {
            return (Supplier<T>) loadingOverlay.invoke(null, mc, ri, ex, fade);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create Minecraft's loading overlay", exception);
        }
    }

    @Override
    public void updateModuleReads(ModuleLayer layer) {
        Class<?> haikalatOverlay = findClass(layer, HAIKALAT_OVERLAY_CLASS);
        if (haikalatOverlay != null) {
            getClass().getModule().addReads(haikalatOverlay.getModule());
            loadingOverlay = findLoadingOverlayBridge(haikalatOverlay);
            LOGGER.info("Using HaikalatHost for the Minecraft loading overlay");
            return;
        }

        Module neoForgeModule = layer.findModule("neoforge")
                .orElseThrow(() -> new IllegalStateException("NeoForge game module was not found"));
        getClass().getModule().addReads(neoForgeModule);
        Class<?> fallback = Class.forName(neoForgeModule,
                "net.neoforged.neoforge.client.loading.NoVizFallback");
        if (fallback == null) {
            throw new IllegalStateException("NeoForge NoVizFallback is unavailable");
        }
        loadingOverlay = findLoadingOverlayBridge(fallback);
        LOGGER.info("HaikalatHost overlay is unavailable; using NeoForge's loading overlay fallback");
    }

    private static Class<?> findClass(ModuleLayer layer, String className) {
        return layer.modules().stream()
                .map(module -> Class.forName(module, className))
                .filter(candidate -> candidate != null)
                .findFirst()
                .orElse(null);
    }

    private static Method findLoadingOverlayBridge(Class<?> bridgeClass) {
        try {
            return bridgeClass.getMethod("loadingOverlay",
                    Supplier.class, Supplier.class, Consumer.class, boolean.class);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(
                    "No loadingOverlay bridge was found on " + bridgeClass.getName(), exception);
        }
    }

    @Override
    public void periodicTick() {
        if (!handedOff && window != MemoryUtil.NULL) {
            glfwPollEvents();
            renderFrame();
        }
    }

    @Override
    public String getGLVersion() {
        return glVersion;
    }

    @Override
    public void crash(String message) {
        LOGGER.error("Early window failure: {}", message);
        TinyFileDialogs.tinyfd_messageBox("Minecraft: Fracture Loader", message,
                "ok", "error", false);
    }

    private void failWindowCreation(String message) {
        crash(message);
        throw new IllegalStateException(message);
    }

    private static Optional<String> getLastGlfwError() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer descriptionPointer = stack.mallocPointer(1);
            int error = glfwGetError(descriptionPointer);
            if (error == GLFW_NO_ERROR) {
                return Optional.empty();
            }
            long address = descriptionPointer.get(0);
            String description = address == MemoryUtil.NULL ? "" : MemoryUtil.memUTF8(address);
            return Optional.of(String.format(Locale.ROOT, "[0x%X] %s", error, description).trim());
        }
    }
}
