/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package org.hismeo.fracture_loader;

import joptsimple.OptionParser;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.loading.FMLConfig;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * The Loading Window that is opened Immediately after Forge starts.
 * It is called from the ModDirTransformerDiscoverer, the soonest method that ModLauncher calls into Forge code.
 * In this way, we can be sure that this will not run before any transformer or injection.
 * <p>
 * The window itself is spun off into a secondary thread, and is handed off to the main game by Forge.
 * <p>
 * Because it is created so early, this thread will "absorb" the context from OpenGL.
 * Therefore, it is of utmost importance that the Context is made Current for the main thread before handoff,
 * otherwise OS X will crash out.
 * <p>
 * Based on the prior ClientVisualization, with some personal touches.
 */
//TODO
public class FractureWindowLoader extends DisplayWindow {
    public static final Logger LOGGER = LoggerFactory.getLogger("fracturewindowloader");

    protected String mcVersion;
    protected String neoForgeVersion;
    protected int winWidth;
    protected int winHeight;
    protected int frameWidth;
    protected int frameHeight;
    protected boolean maximized;
    protected long window;


    @Override
    public String name() {
        return "fracturewindowloader";
    }

    @Override
    public Runnable initialize(String[] arguments) {
        final OptionParser parser = new OptionParser();
        var mcVersionOp = parser.accepts("fml.mcVersion")
                .withRequiredArg().ofType(String.class);
        var neoForgeVersionOp = parser.accepts("fml.neoForgeVersion")
                .withRequiredArg().ofType(String.class);
        var widthOp = parser.accepts("width")
                .withRequiredArg().ofType(Integer.class)
                .defaultsTo(FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_WIDTH));
        var heightOp = parser.accepts("height")
                .withRequiredArg().ofType(Integer.class)
                .defaultsTo(FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_HEIGHT));
        var maximizedOp = parser.accepts("earlywindow.maximized");
        parser.allowsUnrecognizedOptions();
        var parsed = parser.parse(arguments);
//        fbScale = FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_FBSCALE);
        mcVersion = parsed.valueOf(mcVersionOp);
        neoForgeVersion = parsed.valueOf(neoForgeVersionOp);
        winWidth = parsed.valueOf(widthOp);
        winHeight = parsed.valueOf(heightOp);
        maximized = parsed.has(maximizedOp) || FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_MAXIMIZED);
        FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_WIDTH, winWidth);
        FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_HEIGHT, winHeight);
        return taskStart();
    }

    protected Runnable taskStart() {
        return () -> {
            createWindow();
        };
    }

    public void createWindow() {
        long glfwInitBegin = System.nanoTime();
        if (!glfwInit()) {
            crash("We are unable to initialize the graphics system.\nglfwInit failed.\n");
            throw new IllegalStateException("Unable to initialize GLFW");
        }        long glfwInitEnd = System.nanoTime();

        if (glfwInitEnd - glfwInitBegin > 1e9) {
            LOGGER.error("WARNING : glfwInit took {} seconds to start.", (glfwInitEnd - glfwInitBegin) / 1.0e9);
        }

        getLastGlfwError().ifPresent(error -> LOGGER.error("Suppressing Last GLFW error: {}", error));

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
        glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_NATIVE_CONTEXT_API);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        String vanillaWindowTitle = "Minecraft* " + mcVersion;
        glfwWindowHintString(GLFW_X11_CLASS_NAME, vanillaWindowTitle);
        glfwWindowHintString(GLFW_X11_INSTANCE_NAME, vanillaWindowTitle);

        long primaryMonitor = glfwGetPrimaryMonitor();
        if (primaryMonitor == 0) {
            LOGGER.error("Failed to find a primary monitor - this means LWJGL isn't working properly");
            crash("Failed to locate a primary monitor.\nglfwGetPrimaryMonitor failed.\n");
            throw new IllegalStateException("Can't find a primary monitor");
        }
        GLFWVidMode vidmode = glfwGetVideoMode(primaryMonitor);

        if (vidmode == null) {
            LOGGER.error("Failed to get the current display video mode.");
            crash("Failed to get current display resolution.\nglfwGetVideoMode failed.\n");
            throw new IllegalStateException("Can't get a resolution");
        }
        window = glfwCreateWindow(480, 640, vanillaWindowTitle, 0L, 0L);
    }

    protected static Optional<String> getLastGlfwError() {
        try (MemoryStack memorystack = MemoryStack.stackPush()) {
            PointerBuffer pointerbuffer = memorystack.mallocPointer(1);
            int error = glfwGetError(pointerbuffer);
            if (error != GLFW_NO_ERROR) {
                long pDescription = pointerbuffer.get();
                String description = pDescription == 0L ? null : MemoryUtil.memUTF8(pDescription);
                if (description != null) {
                    return Optional.of(String.format(Locale.ROOT, "[0x%X] %s", error, description));
                } else {
                    return Optional.of(String.format(Locale.ROOT, "[0x%X]", error));
                }
            }
        }
        return Optional.empty();
    }


    @Override
    public long setupMinecraftWindow(IntSupplier width, IntSupplier height, Supplier<String> title, LongSupplier monitorSupplier) {
        return window;
    }

    @Override
    public void periodicTick() {
        glfwPollEvents();
    }

    @Override
    public void close() {
    }

    private Method loadingOverlay;

    @SuppressWarnings("unchecked")
    @Override
    public <T> Supplier<T> loadingOverlay(final Supplier<?> mc, final Supplier<?> ri, final Consumer<Optional<Throwable>> ex, final boolean fade) {
        try {
            return (Supplier<T>) loadingOverlay.invoke(null, mc, ri, ex, this);
        } catch (Throwable e) {
            throw new IllegalStateException("How did you get here?", e);
        }
    }

    @Override
    public void updateModuleReads(final ModuleLayer layer) {
        var fm = layer.findModule("neoforge").orElseThrow();
        getClass().getModule().addReads(fm);
        var clz = Class.forName(fm, "net.neoforged.neoforge.client.loading.NeoForgeLoadingOverlay");
        var methods = Arrays.stream(clz.getMethods()).filter(m -> Modifier.isStatic(m.getModifiers())).collect(Collectors.toMap(Method::getName, Function.identity()));
        loadingOverlay = methods.get("newInstance");
    }
}
