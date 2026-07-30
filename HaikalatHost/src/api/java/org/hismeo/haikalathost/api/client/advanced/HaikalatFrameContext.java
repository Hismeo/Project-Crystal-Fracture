package org.hismeo.haikalathost.api.client.advanced;

import com.kaleblangley.haikalat.core.presentation.PresentationTarget;
import com.kaleblangley.haikalat.subsystems.render3d.ExternalCamera;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Immutable view of one Minecraft world-render frame.
 *
 * <p>The camera and presentation target describe this callback only. In particular, the target
 * contains Minecraft-owned framebuffer and texture ids that may be replaced on resize, resource
 * changes or another mod's render integration. Do not cache the target or its attachments, delete
 * them, resize them, change their storage, or use them after {@code render(...)} returns.</p>
 */
@OnlyIn(Dist.CLIENT)
public interface HaikalatFrameContext extends HaikalatEngineContext {
    /**
     * Exact Host camera snapshot for this world-render invocation.
     */
    ExternalCamera camera();

    /**
     * Borrowed Minecraft presentation target valid for this callback.
     */
    PresentationTarget target();

    /**
     * Sanitized elapsed frame time in seconds.
     */
    float deltaSeconds();

    /**
     * Monotonic Host frame sequence.
     */
    long frameIndex();
}
