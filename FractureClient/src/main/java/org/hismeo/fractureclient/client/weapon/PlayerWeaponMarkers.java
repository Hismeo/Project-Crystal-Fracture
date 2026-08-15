package org.hismeo.fractureclient.client.weapon;

import org.hismeo.crystalfracture.weapon.api.MarkerName;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Stable client presentation API. Callers never depend on slot or GLB Locator names. */
public final class PlayerWeaponMarkers {
    public static final MarkerName MAIN_HAND_GRIP = new MarkerName("main_hand_grip");
    public static final MarkerName TRAIL_START = new MarkerName("trail_start");
    public static final MarkerName TRAIL_END = new MarkerName("trail_end");

    private PlayerWeaponMarkers() {
    }

    public static Optional<Matrix4f> worldTransform(UUID playerId, MarkerName marker) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(marker, "marker");
        Matrix4f result = new Matrix4f();
        return PlayerWeaponExtension.markerWorld(playerId, marker, result)
                ? Optional.of(result)
                : Optional.empty();
    }

    public static Optional<WeaponTrailSegment> trail(UUID playerId) {
        Optional<Matrix4f> start = worldTransform(playerId, TRAIL_START);
        Optional<Matrix4f> end = worldTransform(playerId, TRAIL_END);
        if (start.isEmpty() || end.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new WeaponTrailSegment(
                start.orElseThrow().transformPosition(new Vector3f()),
                end.orElseThrow().transformPosition(new Vector3f())));
    }
}
