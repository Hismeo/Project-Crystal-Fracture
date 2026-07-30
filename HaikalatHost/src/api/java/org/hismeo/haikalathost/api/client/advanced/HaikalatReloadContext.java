package org.hismeo.haikalathost.api.client.advanced;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Context supplied after Minecraft has accepted a new resource generation.
 *
 * <p>The callback runs on the Minecraft Render Thread with a current OpenGL context. An extension
 * may rebuild or close its own GPU resources here, retain a last-known-good generation, or ignore
 * an unrelated reload. HaikalatHost does not automatically rebuild extension-owned objects.</p>
 */
@OnlyIn(Dist.CLIENT)
public interface HaikalatReloadContext extends HaikalatEngineContext {
}
