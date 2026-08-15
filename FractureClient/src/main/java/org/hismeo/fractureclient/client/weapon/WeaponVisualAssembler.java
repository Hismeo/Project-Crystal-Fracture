package org.hismeo.fractureclient.client.weapon;

import org.hismeo.crystalfracture.weapon.api.CompiledWeaponAssembly;
import org.hismeo.crystalfracture.weapon.api.CompiledWeaponConnection;
import org.hismeo.crystalfracture.weapon.api.WeaponPartId;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartDefinition;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Applies the frozen parent-first assembly transform contract to GLB node transforms. */
public final class WeaponVisualAssembler {
    private static final float MIN_ABSOLUTE_DETERMINANT = 1.0E-8F;

    public WeaponVisualCompileResult compile(
            CompiledWeaponAssembly assembly,
            Map<WeaponPartId, WeaponPartVisualNodes> visuals
    ) {
        List<WeaponVisualProblem> problems = new ArrayList<>();
        validateLocators(assembly, visuals, problems);
        if (!problems.isEmpty()) {
            return new WeaponVisualCompileResult(Optional.empty(), problems);
        }

        Map<WeaponSlotId, Matrix4f> partTransforms = new TreeMap<>();
        partTransforms.put(assembly.root(), new Matrix4f());
        for (CompiledWeaponConnection connection : assembly.parentFirstConnections()) {
            WeaponSlotId parentSlot = connection.parent().slot();
            WeaponSlotId childSlot = connection.child().slot();
            WeaponPartDefinition parentPart = assembly.parts().get(parentSlot);
            WeaponPartDefinition childPart = assembly.parts().get(childSlot);
            Matrix4fc parentConnect = visuals.get(parentPart.id())
                    .node(connection.parentNode()).orElseThrow();
            Matrix4fc childConnect = visuals.get(childPart.id())
                    .node(connection.childNode()).orElseThrow();
            float determinant = childConnect.determinant();
            if (!Float.isFinite(determinant)
                    || Math.abs(determinant) <= MIN_ABSOLUTE_DETERMINANT
                    || !finite(childConnect)) {
                problems.add(problem(
                        "non_invertible_connect_transform",
                        childSlot,
                        childPart,
                        connection.childNode(),
                        "child Connect transform must be finite and invertible"));
                continue;
            }
            Matrix4f parentWorld = partTransforms.get(parentSlot);
            if (parentWorld == null) {
                continue;
            }
            Matrix4f childWorld = new Matrix4f(parentWorld)
                    .mul(parentConnect)
                    .mul(new Matrix4f(childConnect).invert());
            if (!finite(childWorld)) {
                problems.add(problem(
                        "non_finite_part_transform",
                        childSlot,
                        childPart,
                        connection.childNode(),
                        "assembled Part transform is not finite"));
                continue;
            }
            partTransforms.put(childSlot, childWorld);
        }
        if (!problems.isEmpty()) {
            return new WeaponVisualCompileResult(Optional.empty(), problems);
        }

        Map<org.hismeo.crystalfracture.weapon.api.MarkerName, Matrix4f> markerTransforms =
                new TreeMap<>();
        assembly.markers().forEach((name, marker) -> {
            Matrix4fc local = visuals.get(marker.part()).node(marker.nodeName()).orElseThrow();
            markerTransforms.put(name,
                    new Matrix4f(partTransforms.get(marker.slot())).mul(local));
        });
        return new WeaponVisualCompileResult(
                Optional.of(new WeaponVisualAssembly(assembly, partTransforms, markerTransforms)),
                List.of());
    }

    private static void validateLocators(
            CompiledWeaponAssembly assembly,
            Map<WeaponPartId, WeaponPartVisualNodes> visuals,
            List<WeaponVisualProblem> problems
    ) {
        assembly.parts().forEach((slot, part) -> {
            WeaponPartVisualNodes visual = visuals.get(part.id());
            if (visual == null) {
                problems.add(problem(
                        "missing_part_visual", slot, part, "",
                        "no GLB visual was loaded for Part"));
                return;
            }
            part.connects().forEach((semanticName, nodeName) ->
                    validateNode(slot, part, visual, nodeName, "Connect " + semanticName, problems));
            part.markers().forEach((semanticName, nodeName) ->
                    validateNode(slot, part, visual, nodeName, "Marker " + semanticName, problems));
        });
    }

    private static void validateNode(
            WeaponSlotId slot,
            WeaponPartDefinition part,
            WeaponPartVisualNodes visual,
            String nodeName,
            String semantic,
            List<WeaponVisualProblem> problems
    ) {
        if (visual.ambiguous(nodeName)) {
            problems.add(problem(
                    "ambiguous_visual_node", slot, part, nodeName,
                    semantic + " resolves to more than one GLB Node"));
            return;
        }
        Optional<Matrix4fc> transform = visual.node(nodeName);
        if (transform.isEmpty()) {
            problems.add(problem(
                    "missing_visual_node", slot, part, nodeName,
                    semantic + " does not resolve to a GLB Node"));
        } else if (!finite(transform.orElseThrow())) {
            problems.add(problem(
                    "non_finite_node_transform", slot, part, nodeName,
                    semantic + " has a non-finite GLB Node transform"));
        }
    }

    private static WeaponVisualProblem problem(
            String code,
            WeaponSlotId slot,
            WeaponPartDefinition part,
            String node,
            String message
    ) {
        return new WeaponVisualProblem(code, slot, part.id(), node, message);
    }

    private static boolean finite(Matrix4fc matrix) {
        return Float.isFinite(matrix.m00()) && Float.isFinite(matrix.m01())
                && Float.isFinite(matrix.m02()) && Float.isFinite(matrix.m03())
                && Float.isFinite(matrix.m10()) && Float.isFinite(matrix.m11())
                && Float.isFinite(matrix.m12()) && Float.isFinite(matrix.m13())
                && Float.isFinite(matrix.m20()) && Float.isFinite(matrix.m21())
                && Float.isFinite(matrix.m22()) && Float.isFinite(matrix.m23())
                && Float.isFinite(matrix.m30()) && Float.isFinite(matrix.m31())
                && Float.isFinite(matrix.m32()) && Float.isFinite(matrix.m33());
    }
}
