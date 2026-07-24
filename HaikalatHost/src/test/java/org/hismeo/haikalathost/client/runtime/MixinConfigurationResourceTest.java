package org.hismeo.haikalathost.client.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixinConfigurationResourceTest {
    @Test
    void startupPluginGuardsExperimentalRenderMixins() throws IOException {
        try (InputStream resource = getClass().getClassLoader()
                .getResourceAsStream("mixins.haikalat_host.json")) {
            assertNotNull(resource, "mixin configuration must be packaged");
            String json = new String(resource.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(json.contains("\"plugin\": \"org.hismeo.haikalathost.client.intercept."
                    + "HaikalatHostMixinPlugin\""));
            assertTrue(json.contains("\"RenderTypeMixin\""));
            assertTrue(json.contains("\"LevelRendererMixin\""));
            assertTrue(json.contains("\"FullTakeoverBufferSourceMixin\""));
            assertTrue(json.contains("\"FullTakeoverBufferUploaderAuditMixin\""));
            assertTrue(json.contains("\"ModelPartMixin\""));
            assertTrue(json.contains("\"ItemRendererMixin\""));
            assertTrue(json.contains("\"FullTakeoverRenderSystemStateMixin\""));
        }
    }
}
