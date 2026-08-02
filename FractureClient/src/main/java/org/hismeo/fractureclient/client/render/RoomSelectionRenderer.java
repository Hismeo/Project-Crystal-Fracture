package org.hismeo.fractureclient.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.hismeo.fractureclient.client.control.RoomSelectionController;
import org.hismeo.fractureclient.client.room.RoomRegion;

/** World-space wire preview for the client-only room selection wand. */
public final class RoomSelectionRenderer {
    private static final int MAX_BOUNDARY_PREVIEW_CELLS = 4_096;

    private RoomSelectionRenderer() {
    }

    public static void render(RenderLevelStageEvent event, Minecraft minecraft) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES
                || minecraft.level == null
                || minecraft.player == null
                || !RoomSelectionController.isActive()) {
            return;
        }

        RoomRegion room = RoomSelectionController.draft();
        if (room == null) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        int rendered = 0;
        LongIterator iterator = room.columnIterator();
        while (iterator.hasNext() && rendered < MAX_BOUNDARY_PREVIEW_CELLS) {
            long packed = iterator.nextLong();
            int x = RoomRegion.unpackX(packed);
            int z = RoomRegion.unpackZ(packed);
            if (room.containsColumn(x - 1, z)
                    && room.containsColumn(x + 1, z)
                    && room.containsColumn(x, z - 1)
                    && room.containsColumn(x, z + 1)) {
                continue;
            }

            AABB column = new AABB(
                    x + 0.02,
                    room.floorY() + 0.02,
                    z + 0.02,
                    x + 0.98,
                    room.ceilingY() + 0.98,
                    z + 0.98
            ).move(-camera.x, -camera.y, -camera.z);
            LevelRenderer.renderLineBox(
                    poseStack,
                    lines,
                    column,
                    0.20F,
                    0.85F,
                    1.0F,
                    0.82F
            );
            rendered++;
        }

        BlockPos anchor = RoomSelectionController.anchor();
        BlockHitResult hit = minecraft.hitResult instanceof BlockHitResult blockHit
                && blockHit.getType() == HitResult.Type.BLOCK
                ? blockHit
                : null;
        if (anchor != null) {
            renderAnchor(poseStack, lines, camera, anchor, room);
            if (hit != null) {
                renderRectanglePreview(
                        poseStack,
                        lines,
                        camera,
                        anchor,
                        hit.getBlockPos().relative(hit.getDirection()),
                        room,
                        minecraft.player.isShiftKeyDown()
                );
            }
        } else if (hit != null) {
            BlockPos target = hit.getBlockPos().relative(hit.getDirection());
            AABB targetBox = new AABB(
                    target.getX(),
                    room.floorY(),
                    target.getZ(),
                    target.getX() + 1.0,
                    room.floorY() + 0.08,
                    target.getZ() + 1.0
            ).move(-camera.x, -camera.y, -camera.z);
            LevelRenderer.renderLineBox(
                    poseStack,
                    lines,
                    targetBox,
                    1.0F,
                    0.86F,
                    0.25F,
                    1.0F
            );
        }

        buffers.endBatch(RenderType.lines());
    }

    private static void renderAnchor(
            PoseStack poseStack,
            VertexConsumer lines,
            Vec3 camera,
            BlockPos anchor,
            RoomRegion room
    ) {
        AABB box = new AABB(
                anchor.getX(),
                room.floorY(),
                anchor.getZ(),
                anchor.getX() + 1.0,
                room.floorY() + 0.15,
                anchor.getZ() + 1.0
        ).move(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(
                poseStack,
                lines,
                box,
                1.0F,
                0.75F,
                0.12F,
                1.0F
        );
    }

    private static void renderRectanglePreview(
            PoseStack poseStack,
            VertexConsumer lines,
            Vec3 camera,
            BlockPos anchor,
            BlockPos target,
            RoomRegion room,
            boolean subtract
    ) {
        int minX = Math.min(anchor.getX(), target.getX());
        int minZ = Math.min(anchor.getZ(), target.getZ());
        int maxX = Math.max(anchor.getX(), target.getX()) + 1;
        int maxZ = Math.max(anchor.getZ(), target.getZ()) + 1;
        AABB box = new AABB(
                minX,
                room.floorY() + 0.01,
                minZ,
                maxX,
                room.ceilingY() + 0.99,
                maxZ
        ).move(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(
                poseStack,
                lines,
                box,
                subtract ? 1.0F : 0.20F,
                subtract ? 0.18F : 1.0F,
                subtract ? 0.18F : 0.35F,
                1.0F
        );
    }
}
