package org.hismeo.haikalathost.client.staticmesh;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;

import java.util.IdentityHashMap;

/** Tracks the RenderType currently represented by a direct BufferSource consumer. */
public final class StaticVertexConsumerRegistry {
    private static final IdentityHashMap<VertexConsumer, RenderType> ROUTES =
            new IdentityHashMap<>();

    private StaticVertexConsumerRegistry() {
    }

    public static void observe(VertexConsumer consumer, RenderType renderType) {
        RenderSystem.assertOnRenderThread();
        if (consumer != null && renderType != null) ROUTES.put(consumer, renderType);
    }

    public static RenderType resolve(VertexConsumer consumer) {
        RenderSystem.assertOnRenderThread();
        return ROUTES.get(consumer);
    }

    public static void release(VertexConsumer consumer) {
        RenderSystem.assertOnRenderThread();
        ROUTES.remove(consumer);
    }

    public static void clear() {
        RenderSystem.assertOnRenderThread();
        ROUTES.clear();
    }
}
