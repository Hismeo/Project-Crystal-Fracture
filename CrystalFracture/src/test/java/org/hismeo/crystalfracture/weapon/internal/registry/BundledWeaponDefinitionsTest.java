package org.hismeo.crystalfracture.weapon.internal.registry;

import org.hismeo.crystalfracture.weapon.api.MarkerName;
import org.hismeo.crystalfracture.weapon.api.WeaponAssemblies;
import org.hismeo.crystalfracture.weapon.internal.compile.WeaponAssemblyCompiler;
import org.hismeo.crystalfracture.weapon.internal.reload.WeaponResourceBatchLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledWeaponDefinitionsTest {
    @Test
    void manifestLoadsEveryBundledDefinitionAndCompilesDefaultSword() {
        var result = new WeaponResourceBatchLoader().load(BundledWeaponDefinitions.loadResources());
        assertTrue(result.snapshot().isPresent(), () -> "bundled problems: " + result.problems());
        var snapshot = result.snapshot().orElseThrow();
        assertEquals(9, snapshot.partTypes().size());
        assertEquals(17, snapshot.parts().size());
        assertEquals(2, snapshot.schemas().size());
        String firstHash = WeaponRegistryContentHasher.sha256(snapshot);
        String secondHash = WeaponRegistryContentHasher.sha256(
                new WeaponResourceBatchLoader().load(BundledWeaponDefinitions.loadResources())
                        .snapshot().orElseThrow());
        assertTrue(firstHash.startsWith("sha256:"));
        assertEquals(firstHash, secondHash);

        var compiled = new WeaponAssemblyCompiler().compile(WeaponAssemblies.DEFAULT_SWORD, snapshot);
        assertTrue(compiled.valid(), () -> "compile problems: " + compiled.problems());
        assertEquals(
                List.of("main_hand_grip", "trail_end", "trail_start"),
                compiled.assembly().orElseThrow().markers().keySet().stream()
                        .map(MarkerName::value)
                        .toList());
    }
}
