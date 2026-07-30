package org.hismeo.haikalathost.api;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HaikalatHostApiLocatorTest {
    @Test
    void remainsStableWhenNoRuntimeProviderIsPresent() {
        HaikalatHostApi api =
                HaikalatHostApiLocator.selectProvider(Collections.emptyIterator());

        assertFalse(api.available());
        assertEquals("api_provider_missing", api.status().reasonCode());
    }
}
