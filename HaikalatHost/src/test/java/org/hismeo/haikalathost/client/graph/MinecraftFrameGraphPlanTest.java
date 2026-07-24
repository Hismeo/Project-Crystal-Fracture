package org.hismeo.haikalathost.client.graph;

import org.hismeo.haikalathost.client.submission.PassKey;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftFrameGraphPlanTest {
    @Test
    void everyCapturedPassHasExactlyOneScreenCoverageOwner() {
        Map<PassKey, String> coverage = MinecraftFrameGraphPlan.coverageMatrix();

        assertEquals(EnumSet.allOf(PassKey.class), coverage.keySet());
        assertEquals(MinecraftFrameGraphPlan.DEPTH_OPAQUE, coverage.get(PassKey.WORLD_OPAQUE));
        assertEquals(MinecraftFrameGraphPlan.SKY_CLOUD_WEATHER, coverage.get(PassKey.WEATHER));
        assertEquals(MinecraftFrameGraphPlan.UI_TEXT, coverage.get(PassKey.TEXT));
        assertEquals(MinecraftFrameGraphPlan.UI_TEXT, coverage.get(PassKey.UI));
    }

    @Test
    void dependenciesOnlyPointBackwardAndPresentIsLast() {
        List<MinecraftFrameGraphPlan.Node> nodes = MinecraftFrameGraphPlan.nodes();
        Set<String> seen = new HashSet<>();
        for (MinecraftFrameGraphPlan.Node node : nodes) {
            assertTrue(seen.containsAll(node.dependencies()), node.name());
            seen.add(node.name());
        }

        MinecraftFrameGraphPlan.Node last = nodes.get(nodes.size() - 1);
        assertEquals(MinecraftFrameGraphPlan.PRESENT, last.name());
        assertEquals(MinecraftFrameGraphPlan.Target.BACKBUFFER, last.target());
    }

    @Test
    void h5ResourcesHaveExplicitProducerStages() {
        Set<String> names = new HashSet<>();
        for (MinecraftFrameGraphPlan.Node node : MinecraftFrameGraphPlan.nodes()) {
            names.add(node.name());
        }

        assertTrue(names.containsAll(Set.of(
                MinecraftFrameGraphPlan.DEPTH_OPAQUE,
                MinecraftFrameGraphPlan.HIZ_HALF,
                MinecraftFrameGraphPlan.HIZ_QUARTER,
                MinecraftFrameGraphPlan.HIZ_EIGHTH,
                MinecraftFrameGraphPlan.EXPOSURE,
                MinecraftFrameGraphPlan.BLOOM_EXTRACT,
                MinecraftFrameGraphPlan.BLOOM_DOWNSAMPLE,
                MinecraftFrameGraphPlan.BLOOM_UPSAMPLE,
                MinecraftFrameGraphPlan.TONE_MAPPING,
                MinecraftFrameGraphPlan.FXAA,
                MinecraftFrameGraphPlan.UI_TEXT,
                MinecraftFrameGraphPlan.PRESENT)));
    }
}
