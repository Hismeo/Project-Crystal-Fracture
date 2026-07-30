package org.hismeo.haikalathost.internal.render;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftCameraDescriptorTest {
    @Test
    void createsExactWorldSpaceExternalCameraSnapshot() {
        Matrix4f rotation = new Matrix4f().rotateY(0.35F);
        Matrix4f projection = new Matrix4f().perspective(
                (float) Math.toRadians(70.0),
                16.0F / 9.0F,
                0.05F,
                512.0F);
        Vector3d position = new Vector3d(12.5, 64.0, -7.25);
        MinecraftCameraDescriptor descriptor = new MinecraftCameraDescriptor(
                rotation,
                projection,
                position,
                0.4F,
                0.01F,
                0.05F,
                512.0F,
                9L);

        var camera = descriptor.toExternalCamera();
        Matrix4f expectedView = new Matrix4f(rotation)
                .translate(-12.5F, -64.0F, 7.25F);

        assertTrue(camera.view().equals(expectedView, 0.00001F));
        assertTrue(camera.projection().equals(projection, 0.00001F));
        assertTrue(camera.viewProjection().equals(
                new Matrix4f(projection).mul(expectedView),
                0.00001F));
        assertTrue(camera.position().equals(new Vector3f(12.5F, 64.0F, -7.25F), 0.0F));
        assertEquals(0.4F, camera.partialTick());
        assertEquals(0.05F, camera.nearPlane());
        assertEquals(512.0F, camera.farPlane());
        assertEquals(9L, camera.revision());
    }

    @Test
    void returnsDefensiveMatrixCopies() {
        MinecraftCameraDescriptor descriptor = new MinecraftCameraDescriptor(
                new Matrix4f(),
                new Matrix4f(),
                new Vector3d(),
                0.0F,
                0.0F,
                0.05F,
                100.0F,
                1L);

        var camera = descriptor.toExternalCamera();
        camera.view().translate(100.0F, 0.0F, 0.0F);

        assertTrue(camera.view().equals(new Matrix4f(), 0.0F));
    }

    @Test
    void rejectsNonFiniteMinecraftMatricesAtCaptureBoundary() {
        Matrix4f invalidProjection = new Matrix4f();
        invalidProjection.m00(Float.NaN);

        assertThrows(
                IllegalArgumentException.class,
                () -> new MinecraftCameraDescriptor(
                        new Matrix4f(),
                        invalidProjection,
                        new Vector3d(),
                        0.0F,
                        0.0F,
                        0.05F,
                        100.0F,
                        1L));
    }
}
