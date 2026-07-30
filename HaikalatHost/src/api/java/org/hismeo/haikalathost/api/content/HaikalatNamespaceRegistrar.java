package org.hismeo.haikalathost.api.content;

/**
 * Declares a resource namespace that registered content may reference transitively.
 */
@FunctionalInterface
public interface HaikalatNamespaceRegistrar {
    void register(String namespace);
}
