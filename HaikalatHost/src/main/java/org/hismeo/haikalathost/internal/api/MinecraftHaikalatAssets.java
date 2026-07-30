package org.hismeo.haikalathost.internal.api;

import org.hismeo.haikalathost.api.content.HaikalatAssetDefinition;
import org.hismeo.haikalathost.api.content.HaikalatAssetState;
import org.hismeo.haikalathost.api.content.HaikalatAssetStatus;
import org.hismeo.haikalathost.api.content.HaikalatAssets;
import org.hismeo.haikalathost.internal.content.HaikalatContentRegistry;
import org.hismeo.haikalathost.internal.content.RegisteredSceneRepository;
import org.hismeo.haikalathost.internal.runtime.MinecraftHaikalatRuntime;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Maps internal CPU scene preparation state to the stable, GL-free asset API.
 */
public final class MinecraftHaikalatAssets implements HaikalatAssets {
    @Override
    public List<HaikalatAssetStatus> all() {
        return map(
                HaikalatContentRegistry.instance().assets(),
                MinecraftHaikalatRuntime.instance().sceneRepositorySnapshot());
    }

    static List<HaikalatAssetStatus> map(
            List<HaikalatAssetDefinition> definitions,
            RegisteredSceneRepository.RepositorySnapshot repository
    ) {
        Map<net.minecraft.resources.ResourceLocation,
                RegisteredSceneRepository.SceneSnapshot> scenes =
                repository == null
                        ? Map.of()
                        : repository.scenes().stream().collect(Collectors.toUnmodifiableMap(
                                RegisteredSceneRepository.SceneSnapshot::id,
                                Function.identity()));

        return definitions.stream()
                .map(definition -> map(definition, scenes.get(definition.id())))
                .sorted(Comparator.comparing(status -> status.id().toString()))
                .toList();
    }

    private static HaikalatAssetStatus map(
            HaikalatAssetDefinition definition,
            RegisteredSceneRepository.SceneSnapshot scene
    ) {
        if (scene == null) {
            return new HaikalatAssetStatus(
                    definition.id(),
                    definition.kind(),
                    definition.ownerModId(),
                    HaikalatAssetState.DECLARED,
                    -1L,
                    -1L,
                    false,
                    "declared",
                    "Asset is registered; no asset-specific CPU consumer is active");
        }

        HaikalatAssetState state = switch (scene.state()) {
            case REGISTERED -> HaikalatAssetState.DECLARED;
            case PREPARING -> HaikalatAssetState.PREPARING;
            case CPU_READY -> HaikalatAssetState.CPU_READY;
            case FAILED -> HaikalatAssetState.FAILED;
            case CLOSED -> HaikalatAssetState.CLOSED;
        };
        boolean lastKnownGood = scene.hasPreparedPlan()
                && scene.preparedResourceGeneration() != scene.resourceGeneration();
        String reasonCode = switch (state) {
            case DECLARED -> "declared";
            case PREPARING -> "scene_preparing";
            case CPU_READY -> "scene_cpu_ready";
            case FAILED -> "scene_prepare_failed";
            case CLOSED -> "scene_repository_closed";
        };
        String message = scene.failure() == null
                ? switch (state) {
                    case DECLARED -> "Scene is registered";
                    case PREPARING -> "Scene CPU preparation is in progress";
                    case CPU_READY -> "Scene CPU plan is ready; GPU activation is not yet available";
                    case FAILED -> "Scene CPU preparation failed";
                    case CLOSED -> "Scene preparation repository is closed";
                }
                : "Scene preparation failed at "
                        + valueOrUnknown(scene.failure().phase())
                        + " for "
                        + valueOrUnknown(scene.failure().asset())
                        + ": "
                        + scene.failure().message();

        return new HaikalatAssetStatus(
                definition.id(),
                definition.kind(),
                definition.ownerModId(),
                state,
                scene.resourceGeneration(),
                scene.preparedResourceGeneration(),
                lastKnownGood,
                reasonCode,
                message);
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
