package org.hismeo.haikalathost.client.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuDrivenFrameGraphPlanTest {
    @Test
    void cullCompactRunsBeforeEveryRasterPass() {
        MinecraftFrameGraphPlan.Node first = MinecraftFrameGraphPlan.nodes().getFirst();
        assertEquals(MinecraftFrameGraphPlan.GPU_CULL_COMPACT, first.name());
        assertEquals(MinecraftFrameGraphPlan.Target.COMPUTE, first.target());

        MinecraftFrameGraphPlan.Node depth =
                MinecraftFrameGraphPlan.node(MinecraftFrameGraphPlan.DEPTH_OPAQUE);
        assertTrue(depth.dependencies().contains(MinecraftFrameGraphPlan.GPU_CULL_COMPACT));
    }
}
