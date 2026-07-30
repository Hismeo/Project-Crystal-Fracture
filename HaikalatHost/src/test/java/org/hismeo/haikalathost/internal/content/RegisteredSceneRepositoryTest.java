package org.hismeo.haikalathost.internal.content;

import com.kaleblangley.haikalat.subsystems.resources.AssetId;
import com.kaleblangley.haikalat.subsystems.resources.ResourceCatalog;
import com.kaleblangley.haikalat.subsystems.resources.ResourceGenerationTracker;
import com.kaleblangley.haikalat.subsystems.resources.ResourceSource;
import com.kaleblangley.haikalat.subsystems.scene.SceneAssetService;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.haikalathost.api.content.HaikalatAssetDefinition;
import org.hismeo.haikalathost.api.content.HaikalatAssetKind;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisteredSceneRepositoryTest {
    private static final ResourceLocation SCENE_ID =
            ResourceLocation.fromNamespaceAndPath("example", "scenes/test.scene.json");

    @Test
    void preparesOnlyRegisteredScenesWithoutGpuActivation() {
        AtomicReference<byte[]> resource = new AtomicReference<>(validScene("camera"));
        try (RegisteredSceneRepository repository = repository(resource, List.of(
                definition(SCENE_ID, HaikalatAssetKind.SCENE),
                definition(
                        ResourceLocation.fromNamespaceAndPath("example", "models/unused.glb"),
                        HaikalatAssetKind.MODEL)))) {
            repository.startInitialPreparation();
            repository.startInitialPreparation();

            var snapshot = repository.snapshot();
            assertTrue(snapshot.initialPreparationStarted());
            assertEquals(0L, snapshot.resourceGeneration());
            assertEquals(1, snapshot.readyCount());
            assertEquals(1, snapshot.scenes().size());
            assertEquals(
                    RegisteredSceneRepository.PreparationState.CPU_READY,
                    snapshot.scenes().getFirst().state());
            assertEquals(1, snapshot.scenes().getFirst().nodeCount());
            assertTrue(repository.preparedPlan(SCENE_ID).isPresent());
        }
    }

    @Test
    void retainsLastGoodCpuPlanAndReportsStructuredReloadFailure() {
        AtomicReference<byte[]> resource = new AtomicReference<>(validScene("first-camera"));
        try (RegisteredSceneRepository repository =
                     repository(resource, List.of(definition(SCENE_ID, HaikalatAssetKind.SCENE)))) {
            repository.startInitialPreparation();
            var initialPlan = repository.preparedPlan(SCENE_ID).orElseThrow();

            resource.set("{}".getBytes(StandardCharsets.UTF_8));
            repository.reloadAll(1L);
            repository.reloadAll(1L);

            var scene = repository.snapshot().scenes().getFirst();
            assertEquals(RegisteredSceneRepository.PreparationState.FAILED, scene.state());
            assertEquals(1L, scene.resourceGeneration());
            assertTrue(scene.hasPreparedPlan());
            assertEquals(0L, scene.preparedResourceGeneration());
            assertEquals(0L, scene.preparedSceneGeneration());
            assertNotNull(scene.failure());
            assertEquals("PARSE_SCENE", scene.failure().phase());
            assertEquals(SCENE_ID.toString(), scene.failure().asset());
            assertTrue(scene.failure().message().length() > 0);
            assertEquals(initialPlan, repository.preparedPlan(SCENE_ID).orElseThrow());
        }
    }

    @Test
    void successfulReloadAtomicallyReplacesCpuPlan() {
        AtomicReference<byte[]> resource = new AtomicReference<>(validScene("first-camera"));
        try (RegisteredSceneRepository repository =
                     repository(resource, List.of(definition(SCENE_ID, HaikalatAssetKind.SCENE)))) {
            repository.startInitialPreparation();

            resource.set(validScene("replacement-camera"));
            repository.reloadAll(7L);

            var scene = repository.snapshot().scenes().getFirst();
            assertEquals(RegisteredSceneRepository.PreparationState.CPU_READY, scene.state());
            assertEquals(7L, scene.resourceGeneration());
            assertEquals(7L, scene.preparedResourceGeneration());
            assertEquals(1L, scene.preparedSceneGeneration());
            assertEquals(
                    "replacement-camera",
                    repository.preparedPlan(SCENE_ID).orElseThrow()
                            .definition().nodes().getFirst().id());
        }
    }

    @Test
    void closeIsIdempotentAndReleasesPreparedPlans() {
        AtomicReference<byte[]> resource = new AtomicReference<>(validScene("camera"));
        RegisteredSceneRepository repository =
                repository(resource, List.of(definition(SCENE_ID, HaikalatAssetKind.SCENE)));
        repository.startInitialPreparation();

        repository.close();
        repository.close();

        assertTrue(repository.snapshot().closed());
        assertEquals(
                RegisteredSceneRepository.PreparationState.CLOSED,
                repository.snapshot().scenes().getFirst().state());
        assertFalse(repository.preparedPlan(SCENE_ID).isPresent());
        assertThrows(IllegalStateException.class, repository::startInitialPreparation);
        assertThrows(IllegalStateException.class, () -> repository.reloadAll(2L));
    }

    private static RegisteredSceneRepository repository(
            AtomicReference<byte[]> resource,
            List<HaikalatAssetDefinition> definitions
    ) {
        ResourceSource source = (assetId, maxBytes) -> {
            if (!assetId.equals(AssetId.of("example", "scenes/test.scene.json"))) {
                throw new FileNotFoundException(assetId.toString());
            }
            byte[] bytes = resource.get();
            if (bytes.length > maxBytes) {
                throw new IllegalStateException("fixture exceeds maxBytes");
            }
            return bytes.clone();
        };
        ResourceCatalog catalog = ResourceCatalog.builder()
                .mount("example", source)
                .build();
        SceneAssetService sceneAssets = new SceneAssetService(
                catalog,
                new ResourceGenerationTracker(),
                Runnable::run);
        return new RegisteredSceneRepository(sceneAssets, definitions);
    }

    private static HaikalatAssetDefinition definition(
            ResourceLocation id,
            HaikalatAssetKind kind
    ) {
        return new HaikalatAssetDefinition(id, kind);
    }

    private static byte[] validScene(String cameraNode) {
        return ("""
                {
                  "format": "haikalat.scene",
                  "version": 1,
                  "camera": {
                    "node": "%s",
                    "projection": {
                      "type": "perspective",
                      "fovYDegrees": 60,
                      "near": 0.1,
                      "far": 100
                    }
                  },
                  "nodes": [{"id": "%s"}]
                }
                """.formatted(cameraNode, cameraNode)).getBytes(StandardCharsets.UTF_8);
    }
}
