package org.hismeo.fractureclient.client.weapon;

import org.hismeo.crystalfracture.weapon.api.WeaponPartId;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;

import java.util.Comparator;
import java.util.Objects;

/** Stable client-side diagnostic for GLB locator and transform failures. */
public record WeaponVisualProblem(
        String errorCode,
        WeaponSlotId slot,
        WeaponPartId part,
        String nodeName,
        String message
) {
    public static final Comparator<WeaponVisualProblem> STABLE_ORDER = Comparator
            .comparing((WeaponVisualProblem problem) -> problem.slot().value())
            .thenComparing(problem -> problem.part().toString())
            .thenComparing(WeaponVisualProblem::nodeName)
            .thenComparing(WeaponVisualProblem::errorCode)
            .thenComparing(WeaponVisualProblem::message);

    public WeaponVisualProblem {
        Objects.requireNonNull(errorCode, "errorCode");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(nodeName, "nodeName");
        Objects.requireNonNull(message, "message");
    }
}
