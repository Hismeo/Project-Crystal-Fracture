package org.hismeo.haikalathost.client.submission;

import com.kaleblangley.haikalat.backend.buffer.GlBuffer;
import com.kaleblangley.haikalat.core.command.CommandBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Objects;

import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STREAM_DRAW;

/**
 * Reusable indirect-command storage. Upload and command execution must remain ordered on the render thread.
 */
public final class MinecraftIndirectBatch implements AutoCloseable {
    private final GlBuffer buffer = new GlBuffer(GL_DRAW_INDIRECT_BUFFER, GL_STREAM_DRAW);
    private int drawCount;
    private boolean closed;

    public void upload(List<DrawElementsIndirectCommand> draws) {
        ensureOpen();
        Objects.requireNonNull(draws, "draws");
        ByteBuffer payload = ByteBuffer
                .allocateDirect(Math.multiplyExact(draws.size(), DrawElementsIndirectCommand.BYTES))
                .order(ByteOrder.nativeOrder());
        draws.forEach(draw -> draw.writeTo(payload));
        payload.flip();
        buffer.upload(payload);
        drawCount = draws.size();
    }

    public void record(CommandBuffer commands, int mode, int indexType) {
        ensureOpen();
        if (drawCount == 0) return;
        if (drawCount == 1) {
            MinecraftIndexedCommandEncoder.drawElementsIndirect(
                    commands, buffer, mode, indexType, 0L);
        } else {
            MinecraftIndexedCommandEncoder.multiDrawElementsIndirect(
                    commands,
                    buffer,
                    mode,
                    indexType,
                    0L,
                    drawCount,
                    DrawElementsIndirectCommand.BYTES);
        }
    }

    public int drawCount() {
        return drawCount;
    }

    @Override
    public void close() {
        if (closed) return;
        buffer.close();
        closed = true;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Indirect batch is closed");
    }
}
