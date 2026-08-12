package org.hismeo.actionguide.api.cue;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.Objects;

public record CueEvent(CueItemId id, CueTime time, ResourceLocation type, int order, JsonObject payload) {
    public static final Comparator<CueEvent> ORDERING = Comparator.comparing(CueEvent::time)
            .thenComparingInt(CueEvent::order)
            .thenComparing(CueEvent::id);

    public CueEvent {
        Objects.requireNonNull(id, "event id");
        Objects.requireNonNull(time, "event time");
        Objects.requireNonNull(type, "event type");
        payload = payload == null ? new JsonObject() : payload.deepCopy();
    }

    @Override
    public JsonObject payload() {
        return payload.deepCopy();
    }
}
