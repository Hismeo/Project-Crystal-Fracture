package org.hismeo.haikalathost.client.staticmesh;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import org.hismeo.haikalathost.HaikalatHost;
import org.hismeo.haikalathost.client.runtime.MinecraftRuntimeLifecycle;
import org.joml.Matrix4f;

import java.util.IdentityHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** H3 cache for untinted baked item models; glint and tinted items remain on generic capture. */
public final class ItemModelStaticMeshCache {
    private static final int INITIAL_MODEL_BYTES = 4096;
    private static final long QUAD_RANDOM_SEED = 42L;
    private static final IdentityHashMap<BakedModel, Boolean> CACHED =
            new IdentityHashMap<>();
    private static final Set<BakedModel> UNSUPPORTED =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private ItemModelStaticMeshCache() {
    }

    public static boolean capture(
            BakedModel model,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay
    ) {
        RenderSystem.assertOnRenderThread();
        RenderType renderType = StaticVertexConsumerRegistry.resolve(consumer);
        if (renderType == null || !ensureCaptured(model, packedLight, packedOverlay)) return false;

        Matrix4f combinedModelView =
                new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.pose());
        return MinecraftRuntimeLifecycle.captureStaticModelMesh(
                model,
                combinedModelView,
                RenderSystem.getProjectionMatrix(),
                renderType);
    }

    public static void clear() {
        RenderSystem.assertOnRenderThread();
        for (BakedModel model : CACHED.keySet()) {
            MinecraftRuntimeLifecycle.invalidateStaticMesh(model);
            StaticVertexBufferRegistry.release(model);
        }
        CACHED.clear();
        UNSUPPORTED.clear();
    }

    public static int cachedModelCount() {
        RenderSystem.assertOnRenderThread();
        return CACHED.size();
    }

    private static boolean ensureCaptured(
            BakedModel model, int packedLight, int packedOverlay) {
        if (CACHED.containsKey(model)) return true;

        if (UNSUPPORTED.contains(model)) return false;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(INITIAL_MODEL_BYTES)) {
            BufferBuilder builder = new BufferBuilder(
                    bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            PoseStack.Pose identity = new PoseStack().last();
            RandomSource random = RandomSource.create();
            int quadCount = 0;
            for (Direction direction : Direction.values()) {
                random.setSeed(QUAD_RANDOM_SEED);
                List<BakedQuad> quads = model.getQuads(null, direction, random);
                if (!appendUntinted(builder, identity, quads, packedLight, packedOverlay)) {
                    UNSUPPORTED.add(model);
                    return false;
                }
                quadCount += quads.size();
            }
            random.setSeed(QUAD_RANDOM_SEED);
            List<BakedQuad> unculled = model.getQuads(null, null, random);
            if (!appendUntinted(builder, identity, unculled, packedLight, packedOverlay)) {
                UNSUPPORTED.add(model);
                return false;
            }
            quadCount += unculled.size();
            if (quadCount == 0) {
                UNSUPPORTED.add(model);
                return false;
            }

            MeshData meshData = builder.buildOrThrow();
            if (!StaticVertexBufferRegistry.captureMesh(model, meshData)) {
                UNSUPPORTED.add(model);
                return false;
            }
        }
        CACHED.put(model, Boolean.TRUE);
        int count = CACHED.size();
        if ((count & (count - 1)) == 0) {
            HaikalatHost.LOGGER.info("H3 item-model cache meshes={}", count);
        }
        return true;
    }

    private static boolean appendUntinted(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            List<BakedQuad> quads,
            int packedLight,
            int packedOverlay
    ) {
        for (BakedQuad quad : quads) {
            if (quad.isTinted()) return false;
            consumer.putBulkData(
                    pose, quad, 1.0F, 1.0F, 1.0F, 1.0F,
                    packedLight, packedOverlay, true);
        }
        return true;
    }
}
