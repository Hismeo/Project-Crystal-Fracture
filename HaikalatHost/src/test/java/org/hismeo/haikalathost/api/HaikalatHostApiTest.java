package org.hismeo.haikalathost.api;

import org.junit.jupiter.api.Test;
import org.hismeo.haikalathost.internal.api.MinecraftHaikalatHostApi;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class HaikalatHostApiTest {
    @Test
    void discoversTheMinecraftRuntimeProvider() {
        HaikalatHostApi api = HaikalatHostApi.get();

        assertInstanceOf(MinecraftHaikalatHostApi.class, api);
    }
}
