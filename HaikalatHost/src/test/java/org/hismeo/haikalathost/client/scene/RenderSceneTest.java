package org.hismeo.haikalathost.client.scene;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderSceneTest {
    private static final Bounds BOUNDS = new Bounds(0, 0, 0, 1, 1, 1);

    @Test
    void generationPreventsRemovedHandleFromBecomingLiveAgain() {
        RenderScene scene = new RenderScene();
        RenderObjectId first = scene.create(object(1));
        scene.remove(first);
        RenderObjectId second = scene.create(object(2));

        assertEquals(first.index(), second.index());
        assertNotEquals(first.generation(), second.generation());
        assertFalse(scene.isLive(first));
        assertTrue(scene.isLive(second));
        assertThrows(StaleRenderObjectException.class, () -> scene.get(first));
    }

    @Test
    void appliesImmutableDeltaAtFrameBoundary() {
        RenderScene scene = new RenderScene();
        RenderSceneDelta delta = new RenderSceneDelta(List.of(
                new RenderSceneDelta.Add(10L, object(1)),
                new RenderSceneDelta.Add(20L, object(2))));

        Map<Long, RenderObjectId> added = scene.apply(delta);

        assertEquals(2, scene.size());
        scene.update(added.get(10L), object(3));
        assertEquals(new MeshId(3), scene.get(added.get(10L)).mesh());
    }

    private static RenderObject object(int mesh) {
        return new RenderObject(new MeshId(mesh), new MaterialId(0), new TransformId(0), BOUNDS, 0);
    }
}
