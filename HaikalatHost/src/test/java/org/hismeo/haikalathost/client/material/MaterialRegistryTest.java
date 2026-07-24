package org.hismeo.haikalathost.client.material;

import org.hismeo.haikalathost.client.scene.MaterialId;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaterialRegistryTest {
    @Test
    void deduplicatesKeysAndWritesStableGpuTable() {
        MaterialRegistry registry = new MaterialRegistry();
        int features = MaterialFeature.mask(
                MaterialFeature.TEXTURED, MaterialFeature.VERTEX_COLOR, MaterialFeature.ALPHA_CUTOUT);
        MaterialKey key = new MaterialKey(
                ShaderFamily.WORLD_CUTOUT, features, 11L, 12L, 3, 2, 0.5F, 7);

        MaterialId first = registry.resolve(key);
        MaterialId duplicate = registry.resolve(key);
        ByteBuffer table = ByteBuffer.allocate(GpuMaterial.BYTES).order(ByteOrder.nativeOrder());
        registry.writeGpuTable(table);
        table.flip();

        assertEquals(first, duplicate);
        assertEquals(1, registry.size());
        assertEquals(11L, table.getLong());
        assertEquals(12L, table.getLong());
        assertEquals(2, table.getInt());
        assertEquals(features, table.getInt());
        assertEquals(0.5F, table.getFloat());
        assertEquals(7, table.getInt());
    }

    @Test
    void rejectsCutoffWithoutCutoutFeatureAndInvalidIds() {
        assertThrows(IllegalArgumentException.class, () -> new MaterialKey(
                ShaderFamily.UI, 0, 0L, 0L, 0, 0, 0.25F, 0));

        MaterialRegistry registry = new MaterialRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.get(new MaterialId(0)));
    }
}
