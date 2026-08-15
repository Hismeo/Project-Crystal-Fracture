package org.hismeo.fractureclient.client.weapon;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystalfracture.weapon.api.ConnectName;
import org.hismeo.crystalfracture.weapon.api.MarkerName;
import org.hismeo.crystalfracture.weapon.api.WeaponAssembly;
import org.hismeo.crystalfracture.weapon.api.WeaponPartId;
import org.hismeo.crystalfracture.weapon.api.WeaponPartTypeId;
import org.hismeo.crystalfracture.weapon.api.WeaponSchemaId;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;
import org.hismeo.crystalfracture.weapon.definition.WeaponConnectEndpoint;
import org.hismeo.crystalfracture.weapon.definition.WeaponConnectionDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponMarkerExport;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartTypeDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponSchemaDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponSlotDefinition;
import org.hismeo.crystalfracture.weapon.internal.compile.WeaponAssemblyCompiler;
import org.hismeo.crystalfracture.weapon.internal.registry.WeaponDefinitionRegistry;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponVisualAssemblerTest {
    private static final WeaponSlotId ROOT = new WeaponSlotId("root");
    private static final WeaponSlotId MIDDLE = new WeaponSlotId("middle");
    private static final WeaponSlotId TIP = new WeaponSlotId("tip");
    private static final WeaponPartId ROOT_PART = WeaponPartId.parse("test:root_part");
    private static final WeaponPartId MIDDLE_PART = WeaponPartId.parse("test:middle_part");
    private static final WeaponPartId TIP_PART = WeaponPartId.parse("test:tip_part");

    @Test
    void assemblesParentFirstPositionRotationAndExportedMarkers() {
        var logical = logicalAssembly();
        Matrix4f rootConnect = new Matrix4f()
                .translate(2.0F, 0.0F, 0.0F)
                .rotateZ((float) (Math.PI * 0.5));
        Map<WeaponPartId, WeaponPartVisualNodes> visuals = Map.of(
                ROOT_PART, nodes(ROOT_PART, Map.of(
                        "root_to_middle", rootConnect,
                        "root_grip", new Matrix4f().translate(0.0F, 0.5F, 0.0F))),
                MIDDLE_PART, nodes(MIDDLE_PART, Map.of(
                        "middle_to_root", new Matrix4f().translate(0.0F, 1.0F, 0.0F),
                        "middle_to_tip", new Matrix4f().translate(0.0F, 3.0F, 0.0F))),
                TIP_PART, nodes(TIP_PART, Map.of(
                        "tip_to_middle", new Matrix4f().translate(0.0F, 1.0F, 0.0F),
                        "tip_marker", new Matrix4f().translate(0.0F, 4.0F, 0.0F))));

        var result = new WeaponVisualAssembler().compile(logical, visuals);
        assertTrue(result.assembly().isPresent(), () -> result.problems().toString());
        WeaponVisualAssembly visual = result.assembly().orElseThrow();
        assertMatrix(new Matrix4f(), visual.partTransform(ROOT));

        Matrix4f expectedMiddle = new Matrix4f(rootConnect)
                .mul(new Matrix4f().translate(0.0F, -1.0F, 0.0F));
        assertMatrix(expectedMiddle, visual.partTransform(MIDDLE));
        Matrix4f expectedTip = new Matrix4f(expectedMiddle)
                .translate(0.0F, 3.0F, 0.0F)
                .translate(0.0F, -1.0F, 0.0F);
        assertMatrix(expectedTip, visual.partTransform(TIP));
        Matrix4f expectedMarker = new Matrix4f(expectedTip).translate(0.0F, 4.0F, 0.0F);
        assertMatrix(expectedMarker,
                visual.markerTransform(new MarkerName("tip_export")).orElseThrow());
    }

    @Test
    void diagnosesMissingNodesWithoutProducingPartialAssembly() {
        var logical = logicalAssembly();
        Map<WeaponPartId, WeaponPartVisualNodes> visuals = Map.of(
                ROOT_PART, nodes(ROOT_PART, Map.of("root_grip", new Matrix4f())),
                MIDDLE_PART, nodes(MIDDLE_PART, Map.of(
                        "middle_to_root", new Matrix4f(),
                        "middle_to_tip", new Matrix4f())),
                TIP_PART, nodes(TIP_PART, Map.of(
                        "tip_to_middle", new Matrix4f(),
                        "tip_marker", new Matrix4f())));

        var result = new WeaponVisualAssembler().compile(logical, visuals);
        assertTrue(result.assembly().isEmpty());
        assertTrue(result.problems().stream().anyMatch(problem ->
                problem.errorCode().equals("missing_visual_node")
                        && problem.nodeName().equals("root_to_middle")));
    }

    @Test
    void diagnosesNonInvertibleChildConnect() {
        var logical = logicalAssembly();
        Map<WeaponPartId, WeaponPartVisualNodes> visuals = Map.of(
                ROOT_PART, nodes(ROOT_PART, Map.of(
                        "root_to_middle", new Matrix4f(),
                        "root_grip", new Matrix4f())),
                MIDDLE_PART, nodes(MIDDLE_PART, Map.of(
                        "middle_to_root", new Matrix4f().scale(0.0F),
                        "middle_to_tip", new Matrix4f())),
                TIP_PART, nodes(TIP_PART, Map.of(
                        "tip_to_middle", new Matrix4f(),
                        "tip_marker", new Matrix4f())));

        var result = new WeaponVisualAssembler().compile(logical, visuals);
        assertTrue(result.assembly().isEmpty());
        assertEquals("non_invertible_connect_transform", result.problems().getFirst().errorCode());
    }

    @Test
    void returnedMatricesCannotMutateCompiledVisual() {
        var logical = logicalAssembly();
        Map<WeaponPartId, WeaponPartVisualNodes> visuals = identityVisuals();
        WeaponVisualAssembly result = new WeaponVisualAssembler().compile(logical, visuals)
                .assembly().orElseThrow();
        new Matrix4f(result.partTransform(ROOT)).translate(99.0F, 0.0F, 0.0F);
        Vector3f translation = result.partTransform(ROOT).getTranslation(new Vector3f());
        assertEquals(0.0F, translation.x, 1.0E-6F);
    }

    private static Map<WeaponPartId, WeaponPartVisualNodes> identityVisuals() {
        return Map.of(
                ROOT_PART, nodes(ROOT_PART, Map.of(
                        "root_to_middle", new Matrix4f(), "root_grip", new Matrix4f())),
                MIDDLE_PART, nodes(MIDDLE_PART, Map.of(
                        "middle_to_root", new Matrix4f(), "middle_to_tip", new Matrix4f())),
                TIP_PART, nodes(TIP_PART, Map.of(
                        "tip_to_middle", new Matrix4f(), "tip_marker", new Matrix4f())));
    }

    private static WeaponPartVisualNodes nodes(
            WeaponPartId part,
            Map<String, ? extends Matrix4fc> matrices
    ) {
        return new WeaponPartVisualNodes(
                part,
                ResourceLocation.fromNamespaceAndPath("test", part.value().getPath() + ".glb"),
                matrices,
                Set.of());
    }

    private static org.hismeo.crystalfracture.weapon.api.CompiledWeaponAssembly logicalAssembly() {
        ConnectName rootToMiddle = new ConnectName("to_middle");
        ConnectName middleToRoot = new ConnectName("to_root");
        ConnectName middleToTip = new ConnectName("to_tip");
        ConnectName tipToMiddle = new ConnectName("to_middle");
        MarkerName grip = new MarkerName("grip");
        MarkerName tipMarker = new MarkerName("tip");
        WeaponPartTypeId rootType = WeaponPartTypeId.parse("test:root");
        WeaponPartTypeId middleType = WeaponPartTypeId.parse("test:middle");
        WeaponPartTypeId tipType = WeaponPartTypeId.parse("test:tip");

        var types = List.of(
                new WeaponPartTypeDefinition(rootType, Set.of(rootToMiddle), Set.of(grip)),
                new WeaponPartTypeDefinition(
                        middleType, Set.of(middleToRoot, middleToTip), Set.of()),
                new WeaponPartTypeDefinition(tipType, Set.of(tipToMiddle), Set.of(tipMarker)));
        var parts = List.of(
                new WeaponPartDefinition(
                        ROOT_PART, rootType, model("root"),
                        Map.of(rootToMiddle, "root_to_middle"),
                        Map.of(grip, "root_grip")),
                new WeaponPartDefinition(
                        MIDDLE_PART, middleType, model("middle"),
                        Map.of(middleToRoot, "middle_to_root", middleToTip, "middle_to_tip"),
                        Map.of()),
                new WeaponPartDefinition(
                        TIP_PART, tipType, model("tip"),
                        Map.of(tipToMiddle, "tip_to_middle"),
                        Map.of(tipMarker, "tip_marker")));
        WeaponSchemaId schemaId = WeaponSchemaId.parse("test:weapon");
        var schema = new WeaponSchemaDefinition(
                schemaId,
                1,
                ROOT,
                Map.of(
                        ROOT, new WeaponSlotDefinition(rootType),
                        MIDDLE, new WeaponSlotDefinition(middleType),
                        TIP, new WeaponSlotDefinition(tipType)),
                List.of(
                        new WeaponConnectionDefinition(
                                new WeaponConnectEndpoint(ROOT, rootToMiddle),
                                new WeaponConnectEndpoint(MIDDLE, middleToRoot)),
                        new WeaponConnectionDefinition(
                                new WeaponConnectEndpoint(MIDDLE, middleToTip),
                                new WeaponConnectEndpoint(TIP, tipToMiddle))),
                Map.of(
                        new MarkerName("grip_export"), new WeaponMarkerExport(ROOT, grip),
                        new MarkerName("tip_export"), new WeaponMarkerExport(TIP, tipMarker)));
        var registry = new WeaponDefinitionRegistry().build(types, parts, List.of(schema))
                .snapshot().orElseThrow();
        return new WeaponAssemblyCompiler().compile(
                new WeaponAssembly(schemaId, Map.of(
                        ROOT, ROOT_PART, MIDDLE, MIDDLE_PART, TIP, TIP_PART)),
                registry).assembly().orElseThrow();
    }

    private static ResourceLocation model(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path + ".glb");
    }

    private static void assertMatrix(Matrix4fc expected, Matrix4fc actual) {
        assertTrue(new Matrix4f(expected).equals(actual, 1.0E-5F),
                () -> "expected " + expected + " but got " + actual);
    }
}
