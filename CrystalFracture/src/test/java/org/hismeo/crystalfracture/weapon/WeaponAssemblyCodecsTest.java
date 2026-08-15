package org.hismeo.crystalfracture.weapon;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.hismeo.crystalfracture.weapon.api.WeaponAssembly;
import org.hismeo.crystalfracture.weapon.api.WeaponAssemblyCodecs;
import org.hismeo.crystalfracture.weapon.api.WeaponPartId;
import org.hismeo.crystalfracture.weapon.api.WeaponSchemaId;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WeaponAssemblyCodecsTest {
    @Test
    void archiveCodecRoundTripsOnlyAuthorityIds() {
        WeaponAssembly original = assembly(false);
        var encoded = WeaponAssemblyCodecs.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        JsonObject json = encoded.getAsJsonObject();
        assertEquals(2, json.size());
        assertEquals("crystal_fracture:exported_standard_sword", json.get("schema_id").getAsString());
        assertEquals(original, WeaponAssemblyCodecs.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void networkCodecUsesDeterministicSlotOrder() {
        byte[] forward = encode(assembly(false));
        byte[] reverse = encode(assembly(true));
        assertArrayEquals(forward, reverse);

        ByteBuf buffer = Unpooled.wrappedBuffer(forward);
        try {
            assertEquals(assembly(false), WeaponAssemblyCodecs.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    private static byte[] encode(WeaponAssembly assembly) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            WeaponAssemblyCodecs.STREAM_CODEC.encode(buffer, assembly);
            return ByteBufUtil.getBytes(buffer);
        } finally {
            buffer.release();
        }
    }

    private static WeaponAssembly assembly(boolean reverse) {
        Map<WeaponSlotId, WeaponPartId> parts = new LinkedHashMap<>();
        if (reverse) {
            parts.put(new WeaponSlotId("handle"), WeaponPartId.parse("crystal_fracture:wood_shaft"));
            parts.put(new WeaponSlotId("crossguard"), WeaponPartId.parse("crystal_fracture:crossguard"));
            parts.put(new WeaponSlotId("blade"), WeaponPartId.parse("crystal_fracture:sword"));
        } else {
            parts.put(new WeaponSlotId("blade"), WeaponPartId.parse("crystal_fracture:sword"));
            parts.put(new WeaponSlotId("crossguard"), WeaponPartId.parse("crystal_fracture:crossguard"));
            parts.put(new WeaponSlotId("handle"), WeaponPartId.parse("crystal_fracture:wood_shaft"));
        }
        return new WeaponAssembly(
                WeaponSchemaId.parse("crystal_fracture:exported_standard_sword"),
                parts);
    }
}
