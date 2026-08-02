package org.hismeo.fractureclient.client.render.octopath;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TintedGlassBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Converts Minecraft's broad motion-blocking height-map result into the deliberately simpler
 * opaque proxy used by the shadow maps.
 *
 * <p>Glass blocks and panes have collision/motion shapes, so {@code MOTION_BLOCKING} reports
 * them as a height even though an art-directed sun or lamp should travel through them. A depth
 * map cannot express partial transmission, therefore this first pass treats ordinary glass as
 * fully transmitting and still keeps tinted glass opaque.</p>
 */
final class OctopathShadowOcclusion {
    static final int NO_OPAQUE_SURFACE = Integer.MIN_VALUE;

    private static final int MAX_TRANSMITTING_STACK_DEPTH = 32;

    private OctopathShadowOcclusion() {
    }

    /**
     * Finds the upper surface of the first opaque block below a motion-blocking height-map hit.
     * A bounded descent is enough for windows, panes and railings without turning sparse glass
     * towers into an unbounded per-column world scan.
     */
    static int opaqueSurfaceTopY(
            ClientLevel level,
            BlockPos.MutableBlockPos probe,
            int x,
            int z,
            int motionBlockingTopY
    ) {
        int lowerBound = Math.max(
                level.getMinBuildHeight(),
                motionBlockingTopY - MAX_TRANSMITTING_STACK_DEPTH);
        for (int y = motionBlockingTopY - 1; y >= lowerBound; y--) {
            probe.set(x, y, z);
            BlockState state = level.getBlockState(probe);
            if (state.isAir() || !level.getFluidState(probe).isEmpty() || transmitsShadowLight(state)) {
                continue;
            }
            return y + 1;
        }
        return NO_OPAQUE_SURFACE;
    }

    private static boolean transmitsShadowLight(BlockState state) {
        Block block = state.getBlock();
        // TransparentBlock covers regular and stained glass. Tinted glass intentionally blocks
        // skylight in vanilla, so retain it as a solid occluder. Glass panes use their own block
        // class and must be included explicitly; iron bars remain opaque for shadow purposes.
        return (block instanceof TransparentBlock && !(block instanceof TintedGlassBlock))
                || block == Blocks.GLASS_PANE
                || block instanceof StainedGlassPaneBlock;
    }
}
