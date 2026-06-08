package org.hismeo.fractureclient.client.control;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.hismeo.fractureclient.client.config.OrthographicCameraConfig;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class BlockCullController {
    private static final LongOpenHashSet CULLED_BLOCKS = new LongOpenHashSet();
    private static final double MIN_DISTANCE = 1.0e-4;
    private static final boolean DEBUG_RENDER_BOX = true;
    private static final int CAMERA_FACE_COLOR = 0xAA33CCFF;
    private static final int BOX_EDGE_COLOR = 0xAA66FF66;
    private static final double CULL_LENGTH_BY_SIZE = 5.0;
    private static final double CAMERA_FACE_WIDTH = 14.0;
    private static final double CAMERA_FACE_HEIGHT = 8.0;
    private static final double PLAYER_FACE_WIDTH = 5.0;
    private static final double PLAYER_FACE_HEIGHT = 3.0;
    private static final double BLOCK_EPS = 1.0;
    private static final Vec3 FRUSTUM_OFFSET = new Vec3(0.0, 0.0, 0.0);

    private BlockCullController() {}

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.gameRenderer == null) return;

        if (!OrthographicCameraConfig.isCull) {
            clearAllCulling(minecraft);
            return;
        }

        LongOpenHashSet nextCullSet = new LongOpenHashSet();
        Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 playerFeetPos = new Vec3(
                minecraft.player.getX(),
                minecraft.player.getBoundingBox().minY,
                minecraft.player.getZ()
        );
        Vec3 lookAxis = new Vec3(minecraft.gameRenderer.getMainCamera().getLookVector()).normalize();
        AxisSegment segment = resolveCullSegment(cameraPos, playerFeetPos, lookAxis);

        addBoxCulling(segment.origin, segment.target, ceilToInt(playerFeetPos.y), nextCullSet);

        if (!sameSet(CULLED_BLOCKS, nextCullSet)) {
            markDirtyByDiff(minecraft, CULLED_BLOCKS, nextCullSet);
            CULLED_BLOCKS.clear();
            CULLED_BLOCKS.addAll(nextCullSet);
        }
    }

    public static boolean shouldCull(BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        return CULLED_BLOCKS.contains(pos.asLong());
    }

    public static void renderDebugCullBoxWorld(PoseStack poseStack, Vec3 cameraRenderPos, MultiBufferSource.BufferSource buffer, Minecraft minecraft) {
        if (!DEBUG_RENDER_BOX || !OrthographicCameraConfig.isCull) return;
        if (minecraft.player == null || minecraft.gameRenderer == null) return;

        Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 playerFeetPos = new Vec3(
                minecraft.player.getX(),
                minecraft.player.getBoundingBox().minY,
                minecraft.player.getZ()
        );
        Vec3 lookAxis = new Vec3(minecraft.gameRenderer.getMainCamera().getLookVector()).normalize();
        AxisSegment segment = resolveCullSegment(cameraPos, playerFeetPos, lookAxis);

        Vec3 axis = segment.target.subtract(segment.origin);
        double height = axis.length();
        if (height < MIN_DISTANCE) return;

        Vec3 axisN = axis.scale(1.0 / height);
        Vector3f left = minecraft.gameRenderer.getMainCamera().getLeftVector();
        Vector3f up = minecraft.gameRenderer.getMainCamera().getUpVector();
        Vec3 tangentU = new Vec3(left).normalize();
        Vec3 tangentV = new Vec3(up).normalize();
        Vec3 offset = tangentU.scale(FRUSTUM_OFFSET.x).add(tangentV.scale(FRUSTUM_OFFSET.y)).add(axisN.scale(FRUSTUM_OFFSET.z));

        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
        renderDebugFrustumBox(
                poseStack,
                consumer,
                cameraRenderPos,
                segment.origin.add(offset),
                segment.target.add(offset),
                tangentU,
                tangentV
        );
    }

    private static void renderDebugFrustumBox(
            PoseStack poseStack,
            VertexConsumer consumer,
            Vec3 cameraRenderPos,
            Vec3 origin,
            Vec3 target,
            Vec3 tangentU,
            Vec3 tangentV
    ) {
        Vec3 nearU = tangentU.scale(CAMERA_FACE_WIDTH * 0.5);
        Vec3 nearV = tangentV.scale(CAMERA_FACE_HEIGHT * 0.5);
        Vec3 farU = tangentU.scale(PLAYER_FACE_WIDTH * 0.5);
        Vec3 farV = tangentV.scale(PLAYER_FACE_HEIGHT * 0.5);

        Vec3[] near = new Vec3[]{
                origin.add(nearU).add(nearV),
                origin.add(nearU).subtract(nearV),
                origin.subtract(nearU).subtract(nearV),
                origin.subtract(nearU).add(nearV)
        };
        Vec3[] far = new Vec3[]{
                target.add(farU).add(farV),
                target.add(farU).subtract(farV),
                target.subtract(farU).subtract(farV),
                target.subtract(farU).add(farV)
        };

        for (int i = 0; i < 4; i++) {
            int next = (i + 1) & 3;
            drawLine3D(poseStack, consumer, near[i].subtract(cameraRenderPos), near[next].subtract(cameraRenderPos), CAMERA_FACE_COLOR);
            drawLine3D(poseStack, consumer, far[i].subtract(cameraRenderPos), far[next].subtract(cameraRenderPos), BOX_EDGE_COLOR);
            drawLine3D(poseStack, consumer, near[i].subtract(cameraRenderPos), far[i].subtract(cameraRenderPos), BOX_EDGE_COLOR);
        }
    }

    private static void addBoxCulling(Vec3 origin, Vec3 target, int minCullY, LongOpenHashSet out) {
        Vec3 axis = target.subtract(origin);
        double height = axis.length();
        if (height < MIN_DISTANCE) return;

        Vec3 axisN = axis.scale(1.0 / height);
        Vec3 helper = Math.abs(axisN.y) < 0.99 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 tangentU = axisN.cross(helper).normalize();
        Vec3 tangentV = axisN.cross(tangentU).normalize();

        Vec3 offset = tangentU.scale(FRUSTUM_OFFSET.x).add(tangentV.scale(FRUSTUM_OFFSET.y)).add(axisN.scale(FRUSTUM_OFFSET.z));
        Vec3 shiftedOrigin = origin.add(offset);
        Vec3 shiftedTarget = target.add(offset);

        double nearR = Math.sqrt((CAMERA_FACE_WIDTH * 0.5) * (CAMERA_FACE_WIDTH * 0.5) + (CAMERA_FACE_HEIGHT * 0.5) * (CAMERA_FACE_HEIGHT * 0.5));
        double farR = Math.sqrt((PLAYER_FACE_WIDTH * 0.5) * (PLAYER_FACE_WIDTH * 0.5) + (PLAYER_FACE_HEIGHT * 0.5) * (PLAYER_FACE_HEIGHT * 0.5));
        double maxR = Math.max(nearR, farR) + BLOCK_EPS;
        int minX = floorToInt(Math.min(shiftedOrigin.x, shiftedTarget.x) - maxR);
        int maxX = floorToInt(Math.max(shiftedOrigin.x, shiftedTarget.x) + maxR);
        int minY = Math.max(floorToInt(Math.min(shiftedOrigin.y, shiftedTarget.y) - maxR), minCullY);
        int maxY = floorToInt(Math.max(shiftedOrigin.y, shiftedTarget.y) + maxR);
        int minZ = floorToInt(Math.min(shiftedOrigin.z, shiftedTarget.z) - maxR);
        int maxZ = floorToInt(Math.max(shiftedOrigin.z, shiftedTarget.z) + maxR);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (isInsideFrustumBox(x, y, z, shiftedOrigin, axisN, tangentU, tangentV, height)) {
                        out.add(BlockPos.asLong(x, y, z));
                    }
                }
            }
        }
    }

    private static boolean isInsideFrustumBox(
            int x,
            int y,
            int z,
            Vec3 origin,
            Vec3 axisN,
            Vec3 tangentU,
            Vec3 tangentV,
            double height
    ) {
        double px = x + 0.5 - origin.x;
        double py = y + 0.5 - origin.y;
        double pz = z + 0.5 - origin.z;

        double t = px * axisN.x + py * axisN.y + pz * axisN.z;
        if (t <= -BLOCK_EPS || t >= height + BLOCK_EPS) return false;

        double u = px * tangentU.x + py * tangentU.y + pz * tangentU.z;
        double v = px * tangentV.x + py * tangentV.y + pz * tangentV.z;
        double tClamped = Math.max(0.0, Math.min(height, t));
        double alpha = tClamped / height;
        double halfW = (CAMERA_FACE_WIDTH * 0.5) + ((PLAYER_FACE_WIDTH * 0.5) - (CAMERA_FACE_WIDTH * 0.5)) * alpha;
        double halfH = (CAMERA_FACE_HEIGHT * 0.5) + ((PLAYER_FACE_HEIGHT * 0.5) - (CAMERA_FACE_HEIGHT * 0.5)) * alpha;
        return Math.abs(u) <= halfW + BLOCK_EPS
                && Math.abs(v) <= halfH + BLOCK_EPS;
    }

    private static AxisSegment resolveCullSegment(Vec3 cameraPos, Vec3 playerFeetPos, Vec3 lookAxis) {
        Vec3 toPlayer = playerFeetPos.subtract(cameraPos);
        Vec3 axis = lookAxis;
        if (axis.dot(toPlayer) < 0.0) {
            axis = axis.scale(-1.0);
        }
        double projectedToPlayer = Math.max(MIN_DISTANCE, axis.dot(toPlayer));
        Vec3 playerSideTarget = cameraPos.add(axis.scale(projectedToPlayer));
        double cameraBackLength = Math.max(MIN_DISTANCE, OrthographicCameraConfig.size * CULL_LENGTH_BY_SIZE);
        Vec3 cameraSideOrigin = cameraPos.subtract(axis.scale(cameraBackLength));
        return new AxisSegment(cameraSideOrigin, playerSideTarget);
    }

    private record AxisSegment(Vec3 origin, Vec3 target) {}

    private static boolean sameSet(LongOpenHashSet a, LongOpenHashSet b) {
        if (a.size() != b.size()) return false;
        LongIterator iterator = a.iterator();
        while (iterator.hasNext()) {
            if (!b.contains(iterator.nextLong())) return false;
        }
        return true;
    }

    private static void clearAllCulling(Minecraft minecraft) {
        if (CULLED_BLOCKS.isEmpty()) return;
        LongOpenHashSet empty = new LongOpenHashSet();
        markDirtyByDiff(minecraft, CULLED_BLOCKS, empty);
        CULLED_BLOCKS.clear();
    }

    private static void markDirtyByDiff(Minecraft minecraft, LongOpenHashSet oldSet, LongOpenHashSet newSet) {
        LongIterator oldIterator = oldSet.iterator();
        while (oldIterator.hasNext()) {
            long packed = oldIterator.nextLong();
            if (!newSet.contains(packed)) {
                markBlockDirty(minecraft, packed);
            }
        }

        LongIterator newIterator = newSet.iterator();
        while (newIterator.hasNext()) {
            long packed = newIterator.nextLong();
            if (!oldSet.contains(packed)) {
                markBlockDirty(minecraft, packed);
            }
        }
    }

    private static void markBlockDirty(Minecraft minecraft, long packedPos) {
        BlockPos pos = BlockPos.of(packedPos);
        minecraft.levelRenderer.setBlocksDirty(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
    }

    private static void drawLine3D(PoseStack poseStack, VertexConsumer consumer, Vec3 a, Vec3 b, int color) {
        float nx = (float) (b.x - a.x);
        float ny = (float) (b.y - a.y);
        float nz = (float) (b.z - a.z);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0e-6f) return;
        nx /= len;
        ny /= len;
        nz /= len;

        poseStack.pushPose();
        PoseStack.Pose last = poseStack.last();
        Matrix4f matrix4f = last.pose();
        consumer.addVertex(matrix4f, (float) a.x, (float) a.y, (float) a.z)
                .setColor(color)
                .setNormal(last, nx, ny, nz);
        consumer.addVertex(matrix4f, (float) b.x, (float) b.y, (float) b.z)
                .setColor(color)
                .setNormal(last, nx, ny, nz);
        poseStack.popPose();
    }

    private static int floorToInt(double value) {
        int i = (int) value;
        return value < (double) i ? i - 1 : i;
    }

    private static int ceilToInt(double value) {
        int i = (int) value;
        return value > (double) i ? i + 1 : i;
    }
}
