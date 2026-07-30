package org.hismeo.haikalathost.internal.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class WorldScopeCoordinatorTest {
    @Test
    void unloadedWorldIsNotReopenedDuringMinecraftTransitionFrame() {
        WorldScopeCoordinator<Object> worlds = new WorldScopeCoordinator<>();
        Object oldWorld = new Object();
        Object newWorld = new Object();

        worlds.loaded(oldWorld);
        assertSame(oldWorld, worlds.reconcile(oldWorld));

        worlds.unloaded(oldWorld);
        assertNull(worlds.reconcile(oldWorld));
        assertNull(worlds.reconcile(oldWorld));

        worlds.loaded(newWorld);
        assertNull(worlds.reconcile(oldWorld));
        assertSame(newWorld, worlds.reconcile(newWorld));
    }

    @Test
    void scopesUseObjectIdentityRatherThanEquals() {
        WorldScopeCoordinator<EqualWorld> worlds = new WorldScopeCoordinator<>();
        EqualWorld first = new EqualWorld("same");
        EqualWorld second = new EqualWorld("same");

        worlds.loaded(first);
        assertSame(first, worlds.reconcile(first));
        worlds.unloaded(first);
        worlds.loaded(second);

        assertSame(second, worlds.reconcile(second));
    }

    private record EqualWorld(String key) {
    }
}
