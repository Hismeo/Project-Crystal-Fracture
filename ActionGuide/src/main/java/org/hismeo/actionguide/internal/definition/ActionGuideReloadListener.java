package org.hismeo.actionguide.internal.definition;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.hismeo.actionguide.ActionGuide;
import org.hismeo.actionguide.api.action.ActionDefinition;
import org.hismeo.actionguide.api.action.ActionId;
import org.hismeo.actionguide.api.cue.CombatCueDefinition;
import org.hismeo.actionguide.api.cue.CombatCueId;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ActionGuideReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private final ActionRegistry registry;
    private final CombatCueLoader cueLoader = new CombatCueLoader();
    private final ActionDefinitionLoader actionLoader = new ActionDefinitionLoader();

    public ActionGuideReloadListener(ActionRegistry registry) {
        super(GSON, "action_guide");
        this.registry = registry;
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> resources,
                         @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        List<CombatCueDefinition> cues = new ArrayList<>();
        List<ActionDefinition> actions = new ArrayList<>();
        List<DefinitionProblem> problems = new ArrayList<>();

        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLocation resource = entry.getKey();
            String path = resource.getPath();
            try {
                if (path.startsWith("combat_cues/")) {
                    CombatCueId id = new CombatCueId(ResourceLocation.fromNamespaceAndPath(resource.getNamespace(),
                            stripExtension(path.substring("combat_cues/".length()), ".combat")));
                    LoadedDefinition<CombatCueDefinition> loaded = cueLoader.load(id, entry.getValue().toString());
                    cues.add(loaded.value());
                    problems.addAll(loaded.warnings());
                } else if (path.startsWith("actions/")) {
                    ActionId id = new ActionId(ResourceLocation.fromNamespaceAndPath(resource.getNamespace(),
                            path.substring("actions/".length())));
                    actions.add(actionLoader.load(id, entry.getValue().toString()));
                }
            } catch (DefinitionLoadException exception) {
                problems.addAll(exception.problems());
            }
        });

        if (problems.stream().anyMatch(problem -> problem.severity() == ProblemSeverity.ERROR)) {
            throw new DefinitionLoadException("data/*/action_guide", problems);
        }
        RegistrySnapshot snapshot = registry.publish(cues, actions);
        problems.forEach(problem -> ActionGuide.LOGGER.warn("[ActionGuide reload] {}", problem));
        ActionGuide.LOGGER.info("Published ActionGuide generation {} with {} combat cues and {} actions",
                snapshot.generation(), snapshot.cues().size(), snapshot.actions().size());
    }

    private static String stripExtension(String path, String extension) {
        return path.endsWith(extension) ? path.substring(0, path.length() - extension.length()) : path;
    }
}
