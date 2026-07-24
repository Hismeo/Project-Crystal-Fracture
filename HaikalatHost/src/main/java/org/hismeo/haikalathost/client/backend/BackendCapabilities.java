package org.hismeo.haikalathost.client.backend;

import java.util.ArrayList;
import java.util.List;

public record BackendCapabilities(
        boolean openGl46,
        boolean computeShaders,
        boolean shaderStorageBuffers,
        boolean persistentBufferStorage,
        boolean multiDrawIndirect,
        boolean shaderDrawParameters,
        boolean indirectDrawCount,
        boolean khrDebug,
        boolean bindlessTexture
) {
    public List<String> missingRequirements(FullTakeoverConfiguration configuration) {
        List<String> missing = new ArrayList<>();
        require(openGl46, "OpenGL 4.6 core profile", missing);
        require(computeShaders, "compute shaders", missing);
        require(shaderStorageBuffers, "shader storage buffers", missing);
        require(persistentBufferStorage, "persistent buffer storage", missing);
        require(multiDrawIndirect, "multi-draw indirect", missing);
        require(shaderDrawParameters, "shader draw parameters", missing);
        require(indirectDrawCount, "indirect draw count", missing);
        require(khrDebug, "KHR_debug", missing);
        if (configuration.bindless()) require(bindlessTexture, "ARB_bindless_texture", missing);
        return List.copyOf(missing);
    }

    public String report(FullTakeoverConfiguration configuration) {
        List<String> missing = missingRequirements(configuration);
        return missing.isEmpty()
                ? "OpenGL takeover requirements satisfied"
                : "Missing OpenGL takeover requirements: " + String.join(", ", missing);
    }

    private static void require(boolean available, String name, List<String> missing) {
        if (!available) missing.add(name);
    }
}
