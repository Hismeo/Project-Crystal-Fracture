package org.hismeo.actionguide.api.cue;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.Objects;

public record CueState(CueItemId id, ResourceLocation type, CueTime start, CueTime end, JsonObject payload) {
    public static final Comparator<CueState> ORDERING = Comparator.comparing(CueState::start)
            .thenComparing(CueState::end)
            .thenComparing(CueState::id);

    public CueState {
        Objects.requireNonNull(id, "state id");
        Objects.requireNonNull(type, "state type");
        Objects.requireNonNull(start, "state start");
        Objects.requireNonNull(end, "state end");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("state " + id + " must use a valid [start, end) interval");
        }
        payload = payload == null ? new JsonObject() : payload.deepCopy();
    }

    @Override
    public JsonObject payload() {
        return payload.deepCopy();
    }

    public boolean activeAt(CueTime time) {
        return start.isBeforeOrEqual(time) && time.isBefore(end);
    }
}
