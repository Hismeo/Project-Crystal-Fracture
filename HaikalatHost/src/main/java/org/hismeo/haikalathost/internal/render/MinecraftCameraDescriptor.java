package org.hismeo.haikalathost.internal.render;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.minecraft.client.Minecraft;
import com.kaleblangley.haikalat.subsystems.render3d.ExternalCamera;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * Immutable-by-copy camera matrices captured from the active Minecraft world render.
 */
public final class MinecraftCameraDescriptor {
    private final Matrix4f modelViewMatrix;
    private final Matrix4f projectionMatrix;
    private final Vector3d position;
    private final float partialTick;
    private final float deltaSeconds;
    private final float nearPlane;
    private final float farPlane;
    private final long revision;

    MinecraftCameraDescriptor(
            Matrix4f modelViewMatrix,
            Matrix4f projectionMatrix,
            Vector3d position,
            float partialTick,
            float deltaSeconds,
            float nearPlane,
            float farPlane,
            long revision
    ) {
        this.modelViewMatrix = copyFinite(modelViewMatrix, "model-view matrix");
        this.projectionMatrix = copyFinite(projectionMatrix, "projection matrix");
        this.position = new Vector3d(Objects.requireNonNull(position, "position"));
        if (!Double.isFinite(this.position.x)
                || !Double.isFinite(this.position.y)
                || !Double.isFinite(this.position.z)) {
            throw new IllegalArgumentException("camera position must be finite");
        }
        if (!Float.isFinite(partialTick)) {
            throw new IllegalArgumentException("partialTick must be finite");
        }
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0.0F) {
            throw new IllegalArgumentException("deltaSeconds must be finite and non-negative");
        }
        if (!Float.isFinite(nearPlane) || !Float.isFinite(farPlane)
                || nearPlane <= 0.0F || farPlane <= nearPlane) {
            throw new IllegalArgumentException("camera planes must satisfy 0 < near < far");
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        this.partialTick = partialTick;
        this.deltaSeconds = deltaSeconds;
        this.nearPlane = nearPlane;
        this.farPlane = farPlane;
        this.revision = revision;
    }

    public static MinecraftCameraDescriptor capture(
            RenderLevelStageEvent event,
            long revision
    ) {
        Objects.requireNonNull(event, "event");
        var cameraPosition = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float deltaSeconds = Math.max(
                0.0F,
                event.getPartialTick().getGameTimeDeltaTicks() / 20.0F);
        float nearPlane = 0.05F;
        float farPlane = Minecraft.getInstance().gameRenderer.getDepthFar();
        return new MinecraftCameraDescriptor(
                event.getModelViewMatrix(),
                event.getProjectionMatrix(),
                new Vector3d(cameraPosition.x, cameraPosition.y, cameraPosition.z),
                partialTick,
                deltaSeconds,
                nearPlane,
                farPlane,
                revision);
    }

    public Matrix4f modelViewMatrix() {
        return new Matrix4f(modelViewMatrix);
    }

    public Matrix4f projectionMatrix() {
        return new Matrix4f(projectionMatrix);
    }

    public Vector3d position() {
        return new Vector3d(position);
    }

    public float partialTick() {
        return partialTick;
    }

    public float deltaSeconds() {
        return deltaSeconds;
    }

    public float nearPlane() {
        return nearPlane;
    }

    public float farPlane() {
        return farPlane;
    }

    public long revision() {
        return revision;
    }

    /**
     * Converts Minecraft's camera-relative rotation matrix into a world-space view matrix.
     */
    public ExternalCamera toExternalCamera() {
        Matrix4f view = new Matrix4f(modelViewMatrix)
                .translate(
                        (float) -position.x,
                        (float) -position.y,
                        (float) -position.z);
        Matrix4f projection = new Matrix4f(projectionMatrix);
        Matrix4f viewProjection = new Matrix4f(projection).mul(view);
        return new ExternalCamera(
                view,
                projection,
                viewProjection,
                new Vector3f((float) position.x, (float) position.y, (float) position.z),
                partialTick,
                nearPlane,
                farPlane,
                revision);
    }

    private static Matrix4f copyFinite(Matrix4f matrix, String name) {
        Matrix4f copy = new Matrix4f(Objects.requireNonNull(matrix, name));
        if (!copy.isFinite()) {
            throw new IllegalArgumentException("Minecraft camera " + name + " must be finite");
        }
        return copy;
    }
}
