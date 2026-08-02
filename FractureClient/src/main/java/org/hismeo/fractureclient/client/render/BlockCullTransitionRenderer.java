package org.hismeo.fractureclient.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.hismeo.fractureclient.client.config.OrthographicCameraConfig;
import org.hismeo.fractureclient.client.control.CameraModeController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Renders short-lived translucent copies while the asynchronously compiled chunk mesh changes
 * between its normal and cutaway states.
 *
 * <p>Solid chunk vertices cannot be alpha blended in place. Keeping this transition in a small
 * dynamic pass avoids rebuilding a section every animation frame and leaves the normal chunk
 * shaders untouched.</p>
 */
public final class BlockCullTransitionRenderer {
    private static final int MESH_SETTLE_TICKS = 6;
    private static final float MIN_RENDER_ALPHA = 1.0F / 255.0F;
    private static final RenderType TRANSITION_RENDER_TYPE = RenderType.create(
            "fracture_client_block_cull_transition",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            RenderType.SMALL_BUFFER_SIZE,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setShaderState(RenderStateShard.RENDERTYPE_TRANSLUCENT_MOVING_BLOCK_SHADER)
                    .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    // The transition should test against the world depth but never hide later
                    // translucent geometry, especially through transparent door textures.
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(true)
    );

    private static final Map<Long, Transition> TRANSITIONS = new HashMap<>();
    private static ClientLevel trackedLevel;
    private static long currentTick;

    private BlockCullTransitionRenderer() {
    }

    /** Starts or reverses a transition toward the fully cut state. */
    public static void beginCull(ClientLevel level, long packedPos, BlockState state, long tick) {
        if (fadeTicks() <= 0) {
            TRANSITIONS.remove(packedPos);
            return;
        }
        transitionTo(level, packedPos, state, tick, 0.0F, tick);
    }

    /** Reverses an in-flight reveal if the block obstructs the player again. */
    public static void hideIfTransitioning(
            ClientLevel level,
            long packedPos,
            BlockState state,
            long tick
    ) {
        if (TRANSITIONS.containsKey(packedPos)) {
            beginCull(level, packedPos, state, tick);
        }
    }

    /** Reveals a block dynamically while its ordinary chunk mesh is still deliberately absent. */
    public static void beginReveal(ClientLevel level, long packedPos, BlockState state, long tick) {
        if (fadeTicks() <= 0) {
            return;
        }
        transitionTo(level, packedPos, state, tick, 1.0F, Long.MAX_VALUE);
    }

    /** Keeps a fully revealed copy briefly while the restored chunk mesh compiles and uploads. */
    public static void chunkRestorePublished(
            ClientLevel level,
            long packedPos,
            BlockState state,
            long tick
    ) {
        int fadeTicks = fadeTicks();
        if (fadeTicks <= 0) {
            TRANSITIONS.remove(packedPos);
            return;
        }

        Transition transition = transitionTo(
                level,
                packedPos,
                state,
                tick,
                1.0F,
                tick + fadeTicks + MESH_SETTLE_TICKS
        );
        long animationEnd = tick + (long)Math.ceil(transition.durationTicks);
        transition.lingerUntilTick = animationEnd + MESH_SETTLE_TICKS;
    }

    public static void discard(long packedPos) {
        TRANSITIONS.remove(packedPos);
    }

    public static void tick(ClientLevel level, long tick) {
        if (trackedLevel != level) {
            reset(level);
        }
        currentTick = tick;

        Iterator<Map.Entry<Long, Transition>> iterator = TRANSITIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Transition> entry = iterator.next();
            long packedPos = entry.getKey();
            Transition transition = entry.getValue();
            BlockState currentState = level.getBlockState(BlockPos.of(packedPos));
            if (!currentState.equals(transition.state)) {
                iterator.remove();
                continue;
            }

            if (!transition.isAnimationComplete(tick)) {
                continue;
            }
            if (transition.targetAlpha <= MIN_RENDER_ALPHA
                    || tick > transition.lingerUntilTick) {
                iterator.remove();
            }
        }
    }

    public static void reset(ClientLevel level) {
        trackedLevel = level;
        currentTick = 0L;
        TRANSITIONS.clear();
    }

    public static void reset() {
        trackedLevel = null;
        currentTick = 0L;
        TRANSITIONS.clear();
    }

    public static void render(RenderLevelStageEvent event, Minecraft minecraft) {
        if (!CameraModeController.isOrthographic()
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || TRANSITIONS.isEmpty()
                || minecraft.level == null
                || minecraft.level != trackedLevel) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double renderTime = currentTick + Math.max(0.0F, Math.min(1.0F, partialTick));
        Vec3 cameraPosition = event.getCamera().getPosition();
        List<RenderEntry> entries = collectRenderEntries(minecraft.level, cameraPosition, renderTime);
        if (entries.isEmpty()) {
            return;
        }

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer target = buffers.getBuffer(TRANSITION_RENDER_TYPE);
        PoseStack poseStack = event.getPoseStack();
        RandomSource random = RandomSource.create();

        for (RenderEntry entry : entries) {
            BlockPos pos = entry.pos();
            BlockState state = entry.state();
            var model = minecraft.getBlockRenderer().getBlockModel(state);
            var modelData = minecraft.level.getModelData(pos);
            modelData = model.getModelData(minecraft.level, pos, state, modelData);
            random.setSeed(state.getSeed(pos));
            var sourceRenderTypes = model.getRenderTypes(state, random, modelData);

            poseStack.pushPose();
            poseStack.translate(
                    pos.getX() - cameraPosition.x,
                    pos.getY() - cameraPosition.y,
                    pos.getZ() - cameraPosition.z
            );
            VertexConsumer alphaConsumer = new AlphaVertexConsumer(target, entry.alpha());
            for (RenderType sourceRenderType : sourceRenderTypes) {
                random.setSeed(state.getSeed(pos));
                minecraft.getBlockRenderer().renderBatched(
                        state,
                        pos,
                        minecraft.level,
                        poseStack,
                        alphaConsumer,
                        true,
                        random,
                        modelData,
                        sourceRenderType
                );
            }
            poseStack.popPose();
        }

        buffers.endBatch(TRANSITION_RENDER_TYPE);
    }

    private static List<RenderEntry> collectRenderEntries(
            ClientLevel level,
            Vec3 cameraPosition,
            double renderTime
    ) {
        List<RenderEntry> entries = new ArrayList<>(TRANSITIONS.size());
        for (Map.Entry<Long, Transition> entry : TRANSITIONS.entrySet()) {
            long packedPos = entry.getKey();
            Transition transition = entry.getValue();
            float alpha = transition.alphaAt(renderTime);
            if (alpha <= MIN_RENDER_ALPHA) {
                continue;
            }

            BlockPos pos = BlockPos.of(packedPos);
            BlockState state = level.getBlockState(pos);
            if (!state.equals(transition.state)) {
                continue;
            }
            double distanceSquared = cameraPosition.distanceToSqr(
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5
            );
            entries.add(new RenderEntry(pos, state, alpha, distanceSquared));
        }
        entries.sort(Comparator.comparingDouble(RenderEntry::distanceSquared).reversed());
        return entries;
    }

    private static Transition transitionTo(
            ClientLevel level,
            long packedPos,
            BlockState state,
            long tick,
            float targetAlpha,
            long lingerUntilTick
    ) {
        if (trackedLevel != level) {
            reset(level);
        }

        Transition existing = TRANSITIONS.get(packedPos);
        if (existing != null
                && existing.state.equals(state)
                && Math.abs(existing.targetAlpha - targetAlpha) < 0.0001F) {
            existing.lingerUntilTick = Math.max(existing.lingerUntilTick, lingerUntilTick);
            return existing;
        }

        float startAlpha = existing == null || !existing.state.equals(state)
                ? 1.0F - targetAlpha
                : existing.alphaAt(tick);
        float duration = fadeTicks() * Math.abs(targetAlpha - startAlpha);
        Transition next = new Transition(
                state,
                startAlpha,
                targetAlpha,
                tick,
                duration,
                lingerUntilTick
        );
        TRANSITIONS.put(packedPos, next);
        return next;
    }

    private static int fadeTicks() {
        return Math.max(0, Math.min(40, OrthographicCameraConfig.cullFadeDurationTicks));
    }

    private record RenderEntry(
            BlockPos pos,
            BlockState state,
            float alpha,
            double distanceSquared
    ) {
    }

    private static final class Transition {
        private final BlockState state;
        private final float startAlpha;
        private final float targetAlpha;
        private final double startTick;
        private final double durationTicks;
        private long lingerUntilTick;

        private Transition(
                BlockState state,
                float startAlpha,
                float targetAlpha,
                double startTick,
                double durationTicks,
                long lingerUntilTick
        ) {
            this.state = state;
            this.startAlpha = startAlpha;
            this.targetAlpha = targetAlpha;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
            this.lingerUntilTick = lingerUntilTick;
        }

        private float alphaAt(double tick) {
            if (durationTicks <= 0.0001) {
                return targetAlpha;
            }
            double linear = Math.max(0.0, Math.min(1.0, (tick - startTick) / durationTicks));
            float progress = (float)(linear * linear * (3.0 - 2.0 * linear));
            return startAlpha + (targetAlpha - startAlpha) * progress;
        }

        private boolean isAnimationComplete(long tick) {
            return tick >= startTick + durationTicks;
        }
    }

    private static final class AlphaVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float alpha;

        private AlphaVertexConsumer(VertexConsumer delegate, float alpha) {
            this.delegate = delegate;
            this.alpha = Math.max(0.0F, Math.min(1.0F, alpha));
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int sourceAlpha) {
            delegate.setColor(red, green, blue, Math.round(sourceAlpha * alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            delegate.setNormal(normalX, normalY, normalZ);
            return this;
        }
    }
}
