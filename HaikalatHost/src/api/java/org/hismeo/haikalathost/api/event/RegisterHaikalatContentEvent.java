package org.hismeo.haikalathost.api.event;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;
import org.hismeo.haikalathost.api.content.HaikalatAssetKind;
import org.hismeo.haikalathost.api.content.HaikalatAssetRegistrar;
import org.hismeo.haikalathost.api.content.HaikalatNamespaceRegistrar;

import java.util.Objects;
import java.util.function.Function;

/**
 * Single client mod-bus entry point for declarative Haikalat content.
 *
 * <p>Listeners must only declare immutable content here. No resource reads or GPU operations are
 * allowed during this event.</p>
 */
public final class RegisterHaikalatContentEvent extends Event implements IModBusEvent {
    private final HaikalatAssetRegistrar scenes;
    private final HaikalatAssetRegistrar models;
    private final HaikalatAssetRegistrar animationLibraries;
    private final HaikalatAssetRegistrar effects;
    private final HaikalatAssetRegistrar ui;
    private final HaikalatNamespaceRegistrar namespaces;

    /**
     * Runtime constructor. Integration mods should subscribe to the event, not instantiate it.
     */
    public RegisterHaikalatContentEvent(
            Function<HaikalatAssetKind, HaikalatAssetRegistrar> registrarFactory,
            HaikalatNamespaceRegistrar namespaceRegistrar
    ) {
        Objects.requireNonNull(registrarFactory, "registrarFactory");
        scenes = requireRegistrar(registrarFactory, HaikalatAssetKind.SCENE);
        models = requireRegistrar(registrarFactory, HaikalatAssetKind.MODEL);
        animationLibraries =
                requireRegistrar(registrarFactory, HaikalatAssetKind.ANIMATION_LIBRARY);
        effects = requireRegistrar(registrarFactory, HaikalatAssetKind.EFFECT);
        ui = requireRegistrar(registrarFactory, HaikalatAssetKind.UI);
        namespaces = Objects.requireNonNull(namespaceRegistrar, "namespaceRegistrar");
    }

    /**
     * Compatibility constructor for runtimes that have not connected explicit namespaces yet.
     */
    public RegisterHaikalatContentEvent(
            Function<HaikalatAssetKind, HaikalatAssetRegistrar> registrarFactory
    ) {
        this(registrarFactory, namespace -> {
        });
    }

    public HaikalatAssetRegistrar scenes() {
        return scenes;
    }

    public HaikalatAssetRegistrar models() {
        return models;
    }

    public HaikalatAssetRegistrar animationLibraries() {
        return animationLibraries;
    }

    public HaikalatAssetRegistrar effects() {
        return effects;
    }

    public HaikalatAssetRegistrar ui() {
        return ui;
    }

    public HaikalatNamespaceRegistrar namespaces() {
        return namespaces;
    }

    private static HaikalatAssetRegistrar requireRegistrar(
            Function<HaikalatAssetKind, HaikalatAssetRegistrar> factory,
            HaikalatAssetKind kind
    ) {
        return Objects.requireNonNull(factory.apply(kind), "registrar for " + kind);
    }
}
