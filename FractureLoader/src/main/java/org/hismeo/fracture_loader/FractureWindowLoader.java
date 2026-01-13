/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package org.hismeo.fracture_loader;

import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.earlydisplay.SimpleFont;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

/**
 * The Loading Window that is opened Immediately after Forge starts.
 * It is called from the ModDirTransformerDiscoverer, the soonest method that ModLauncher calls into Forge code.
 * In this way, we can be sure that this will not run before any transformer or injection.
 *
 * The window itself is spun off into a secondary thread, and is handed off to the main game by Forge.
 *
 * Because it is created so early, this thread will "absorb" the context from OpenGL.
 * Therefore, it is of utmost importance that the Context is made Current for the main thread before handoff,
 * otherwise OS X will crash out.
 *
 * Based on the prior ClientVisualization, with some personal touches.
 */
public class FractureWindowLoader extends DisplayWindow {
    @Override
    public String name() {
        return "fracturewindowloader";
    }

    @Override
    public Runnable initialize(String[] arguments) {
        super.initialize(arguments);
        return () -> {};
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
