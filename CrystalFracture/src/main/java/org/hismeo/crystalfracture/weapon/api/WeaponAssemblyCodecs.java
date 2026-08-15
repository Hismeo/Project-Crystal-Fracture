package org.hismeo.crystalfracture.weapon.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.TreeMap;

/** Canonical disk and wire encoding. Only schema and sorted slot-to-Part IDs are persisted. */
public final class WeaponAssemblyCodecs {
    private static final int MAX_PARTS = 256;
    private static final Codec<Map<String, ResourceLocation>> PARTS_CODEC =
            Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC);

    public static final Codec<WeaponAssembly> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("schema_id")
                    .forGetter(assembly -> assembly.schema().value()),
            PARTS_CODEC.fieldOf("parts")
                    .forGetter(WeaponAssemblyCodecs::archiveParts)
    ).apply(instance, WeaponAssemblyCodecs::fromArchive));

    public static final StreamCodec<ByteBuf, WeaponAssembly> STREAM_CODEC = StreamCodec.ofMember(
            WeaponAssemblyCodecs::encode,
            WeaponAssemblyCodecs::decode);

    private WeaponAssemblyCodecs() {
    }

    private static Map<String, ResourceLocation> archiveParts(WeaponAssembly assembly) {
        Map<String, ResourceLocation> result = new TreeMap<>();
        assembly.parts().forEach((slot, part) -> result.put(slot.value(), part.value()));
        return result;
    }

    private static WeaponAssembly fromArchive(
            ResourceLocation schema,
            Map<String, ResourceLocation> encodedParts
    ) {
        if (encodedParts.size() > MAX_PARTS) {
            throw new IllegalArgumentException("weapon assembly exceeds " + MAX_PARTS + " parts");
        }
        Map<WeaponSlotId, WeaponPartId> parts = new TreeMap<>();
        encodedParts.forEach((slot, part) -> parts.put(new WeaponSlotId(slot), new WeaponPartId(part)));
        return new WeaponAssembly(new WeaponSchemaId(schema), parts);
    }

    private static void encode(WeaponAssembly assembly, ByteBuf raw) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(raw);
        buffer.writeResourceLocation(assembly.schema().value());
        buffer.writeVarInt(assembly.parts().size());
        assembly.parts().forEach((slot, part) -> {
            buffer.writeUtf(slot.value(), 256);
            buffer.writeResourceLocation(part.value());
        });
    }

    private static WeaponAssembly decode(ByteBuf raw) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(raw);
        WeaponSchemaId schema = new WeaponSchemaId(buffer.readResourceLocation());
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_PARTS) {
            throw new DecoderException("invalid weapon assembly part count: " + size);
        }
        Map<WeaponSlotId, WeaponPartId> parts = new TreeMap<>();
        for (int index = 0; index < size; index++) {
            WeaponSlotId slot = new WeaponSlotId(buffer.readUtf(256));
            WeaponPartId previous = parts.put(slot, new WeaponPartId(buffer.readResourceLocation()));
            if (previous != null) {
                throw new DecoderException("duplicate weapon slot: " + slot);
            }
        }
        return new WeaponAssembly(schema, parts);
    }
}
