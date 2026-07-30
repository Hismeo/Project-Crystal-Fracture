package org.hismeo.haikalathost.internal.api;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.haikalathost.api.content.HaikalatAssetDefinition;
import org.hismeo.haikalathost.api.content.HaikalatAssetKind;
import org.hismeo.haikalathost.api.content.HaikalatAssetState;
import org.hismeo.haikalathost.internal.content.RegisteredSceneRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftHaikalatAssetsTest {
    private static final ResourceLocation SCENE =
            ResourceLocation.fromNamespaceAndPath(
                    "example",
                    "haikalat/scenes/test.scene.json");
    private static final ResourceLocation EFFECT =
            ResourceLocation.fromNamespaceAndPath(
                    "example",
                    "haikalat/effects/test.effect.json");

    @Test
    void exposesFailedReplacementAndRetainedCpuPlanWithoutClaimingGpuReadiness() {
        var failure = new RegisteredSceneRepository.FailureSnapshot(
                "PARSE_SCENE",
                SCENE.toString(),
                IllegalArgumentException.class.getName(),
                "bad json");
        var scene = new RegisteredSceneRepository.SceneSnapshot(
                SCENE,
                RegisteredSceneRepository.PreparationState.FAILED,
                4L,
                2L,
                true,
                3L,
                1L,
                5,
                1,
                failure);
        var repository = new RegisteredSceneRepository.RepositorySnapshot(
                false,
                true,
                4L,
                0,
                0,
                1,
                List.of(scene));

        var statuses = MinecraftHaikalatAssets.map(
                List.of(
                        new HaikalatAssetDefinition(
                                EFFECT,
                                HaikalatAssetKind.EFFECT,
                                "example"),
                        new HaikalatAssetDefinition(
                                SCENE,
                                HaikalatAssetKind.SCENE,
                                "example")),
                repository);

        assertEquals(List.of(EFFECT, SCENE), statuses.stream().map(status -> status.id()).toList());
        assertEquals(HaikalatAssetState.DECLARED, statuses.get(0).state());
        assertEquals(HaikalatAssetState.FAILED, statuses.get(1).state());
        assertTrue(statuses.get(1).lastKnownGood());
        assertEquals(3L, statuses.get(1).preparedResourceGeneration());
        assertTrue(statuses.get(1).message().contains("PARSE_SCENE"));
    }
}
