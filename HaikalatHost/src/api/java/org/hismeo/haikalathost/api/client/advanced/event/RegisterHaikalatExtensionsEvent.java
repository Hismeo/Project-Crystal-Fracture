package org.hismeo.haikalathost.api.client.advanced.event;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;
import org.hismeo.haikalathost.api.client.advanced.HaikalatRenderExtension;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Client mod-bus event for registering advanced Haikalat render extensions.
 *
 * <p>Registration is declaration-only: do not read resources or create GPU objects in this event.
 * IDs must use the registering mod's namespace. The Host freezes the registry after the event has
 * been delivered in mod order.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class RegisterHaikalatExtensionsEvent extends Event implements IModBusEvent {
    private final BiConsumer<ResourceLocation, HaikalatRenderExtension> registrar;

    /**
     * Runtime constructor. Integration mods should subscribe to the event, not instantiate it.
     */
    public RegisterHaikalatExtensionsEvent(
            BiConsumer<ResourceLocation, HaikalatRenderExtension> registrar
    ) {
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }

    /**
     * Registers one extension at this position in deterministic mod-event order.
     */
    public void register(ResourceLocation id, HaikalatRenderExtension extension) {
        registrar.accept(
                Objects.requireNonNull(id, "id"),
                Objects.requireNonNull(extension, "extension"));
    }
}
