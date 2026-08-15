package org.hismeo.actionguide.internal.definition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.hismeo.actionguide.api.ResourceIds;
import org.hismeo.actionguide.api.cue.CombatCueDefinition;
import org.hismeo.actionguide.api.cue.CombatCueId;
import org.hismeo.actionguide.api.cue.CueEvent;
import org.hismeo.actionguide.api.cue.CueItemId;
import org.hismeo.actionguide.api.cue.CueSection;
import org.hismeo.actionguide.api.cue.CueState;
import org.hismeo.actionguide.api.cue.CueTime;
import org.hismeo.actionguide.api.cue.RootMotionContract;
import org.hismeo.actionguide.api.cue.RootMotionKeyframe;
import org.hismeo.actionguide.api.cue.RootMotionMode;
import org.hismeo.actionguide.api.cue.SectionId;
import org.hismeo.actionguide.api.cue.SkeletonBinding;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CombatCueLoader {
    private final DefinitionValidator validator;

    public CombatCueLoader() {
        this(new DefinitionValidator());
    }

    public CombatCueLoader(DefinitionValidator validator) {
        this.validator = validator;
    }

    public LoadedDefinition<CombatCueDefinition> load(CombatCueId id, Reader json) {
        try {
            return load(id, JsonParser.parseReader(json));
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException exception) {
            throw syntaxFailure(id.toString(), exception);
        }
    }

    public LoadedDefinition<CombatCueDefinition> load(CombatCueId id, String json) {
        try {
            return load(id, JsonParser.parseString(json));
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException exception) {
            throw syntaxFailure(id.toString(), exception);
        }
    }

    private LoadedDefinition<CombatCueDefinition> load(CombatCueId id, JsonElement json) {
        if (!json.isJsonObject()) {
            throw new JsonParseException("$ must be an object");
        }
        JsonObject root = json.getAsJsonObject();
        int schema = JsonFields.integer(root, "schema_version", "$");
        if (schema != CombatCueDefinition.CURRENT_SCHEMA_VERSION) {
            throw new JsonParseException("$.schema_version unsupported schema " + schema + "; expected 4 with authoritative duration");
        }
        CueTime duration = CueTime.fromSeconds(JsonFields.decimal(root, "duration", "$"));
        JsonObject skeletonJson = JsonFields.object(root, "skeleton", "$" );
        SkeletonBinding skeleton = new SkeletonBinding(
                ResourceIds.parse(JsonFields.string(skeletonJson, "id", "$.skeleton"), "skeleton id"),
                JsonFields.optionalInteger(skeletonJson, "version", 1),
                Optional.ofNullable(skeletonJson.has("topology_hash") ? skeletonJson.get("topology_hash").getAsString() : null)
        );
        JsonObject rootMotionJson = JsonFields.optionalObject(root, "root_motion");
        boolean rootMotionEnabled = JsonFields.optionalBoolean(rootMotionJson, "enabled", true);
        RootMotionContract rootMotion = new RootMotionContract(
                rootMotionEnabled,
                JsonFields.optionalString(rootMotionJson, "bone", rootMotionEnabled ? "motion_root" : ""),
                RootMotionMode.parse(JsonFields.optionalString(rootMotionJson, "mode", "xz_yaw")),
                parseRootMotionKeyframes(array(rootMotionJson, "keyframes"))
        );
        CombatCueDefinition cue = new CombatCueDefinition(
                id, schema, duration, skeleton,
                parseSections(array(root, "sections")),
                parseEvents(array(root, "events")),
                parseStates(array(root, "states")),
                rootMotion,
                JsonFields.optionalBoolean(root, "loop", false)
        );
        List<DefinitionProblem> problems = validator.validate(cue);
        List<DefinitionProblem> errors = problems.stream().filter(problem -> problem.severity() == ProblemSeverity.ERROR).toList();
        if (!errors.isEmpty()) {
            throw new DefinitionLoadException(id.toString(), problems);
        }
        return new LoadedDefinition<>(cue, problems);
    }

    private static List<CueSection> parseSections(JsonArray array) {
        List<CueSection> result = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            String path = "$.sections[" + index + "]";
            JsonObject value = requireObject(array.get(index), path);
            result.add(new CueSection(
                    new SectionId(JsonFields.string(value, "id", path)),
                    CueTime.fromSeconds(JsonFields.decimal(value, "start", path)),
                    CueTime.fromSeconds(JsonFields.decimal(value, "end", path))
            ));
        }
        return result;
    }

    private static List<CueEvent> parseEvents(JsonArray array) {
        List<CueEvent> result = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            String path = "$.events[" + index + "]";
            JsonObject value = requireObject(array.get(index), path);
            result.add(new CueEvent(
                    new CueItemId(JsonFields.string(value, "id", path)),
                    CueTime.fromSeconds(JsonFields.decimal(value, "time", path)),
                    CueTypes.event(JsonFields.string(value, "type", path)),
                    JsonFields.optionalInteger(value, "order", 0),
                    JsonFields.optionalObject(value, "payload")
            ));
        }
        return result;
    }

    private static List<CueState> parseStates(JsonArray array) {
        List<CueState> result = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            String path = "$.states[" + index + "]";
            JsonObject value = requireObject(array.get(index), path);
            result.add(new CueState(
                    new CueItemId(JsonFields.string(value, "id", path)),
                    CueTypes.state(JsonFields.string(value, "type", path)),
                    CueTime.fromSeconds(JsonFields.decimal(value, "start", path)),
                    CueTime.fromSeconds(JsonFields.decimal(value, "end", path)),
                    JsonFields.optionalObject(value, "payload")
            ));
        }
        return result;
    }

    private static List<RootMotionKeyframe> parseRootMotionKeyframes(JsonArray array) {
        List<RootMotionKeyframe> result = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            String path = "$.root_motion.keyframes[" + index + "]";
            JsonObject value = requireObject(array.get(index), path);
            result.add(new RootMotionKeyframe(
                    CueTime.fromSeconds(JsonFields.decimal(value, "time", path)),
                    JsonFields.decimal(value, "x", path).doubleValue(),
                    JsonFields.decimal(value, "y", path).doubleValue(),
                    JsonFields.decimal(value, "z", path).doubleValue(),
                    value.has("yaw")
                            ? JsonFields.decimal(value, "yaw", path).doubleValue()
                            : 0.0));
        }
        return result;
    }

    private static JsonArray array(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || value.isJsonNull()) {
            return new JsonArray();
        }
        if (!value.isJsonArray()) {
            throw new JsonParseException("$." + name + " must be an array");
        }
        return value.getAsJsonArray();
    }

    private static JsonObject requireObject(JsonElement value, String path) {
        if (!value.isJsonObject()) {
            throw new JsonParseException(path + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static DefinitionLoadException syntaxFailure(String resource, RuntimeException exception) {
        if (exception instanceof DefinitionLoadException definitionLoadException) {
            return definitionLoadException;
        }
        return new DefinitionLoadException(resource, List.of(new DefinitionProblem(ProblemSeverity.ERROR, "$", exception.getMessage())));
    }
}
