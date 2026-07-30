package org.hismeo.haikalathost.api;

import org.hismeo.haikalathost.api.runtime.HostStatus;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Package-private so the public API does not expose implementation holder/provider types.
 */
final class HaikalatHostApiLocator {
    private static final HaikalatHostApi INSTANCE = discover();

    private HaikalatHostApiLocator() {
    }

    static HaikalatHostApi instance() {
        return INSTANCE;
    }

    private static HaikalatHostApi discover() {
        try {
            ServiceLoader<HaikalatHostApi> loader = ServiceLoader.load(
                    HaikalatHostApi.class,
                    HaikalatHostApi.class.getClassLoader());
            return selectProvider(loader.iterator());
        } catch (ServiceConfigurationError | LinkageError | RuntimeException exception) {
            return unavailable(
                    "api_provider_invalid",
                    "HaikalatHost runtime provider discovery failed: "
                            + exception.getClass().getSimpleName());
        }
    }

    static HaikalatHostApi selectProvider(Iterator<HaikalatHostApi> providers) {
        try {
            if (!providers.hasNext()) {
                return unavailable(
                        "api_provider_missing",
                        "No HaikalatHost runtime provider is installed");
            }

            HaikalatHostApi provider = providers.next();
            if (providers.hasNext()) {
                return unavailable(
                        "api_provider_ambiguous",
                        "Multiple HaikalatHost runtime providers are installed");
            }
            return provider;
        } catch (ServiceConfigurationError | LinkageError | RuntimeException exception) {
            return unavailable(
                    "api_provider_invalid",
                    "HaikalatHost runtime provider discovery failed: "
                            + exception.getClass().getSimpleName());
        }
    }

    private static HaikalatHostApi unavailable(String reasonCode, String message) {
        HostStatus status = HostStatus.unavailable(reasonCode, message);
        return () -> status;
    }
}
