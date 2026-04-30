package org.hismeo.fractureclient.client.control;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
    private enum CullShape {
        CONE,
        BOX
    }

    private static final LongOpenHashSet CULLED_BLOCKS = new LongOpenHashSet();
    private static final double MIN_DISTANCE = 1.0e-4;
    private static final boolean DEBUG_RENDER_CONE = true;
    private static final int CONE_COLOR = 0xAA33CCFF;
    private static final int BOX_COLOR = 0xAA66FF66;
    private static final CullShape CULL_SHAPE = CullShape.BOX;
    // 正交模式下剔除长度按 size 缩放：length = size * CULL_LENGTH_BY_SIZE
    private static final double CULL_LENGTH_BY_SIZE = 5;
    // 允许剔除体越过玩家一点点，避免边界抖动
    private static final double LENGTH_EXTRA_PAST_PLAYER = 1.0;
    // 固定底面半径（世界坐标）
    private static final double BASE_RADIUS = 20.0;
    // 梯形体参数：摄像机面尺寸、玩家面尺寸（世界坐标）
    private static final double CAMERA_FACE_WIDTH = 14.0;
    private static final double CAMERA_FACE_HEIGHT = 8.0;
    private static final double PLAYER_FACE_WIDTH = 5.0;
    private static final double PLAYER_FACE_HEIGHT = 3.0;
    // 梯形体整体偏移（沿相机局部 left/up/look）
    private static final Vec3 FRUSTUM_OFFSET = new Vec3(0.0, 0.0, 0.0);
    // 方块中心判定时给一点容差，避免底面/边缘“差一格”漏剔除
    private static final double BLOCK_EPS = 1.0;

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

        if (CULL_SHAPE == CullShape.BOX) {
            addBoxCulling(segment.origin, segment.target, nextCullSet);
        } else {
            addConeCulling(segment.origin, segment.target, nextCullSet);
        }

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

    public static void renderDebugConeWorld(PoseStack poseStack, Vec3 cameraRenderPos, MultiBufferSource.BufferSource buffer, Minecraft minecraft) {
        if (!DEBUG_RENDER_CONE || !OrthographicCameraConfig.isCull) return;
        if (minecraft.player == null || minecraft.gameRenderer == null) return;

        Vec3 apex = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 baseCenter = new Vec3(
                minecraft.player.getX(),
                minecraft.player.getBoundingBox().minY,
                minecraft.player.getZ()
        );
        Vec3 lookAxis = new Vec3(minecraft.gameRenderer.getMainCamera().getLookVector()).normalize();
        AxisSegment segment = resolveCullSegment(apex, baseCenter, lookAxis);
        apex = segment.origin;
        baseCenter = segment.target;
        Vec3 axis = baseCenter.subtract(apex);
        double height = axis.length();
        if (height < MIN_DISTANCE) return;

        Vec3 axisN = axis.scale(1.0 / height);
        double baseRadius = BASE_RADIUS;

        Vector3f left = minecraft.gameRenderer.getMainCamera().getLeftVector();
        Vector3f up = minecraft.gameRenderer.getMainCamera().getUpVector();
        Vec3 tangentU = new Vec3(left).normalize();
        Vec3 tangentV = new Vec3(up).normalize();
        Vec3 offset = tangentU.scale(FRUSTUM_OFFSET.x).add(tangentV.scale(FRUSTUM_OFFSET.y)).add(axisN.scale(FRUSTUM_OFFSET.z));
        Vec3 shiftedApex = apex.add(offset);
        Vec3 shiftedBaseCenter = baseCenter.add(offset);

        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
        if (CULL_SHAPE == CullShape.BOX) {
            renderDebugFrustumBox(poseStack, consumer, cameraRenderPos, shiftedApex, shiftedBaseCenter, axisN, tangentU, tangentV);
        } else {
            renderDebugCone(poseStack, consumer, cameraRenderPos, shiftedApex, shiftedBaseCenter, baseRadius, tangentU, tangentV);
        }
    }

    private static void renderDebugCone(
            PoseStack poseStack,
            VertexConsumer consumer,
            Vec3 cameraRenderPos,
            Vec3 apex,
            Vec3 baseCenter,
            double baseRadius,
            Vec3 tangentU,
            Vec3 tangentV
    ) {
        int segments = 28;
        Vec3 apexRel = apex.subtract(cameraRenderPos);
        Vec3 first = null;
        Vec3 prev = null;
        for (int i = 0; i < segments; i++) {
            double rad = Math.toRadians((360.0 * i) / segments);
            double cx = Math.cos(rad) * baseRadius;
            double cy = Math.sin(rad) * baseRadius;
            Vec3 p = baseCenter.add(tangentU.scale(cx)).add(tangentV.scale(cy));
            Vec3 now = p.subtract(cameraRenderPos);
            if (first == null) first = now;
            if (prev != null) drawLine3D(poseStack, consumer, prev, now, CONE_COLOR);
            if (i % 4 == 0) drawLine3D(poseStack, consumer, apexRel, now, CONE_COLOR);
            prev = now;
        }
        if (first != null && prev != null) drawLine3D(poseStack, consumer, prev, first, CONE_COLOR);
    }

    private static void renderDebugFrustumBox(
            PoseStack poseStack,
            VertexConsumer consumer,
            Vec3 cameraRenderPos,
            Vec3 apex,
            Vec3 baseCenter,
            Vec3 axisN,
            Vec3 tangentU,
            Vec3 tangentV
    ) {
        Vec3 c0 = apex;
        Vec3 c1 = baseCenter;
        Vec3 uNear = tangentU.scale(CAMERA_FACE_WIDTH * 0.5);
        Vec3 vNear = tangentV.scale(CAMERA_FACE_HEIGHT * 0.5);
        Vec3 uFar = tangentU.scale(PLAYER_FACE_WIDTH * 0.5);
        Vec3 vFar = tangentV.scale(PLAYER_FACE_HEIGHT * 0.5);

        Vec3[] near = new Vec3[]{
                c0.add(uNear).add(vNear), c0.add(uNear).subtract(vNear), c0.subtract(uNear).subtract(vNear), c0.subtract(uNear).add(vNear)
        };
        Vec3[] far = new Vec3[]{
                c1.add(uFar).add(vFar), c1.add(uFar).subtract(vFar), c1.subtract(uFar).subtract(vFar), c1.subtract(uFar).add(vFar)
        };

        for (int i = 0; i < 4; i++) {
            int j = (i + 1) & 3;
            drawLine3D(poseStack, consumer, near[i].subtract(cameraRenderPos), near[j].subtract(cameraRenderPos), CONE_COLOR);
            drawLine3D(poseStack, consumer, far[i].subtract(cameraRenderPos), far[j].subtract(cameraRenderPos), BOX_COLOR);
            drawLine3D(poseStack, consumer, near[i].subtract(cameraRenderPos), far[i].subtract(cameraRenderPos), BOX_COLOR);
        }
    }

    private static void addConeCulling(Vec3 origin, Vec3 target, LongOpenHashSet out) {
        Vec3 axis = target.subtract(origin); // camera -> player, cone base at player side
        double height = axis.length();
        if (height < MIN_DISTANCE) return;

        Vec3 axisN = axis.scale(1.0 / height);
        double baseRadius = BASE_RADIUS;

        int minX = floorToInt(Math.min(origin.x, target.x) - baseRadius);
        int maxX = floorToInt(Math.max(origin.x, target.x) + baseRadius);
        int minY = floorToInt(Math.min(origin.y, target.y) - baseRadius);
        int maxY = floorToInt(Math.max(origin.y, target.y) + baseRadius);
        int minZ = floorToInt(Math.min(origin.z, target.z) - baseRadius);
        int maxZ = floorToInt(Math.max(origin.z, target.z) + baseRadius);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (isInsideCone(x, y, z, origin, axisN, height, baseRadius)) {
                        out.add(BlockPos.asLong(x, y, z));
                    }
                }
            }
        }
    }

    private static void addBoxCulling(Vec3 origin, Vec3 target, LongOpenHashSet out) {
        Vec3 axis = target.subtract(origin); // camera -> player-feet
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
        int minY = floorToInt(Math.min(shiftedOrigin.y, shiftedTarget.y) - maxR);
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

    private static boolean isInsideCone(int x, int y, int z, Vec3 origin, Vec3 axisN, double height, double baseRadius) {
        double px = x + 0.5 - origin.x;
        double py = y + 0.5 - origin.y;
        double pz = z + 0.5 - origin.z;

        double t = px * axisN.x + py * axisN.y + pz * axisN.z;
        if (t <= -BLOCK_EPS || t >= height + BLOCK_EPS) return false;

        double rel2 = px * px + py * py + pz * pz;
        double radial2 = rel2 - t * t;
        if (radial2 < 0.0) radial2 = 0.0;

        double tClamped = Math.max(0.0, Math.min(height, t));
        double radiusAtT = (tClamped / height) * baseRadius;
        radiusAtT += BLOCK_EPS;
        return radial2 <= radiusAtT * radiusAtT;
    }

    private static AxisSegment resolveCullSegment(Vec3 cameraPos, Vec3 playerFeetPos, Vec3 lookAxis) {
        Vec3 toPlayer = playerFeetPos.subtract(cameraPos);
        Vec3 axis = lookAxis;
        if (axis.dot(toPlayer) < 0.0) {
            axis = axis.scale(-1.0);
        }
        double projectedToPlayer = Math.max(MIN_DISTANCE, axis.dot(toPlayer));
        Vec3 playerSideTarget = cameraPos.add(axis.scale(projectedToPlayer));

        // 保持玩家侧长度不变，只向摄像机反方向延长
        double cameraBackLength = Math.max(MIN_DISTANCE, OrthographicCameraConfig.size * CULL_LENGTH_BY_SIZE);
        Vec3 cameraSideOrigin = cameraPos.subtract(axis.scale(cameraBackLength));
        return new AxisSegment(cameraSideOrigin, playerSideTarget);
    }

    private record AxisSegment(Vec3 origin, Vec3 target) {}

    private static boolean sameSet(LongOpenHashSet a, LongOpenHashSet b) {
        if (a.size() != b.size()) return false;
        LongIterator it = a.iterator();
        while (it.hasNext()) {
            if (!b.contains(it.nextLong())) return false;
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
        LongIterator itOld = oldSet.iterator();
        while (itOld.hasNext()) {
            long packed = itOld.nextLong();
            if (!newSet.contains(packed)) {
                markBlockDirty(minecraft, packed);
            }
        }

        LongIterator itNew = newSet.iterator();
        while (itNew.hasNext()) {
            long packed = itNew.nextLong();
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
//        matrix4f.rotateX(0.1f);
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
}
