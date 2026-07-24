package org.hismeo.haikalathost.client.backend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftGlAuditTest {
    @Test
    void strictFrameRejectsMinecraftGlExceptInsideNamedAllowance() {
        MinecraftGlAudit audit = new MinecraftGlAudit(ValidationMode.STRICT);

        try (MinecraftGlAudit.Scope frame = audit.beginHaikalatFrame()) {
            assertTrue(audit.frameActive());
            assertThrows(IllegalStateException.class,
                    () -> audit.checkMinecraftGl("BufferUploader.drawWithShader"));
            try (MinecraftGlAudit.Scope ignored = audit.allowExternalGl("screenshot readback")) {
                assertDoesNotThrow(() -> audit.checkMinecraftGl("screenshot"));
            }
        }
        assertFalse(audit.frameActive());
    }

    @Test
    void detectsFrameAndAllowanceLifecycleErrors() {
        MinecraftGlAudit audit = new MinecraftGlAudit(ValidationMode.STRICT);
        MinecraftGlAudit.Scope frame = audit.beginHaikalatFrame();
        MinecraftGlAudit.Scope allowance = audit.allowExternalGl("resize");

        assertThrows(IllegalStateException.class, frame::close);
        allowance.close();
        frame.close();
    }
}
