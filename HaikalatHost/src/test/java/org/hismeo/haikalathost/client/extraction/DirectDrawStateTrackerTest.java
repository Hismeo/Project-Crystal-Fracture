package org.hismeo.haikalathost.client.extraction;

import org.hismeo.haikalathost.client.submission.PassKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DirectDrawStateTrackerTest {
    private final DirectPassOverrideStack overrides = new DirectPassOverrideStack(8);

    @AfterEach
    void clearState() {
        overrides.clear();
    }

    @Test
    void weatherOverrideIsNestedAndRestoredWithoutAllocation() {
        assertNull(overrides.current());

        overrides.push(PassKey.WEATHER);
        overrides.push(PassKey.PARTICLE);
        assertEquals(PassKey.PARTICLE, overrides.current());

        overrides.pop(PassKey.PARTICLE);
        assertEquals(PassKey.WEATHER, overrides.current());
        overrides.pop(PassKey.WEATHER);
        assertNull(overrides.current());
    }

    @Test
    void mismatchedOverrideFailsClosedAndClearsTheStack() {
        overrides.push(PassKey.WEATHER);

        assertThrows(
                IllegalStateException.class,
                () -> overrides.pop(PassKey.PARTICLE));
        assertNull(overrides.current());
    }
}
