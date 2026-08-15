package org.hismeo.crystalfracture.weapon;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import org.hismeo.crystalfracture.CrystalFracture;
import org.hismeo.crystalfracture.weapon.api.WeaponAssemblies;
import org.hismeo.crystalfracture.weapon.api.WeaponAssembly;
import org.hismeo.crystalfracture.weapon.api.WeaponAssemblyCodecs;

/** Persisted and automatically synchronized player weapon selection. */
public final class WeaponAttachments {
    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CrystalFracture.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<WeaponAssembly>> EQUIPPED =
            TYPES.register("equipped_weapon", () -> AttachmentType.builder(() -> WeaponAssemblies.DEFAULT_SWORD)
                    .serialize(WeaponAssemblyCodecs.CODEC)
                    .copyOnDeath()
                    .sync(WeaponAssemblyCodecs.STREAM_CODEC)
                    .build());

    /** Non-persistent compatibility metadata for the authoritative logical Registry. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> REGISTRY_HASH =
            TYPES.register("weapon_registry_hash", () -> AttachmentType.builder(() -> "")
                    .sync(ByteBufCodecs.STRING_UTF8)
                    .build());

    private WeaponAttachments() {
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
