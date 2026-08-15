package org.hismeo.fractureclient.client.weapon;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponPreviewScreenTest {
    @Test
    void autoRotationDragZoomAndResetAreDeterministic() {
        WeaponPreviewScreen screen = new WeaponPreviewScreen();
        float initialYaw = screen.yawRadians();
        screen.advance(1.0F);
        assertTrue(screen.yawRadians() > initialYaw);

        assertTrue(screen.mouseDragged(
                100.0, 100.0, GLFW.GLFW_MOUSE_BUTTON_LEFT, 12.0, -4.0));
        float draggedYaw = screen.yawRadians();
        screen.advance(1.0F);
        assertEquals(draggedYaw, screen.yawRadians(), 1.0E-6F);

        assertTrue(screen.mouseScrolled(100.0, 100.0, 0.0, 2.0));
        assertTrue(screen.zoom() > 1.0F);
        assertTrue(screen.keyPressed(GLFW.GLFW_KEY_R, 0, 0));
        assertEquals(1.0F, screen.zoom(), 1.0E-6F);
        assertFalse(screen.isPauseScreen());
    }
}
