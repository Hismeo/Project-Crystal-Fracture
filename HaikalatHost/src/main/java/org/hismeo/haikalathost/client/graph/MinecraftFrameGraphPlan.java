package org.hismeo.haikalathost.client.graph;

import org.hismeo.haikalathost.client.submission.PassKey;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure description of the H4/H5 frame topology.
 *
 * <p>The runtime graph is built from the same names and coverage sets, while this class stays free
 * of OpenGL state so the complete-screen coverage contract can be verified with JUnit.</p>
 */
public final class MinecraftFrameGraphPlan {
    public static final String GPU_CULL_COMPACT = "GpuCullCompact";
    public static final String DEPTH_OPAQUE = "DepthOpaque";
    public static final String HIZ_HALF = "HiZ/Half";
    public static final String HIZ_QUARTER = "HiZ/Quarter";
    public static final String HIZ_EIGHTH = "HiZ/Eighth";
    public static final String WORLD_CUTOUT = "WorldCutout";
    public static final String ENTITIES = "Entities";
    public static final String SKY_CLOUD_WEATHER = "SkyCloudWeather";
    public static final String TRANSLUCENT = "Translucent";
    public static final String PARTICLES = "Particles";
    public static final String OUTLINE_FIRST_PERSON_DEBUG = "OutlineFirstPersonDebug";
    public static final String EXPOSURE = "Exposure";
    public static final String BLOOM_EXTRACT = "Bloom/Extract";
    public static final String BLOOM_DOWNSAMPLE = "Bloom/Downsample";
    public static final String BLOOM_UPSAMPLE = "Bloom/Upsample";
    public static final String TONE_MAPPING = "ToneMapping";
    public static final String FXAA = "FXAA";
    public static final String UI_TEXT = "UiText";
    public static final String PRESENT = "Present";

    public static final String SCENE_COLOR = "sceneColor";
    public static final String SCENE_DEPTH = "sceneDepth";
    public static final String HIZ_HALF_TEXTURE = "hiZHalf";
    public static final String HIZ_QUARTER_TEXTURE = "hiZQuarter";
    public static final String HIZ_EIGHTH_TEXTURE = "hiZEighth";
    public static final String EXPOSURE_TEXTURE = "exposure";
    public static final String BLOOM_HALF = "bloomHalf";
    public static final String BLOOM_QUARTER = "bloomQuarter";
    public static final String BLOOM_COMBINED = "bloomCombined";
    public static final String TONEMAPPED_COLOR = "toneMappedColor";
    public static final String FXAA_COLOR = "fxaaColor";
    public static final String UI_COLOR = "uiColor";

    private static final List<Node> NODES = List.of(
            node(GPU_CULL_COMPACT, Target.COMPUTE, List.of()),
            node(DEPTH_OPAQUE, Target.MANAGED, List.of(GPU_CULL_COMPACT),
                    PassKey.DEPTH_PREPASS, PassKey.WORLD_OPAQUE),
            node(HIZ_HALF, Target.MANAGED, List.of(DEPTH_OPAQUE)),
            node(HIZ_QUARTER, Target.MANAGED, List.of(HIZ_HALF)),
            node(HIZ_EIGHTH, Target.MANAGED, List.of(HIZ_QUARTER)),
            node(WORLD_CUTOUT, Target.SCENE, List.of(HIZ_EIGHTH), PassKey.WORLD_CUTOUT),
            node(ENTITIES, Target.SCENE, List.of(WORLD_CUTOUT),
                    PassKey.ENTITY_OPAQUE, PassKey.ENTITY_CUTOUT),
            node(SKY_CLOUD_WEATHER, Target.SCENE, List.of(ENTITIES),
                    PassKey.SKY, PassKey.CLOUD, PassKey.WEATHER),
            node(TRANSLUCENT, Target.SCENE, List.of(SKY_CLOUD_WEATHER),
                    PassKey.WORLD_TRANSLUCENT, PassKey.ENTITY_TRANSLUCENT),
            node(PARTICLES, Target.SCENE, List.of(TRANSLUCENT), PassKey.PARTICLE),
            node(OUTLINE_FIRST_PERSON_DEBUG, Target.SCENE, List.of(PARTICLES),
                    PassKey.LINE, PassKey.OUTLINE, PassKey.FIRST_PERSON, PassKey.POSTPROCESS),
            node(EXPOSURE, Target.MANAGED, List.of(OUTLINE_FIRST_PERSON_DEBUG)),
            node(BLOOM_EXTRACT, Target.MANAGED, List.of(OUTLINE_FIRST_PERSON_DEBUG)),
            node(BLOOM_DOWNSAMPLE, Target.MANAGED, List.of(BLOOM_EXTRACT)),
            node(BLOOM_UPSAMPLE, Target.MANAGED, List.of(BLOOM_DOWNSAMPLE)),
            node(TONE_MAPPING, Target.MANAGED, List.of(EXPOSURE, BLOOM_UPSAMPLE)),
            node(FXAA, Target.MANAGED, List.of(TONE_MAPPING)),
            node(UI_TEXT, Target.MANAGED, List.of(FXAA), PassKey.TEXT, PassKey.UI),
            node(PRESENT, Target.BACKBUFFER, List.of(UI_TEXT))
    );

    static {
        validate();
    }

    private MinecraftFrameGraphPlan() {
    }

    public static List<Node> nodes() {
        return NODES;
    }

    public static Node node(String name) {
        return NODES.stream()
                .filter(node -> node.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown frame-graph node " + name));
    }

    public static Map<PassKey, String> coverageMatrix() {
        EnumMap<PassKey, String> coverage = new EnumMap<>(PassKey.class);
        for (Node node : NODES) {
            for (PassKey pass : node.coverage()) {
                String previous = coverage.put(pass, node.name());
                if (previous != null) {
                    throw new IllegalStateException(
                            pass + " is covered by both " + previous + " and " + node.name());
                }
            }
        }
        return Map.copyOf(coverage);
    }

    private static Node node(String name, Target target, List<String> dependencies, PassKey... coverage) {
        EnumSet<PassKey> passes = coverage.length == 0
                ? EnumSet.noneOf(PassKey.class)
                : EnumSet.of(coverage[0], coverage);
        return new Node(name, target, dependencies, passes);
    }

    private static void validate() {
        Set<String> seen = new HashSet<>();
        List<String> order = new ArrayList<>(NODES.size());
        for (Node node : NODES) {
            if (!seen.add(node.name())) {
                throw new IllegalStateException("Duplicate frame-graph node " + node.name());
            }
            for (String dependency : node.dependencies()) {
                if (!seen.contains(dependency)) {
                    throw new IllegalStateException(
                            node.name() + " depends on missing or later node " + dependency);
                }
            }
            order.add(node.name());
        }
        if (!coverageMatrix().keySet().equals(EnumSet.allOf(PassKey.class))) {
            EnumSet<PassKey> missing = EnumSet.allOf(PassKey.class);
            missing.removeAll(coverageMatrix().keySet());
            throw new IllegalStateException("Incomplete H4 pass coverage: " + missing);
        }
        if (NODES.get(NODES.size() - 1).target() != Target.BACKBUFFER) {
            throw new IllegalStateException("Present must be the final graph node");
        }
    }

    public enum Target {
        COMPUTE,
        MANAGED,
        SCENE,
        BACKBUFFER
    }

    public record Node(
            String name,
            Target target,
            List<String> dependencies,
            Set<PassKey> coverage
    ) {
        public Node {
            dependencies = List.copyOf(dependencies);
            coverage = Set.copyOf(coverage);
        }
    }
}
