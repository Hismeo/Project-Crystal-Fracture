package org.hismeo.haikalathost.internal.api;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.hismeo.haikalathost.api.HaikalatHostApi;
import org.hismeo.haikalathost.api.content.HaikalatAssets;
import org.hismeo.haikalathost.api.runtime.HostStatus;

/**
 * Distribution-safe service provider.
 *
 * <p>This class deliberately has no symbolic reference to a Minecraft client class or the client
 * runtime. The client bridge is loaded by name only when NeoForge reports the client
 * distribution, so discovering this provider cannot crash a dedicated server.</p>
 */
public final class MinecraftHaikalatHostApi implements HaikalatHostApi {
    private static final String CLIENT_BRIDGE_CLASS =
            "org.hismeo.haikalathost.internal.api.client.ClientHaikalatHostApiBridge";

    private final HaikalatHostApi delegate = createDelegate();

    @Override
    public HostStatus status() {
        try {
            return delegate.status();
        } catch (LinkageError | RuntimeException exception) {
            return HostStatus.unavailable(
                    "api_provider_failure",
                    "HaikalatHost runtime provider failed: "
                            + exception.getClass().getSimpleName());
        }
    }

    @Override
    public HaikalatAssets assets() {
        try {
            return delegate.assets();
        } catch (LinkageError | RuntimeException exception) {
            return HaikalatAssets.empty();
        }
    }

    private static HaikalatHostApi createDelegate() {
        try {
            if (FMLEnvironment.dist != Dist.CLIENT) {
                return unavailable(
                        "client_runtime_unavailable",
                        "HaikalatHost runtime is only available on the client distribution");
            }

            Class<?> bridgeClass = Class.forName(
                    CLIENT_BRIDGE_CLASS,
                    true,
                    MinecraftHaikalatHostApi.class.getClassLoader());
            Object bridge = bridgeClass.getDeclaredConstructor().newInstance();
            return HaikalatHostApi.class.cast(bridge);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return unavailable(
                    "client_bridge_unavailable",
                    "HaikalatHost client bridge could not be loaded: "
                            + exception.getClass().getSimpleName());
        }
    }

    private static HaikalatHostApi unavailable(String reasonCode, String message) {
        HostStatus status = HostStatus.unavailable(reasonCode, message);
        return () -> status;
    }
}
