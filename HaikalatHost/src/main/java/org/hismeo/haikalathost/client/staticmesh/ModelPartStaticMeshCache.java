package org.hismeo.haikalathost.client.staticmesh;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import org.hismeo.haikalathost.client.runtime.MinecraftRuntimeLifecycle;
import org.hismeo.haikalathost.HaikalatHost;
import org.joml.Matrix4f;

import java.util.IdentityHashMap;

/** H3 cache: immutable ModelPart cube geometry plus per-frame transform-only instances. */
public final class ModelPartStaticMeshCache {
    private static final int INITIAL_CUBE_BYTES = 1024;
    private static final IdentityHashMap<ModelPart.Cube, Boolean> CACHED =
            new IdentityHashMap<>();

    private ModelPartStaticMeshCache() {
    }

    public static boolean capture(
            ModelPart.Cube cube,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            int color
    ) {
        RenderSystem.assertOnRenderThread();
        if (color != -1) return false;
        RenderType renderType = StaticVertexConsumerRegistry.resolve(consumer);
        if (renderType == null || !ensureCaptured(cube, packedLight, packedOverlay)) return false;

        Matrix4f combinedModelView =
                new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.pose());
        return MinecraftRuntimeLifecycle.captureStaticModelMesh(
                cube,
                combinedModelView,
                RenderSystem.getProjectionMatrix(),
                renderType);
    }

    public static void clear() {
        RenderSystem.assertOnRenderThread();
        for (ModelPart.Cube cube : CACHED.keySet()) {
            MinecraftRuntimeLifecycle.invalidateStaticMesh(cube);
            StaticVertexBufferRegistry.release(cube);
        }
        CACHED.clear();
    }

    public static int cachedCubeCount() {
        RenderSystem.assertOnRenderThread();
        return CACHED.size();
    }

    private static boolean ensureCaptured(
            ModelPart.Cube cube, int packedLight, int packedOverlay) {
        if (CACHED.containsKey(cube)) return true;

        try (ByteBufferBuilder bytes = new ByteBufferBuilder(INITIAL_CUBE_BYTES)) {
            BufferBuilder builder = new BufferBuilder(
                    bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            cube.compile(new PoseStack().last(), builder, packedLight, packedOverlay, -1);
            MeshData meshData = builder.buildOrThrow();
            if (!StaticVertexBufferRegistry.captureMesh(cube, meshData)) return false;
        }
        CACHED.put(cube, Boolean.TRUE);
        int count = CACHED.size();
        if ((count & (count - 1)) == 0) {
            HaikalatHost.LOGGER.info("H3 model-part cache cubes={}", count);
        }
        return true;
    }
}
