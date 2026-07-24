package org.hismeo.haikalathost.client.submission;

import com.kaleblangley.haikalat.backend.buffer.GlBuffer;
import com.kaleblangley.haikalat.core.command.CommandBuffer;

import java.util.Objects;

import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL32.glDrawElementsBaseVertex;
import static org.lwjgl.opengl.GL40.glDrawElementsIndirect;
import static org.lwjgl.opengl.GL43.glMultiDrawElementsIndirect;

/**
 * Minecraft-specific indexed command extensions recorded inside a Haikalat command stream.
 */
public final class MinecraftIndexedCommandEncoder {
    private MinecraftIndexedCommandEncoder() {
    }

    public static CommandBuffer drawElementsBaseVertex(
            CommandBuffer commands,
            int mode,
            int indexCount,
            int indexType,
            long indexOffsetBytes,
            int baseVertex) {
        Objects.requireNonNull(commands, "commands");
        if (indexCount < 0 || indexOffsetBytes < 0L) {
            throw new IllegalArgumentException("Indexed draw count and offset must not be negative");
        }
        return commands.custom(() ->
                glDrawElementsBaseVertex(mode, indexCount, indexType, indexOffsetBytes, baseVertex));
    }

    public static CommandBuffer drawElementsIndirect(
            CommandBuffer commands,
            GlBuffer indirectBuffer,
            int mode,
            int indexType,
            long commandOffsetBytes) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(indirectBuffer, "indirectBuffer");
        if (commandOffsetBytes < 0L) {
            throw new IllegalArgumentException("Indirect command offset must not be negative");
        }
        return commands.custom(() -> {
            indirectBuffer.bind();
            try {
                glDrawElementsIndirect(mode, indexType, commandOffsetBytes);
            } finally {
                glBindBuffer(GL_DRAW_INDIRECT_BUFFER, 0);
            }
        });
    }

    public static CommandBuffer multiDrawElementsIndirect(
            CommandBuffer commands,
            GlBuffer indirectBuffer,
            int mode,
            int indexType,
            long commandOffsetBytes,
            int drawCount,
            int strideBytes) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(indirectBuffer, "indirectBuffer");
        if (commandOffsetBytes < 0L || drawCount < 0) {
            throw new IllegalArgumentException("MDI offset and draw count must not be negative");
        }
        if (strideBytes != 0
                && (strideBytes < DrawElementsIndirectCommand.BYTES
                || strideBytes % Integer.BYTES != 0)) {
            throw new IllegalArgumentException("MDI stride must be zero or an aligned command-sized value");
        }
        return commands.custom(() -> {
            indirectBuffer.bind();
            try {
                glMultiDrawElementsIndirect(
                        mode, indexType, commandOffsetBytes, drawCount, strideBytes);
            } finally {
                glBindBuffer(GL_DRAW_INDIRECT_BUFFER, 0);
            }
        });
    }
}
