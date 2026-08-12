package org.hismeo.actionguide.internal.definition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.hismeo.actionguide.api.action.ActionChannel;
import org.hismeo.actionguide.api.action.ActionDefinition;
import org.hismeo.actionguide.api.action.ActionEndPolicy;
import org.hismeo.actionguide.api.action.ActionId;
import org.hismeo.actionguide.api.action.ActionIntentId;
import org.hismeo.actionguide.api.action.ActionTag;
import org.hismeo.actionguide.api.action.ActionTransition;
import org.hismeo.actionguide.api.action.TransitionMode;
import org.hismeo.actionguide.api.cue.CombatCueId;
import org.hismeo.actionguide.api.cue.CueItemId;
import org.hismeo.actionguide.api.cue.SectionId;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class ActionDefinitionLoader {
    public ActionDefinition load(ActionId id, Reader json) {
        try {
            return load(id, JsonParser.parseReader(json));
        } catch (DefinitionLoadException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DefinitionLoadException(id.toString(), List.of(
                    new DefinitionProblem(ProblemSeverity.ERROR, "$", exception.getMessage())));
        }
    }

    public ActionDefinition load(ActionId id, String json) {
        try {
            return load(id, JsonParser.parseString(json));
        } catch (DefinitionLoadException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DefinitionLoadException(id.toString(), List.of(
                    new DefinitionProblem(ProblemSeverity.ERROR, "$", exception.getMessage())));
        }
    }

    private ActionDefinition load(ActionId id, JsonElement json) {
        if (!json.isJsonObject()) {
            throw new JsonParseException("$ must be an object");
        }
        JsonObject root = json.getAsJsonObject();
        Set<ActionTag> tags = new HashSet<>();
        for (JsonElement value : array(root, "tags")) {
            tags.add(ActionTag.parse(value.getAsString()));
        }
        return new ActionDefinition(
                id,
                CombatCueId.parse(JsonFields.string(root, "cue", "$")),
                new SectionId(JsonFields.string(root, "entry_section", "$")),
                parseEnum(ActionChannel.class, JsonFields.optionalString(root, "channel", "full_body"), "channel"),
                tags,
                parseTransitions(array(root, "transitions")),
                parseEnum(ActionEndPolicy.class, JsonFields.optionalString(root, "end_policy", "complete"), "end policy")
        );
    }

    private static List<ActionTransition> parseTransitions(JsonArray array) {
        List<ActionTransition> result = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            String path = "$.transitions[" + index + "]";
            JsonElement element = array.get(index);
            if (!element.isJsonObject()) {
                throw new JsonParseException(path + " must be an object");
            }
            JsonObject value = element.getAsJsonObject();
            Optional<SectionId> targetSection = value.has("target_section")
                    ? Optional.of(new SectionId(JsonFields.string(value, "target_section", path))) : Optional.empty();
            Optional<ActionId> targetAction = value.has("target_action")
                    ? Optional.of(ActionId.parse(JsonFields.string(value, "target_action", path))) : Optional.empty();
            result.add(new ActionTransition(
                    new SectionId(JsonFields.string(value, "from_section", path)),
                    new CueItemId(JsonFields.string(value, "input_state", path)),
                    ActionIntentId.parse(JsonFields.string(value, "intent", path)),
                    targetSection,
                    targetAction,
                    parseEnum(TransitionMode.class, JsonFields.optionalString(value, "mode", "direct"), "transition mode")
            ));
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

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new JsonParseException("invalid " + label + ": " + value, exception);
        }
    }
}
