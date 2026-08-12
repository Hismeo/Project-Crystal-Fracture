package org.hismeo.actionguide.api.cue;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CombatCueDefinition(
        CombatCueId id,
        int schemaVersion,
        CueTime duration,
        SkeletonBinding skeleton,
        List<CueSection> sections,
        List<CueEvent> events,
        List<CueState> states,
        RootMotionContract rootMotion,
        boolean loop
) {
    public static final int CURRENT_SCHEMA_VERSION = 4;

    public CombatCueDefinition {
        Objects.requireNonNull(id, "cue id");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(skeleton, "skeleton");
        Objects.requireNonNull(rootMotion, "root motion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported combat cue schema: " + schemaVersion);
        }
        if (duration.equals(CueTime.ZERO)) {
            throw new IllegalArgumentException("duration must be greater than zero");
        }
        sections = sections.stream().sorted(Comparator.comparing(CueSection::start).thenComparing(CueSection::end).thenComparing(CueSection::id)).toList();
        events = events.stream().sorted(CueEvent.ORDERING).toList();
        states = states.stream().sorted(CueState.ORDERING).toList();
    }

    public Optional<CueSection> sectionAt(CueTime time) {
        return sections.stream().filter(section -> section.contains(time)).findFirst();
    }

    public CueSection requireSection(SectionId sectionId) {
        return sections.stream().filter(section -> section.id().equals(sectionId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown section " + sectionId + " in cue " + id));
    }
}
