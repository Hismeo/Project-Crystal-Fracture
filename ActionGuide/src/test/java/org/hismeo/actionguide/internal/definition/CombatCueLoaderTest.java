package org.hismeo.actionguide.internal.definition;

import org.hismeo.actionguide.api.cue.CombatCueDefinition;
import org.hismeo.actionguide.api.cue.CombatCueId;
import org.hismeo.actionguide.api.cue.CueTime;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatCueLoaderTest {
    private final CombatCueLoader loader = new CombatCueLoader();

    @Test
    void loadsGoldenFixtureAndNormalizesTimeAndOrder() {
        var stream = getClass().getResourceAsStream("/fixtures/sword_slash.combat.json");
        var loaded = loader.load(CombatCueId.parse("crystal_fracture:sword_slash"),
                new InputStreamReader(stream, StandardCharsets.UTF_8));

        CombatCueDefinition cue = loaded.value();
        assertEquals(4, cue.schemaVersion());
        assertEquals(800_000, cue.duration().micros());
        assertEquals(210_000, cue.states().stream().filter(state -> state.id().value().equals("attack_main"))
                .findFirst().orElseThrow().start().micros());
        assertEquals("start", cue.events().getFirst().id().value());
        assertEquals("swing_sound", cue.events().get(1).id().value());
        assertEquals("swing_vfx", cue.events().get(2).id().value());
        assertTrue(loaded.warnings().isEmpty());
    }

    @Test
    void roundsTimeToSixDecimalPlaces() {
        assertEquals(1_458_333, CueTime.fromSeconds("1.458333333").micros());
        assertEquals(500_010, CueTime.fromSeconds("0.5000095").micros());
    }

    @Test
    void rejectsV3BecauseItHasNoAuthoritativeDuration() {
        DefinitionLoadException error = assertThrows(DefinitionLoadException.class, () -> loader.load(
                CombatCueId.parse("test:v3"), "{\"schema_version\":3}"));
        assertTrue(error.getMessage().contains("expected 4"));
    }

    @Test
    void realPluginExportRequiresV4ContractThenLoadsWithoutLosingEventsOrStates() throws IOException {
        String pluginV3;
        try (var stream = getClass().getResourceAsStream("/fixtures/player_wild.plugin-v3.json")) {
            pluginV3 = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        DefinitionLoadException rejected = assertThrows(DefinitionLoadException.class, () ->
                loader.load(CombatCueId.parse("test:player_wild"), pluginV3));
        assertTrue(rejected.getMessage().contains("expected 4 with authoritative duration"));

        String runtimeV4 = pluginV3
                .replace("\"schema_version\": 3", "\"schema_version\": 4,\n  \"duration\": 1.70833");
        CombatCueDefinition cue = loader.load(CombatCueId.parse("test:player_wild"), runtimeV4).value();
        assertEquals(1_708_330, cue.duration().micros());
        assertEquals(6, cue.events().size());
        assertEquals(9, cue.states().size());
        assertEquals("full", cue.rootMotion().mode().serializedName());
    }

    @Test
    void rejectsIntervalsDuplicatesMissingSlotsAndUnnamespacedTypes() {
        String json = """
                {"schema_version":4,"duration":1,"skeleton":{"id":"game:test","version":1},
                 "sections":[{"id":"same","start":0,"end":0.6},{"id":"overlap","start":0.5,"end":1}],
                 "events":[{"id":"same","time":0.1,"type":"bad_custom","payload":{}}],
                 "states":[{"id":"input","type":"input","start":0.2,"end":0.3,"payload":{}}],
                 "root_motion":{"enabled":false,"mode":"xz_yaw"}}
                """;
        DefinitionLoadException error = assertThrows(DefinitionLoadException.class, () ->
                loader.load(CombatCueId.parse("test:invalid"), json));
        assertTrue(error.getMessage().contains("custom event type must be namespaced"));

        String structurallyValidTypes = json.replace("bad_custom", "test:custom");
        DefinitionLoadException validation = assertThrows(DefinitionLoadException.class, () ->
                loader.load(CombatCueId.parse("test:invalid"), structurallyValidTypes));
        assertTrue(validation.getMessage().contains("duplicate cue item id"));
        assertTrue(validation.getMessage().contains("overlap"));
        assertTrue(validation.getMessage().contains("input state requires slot"));
    }

    @Test
    void sectionGapsAreWarningsOnlyAndCollectionsAreImmutable() {
        String json = """
                {"schema_version":4,"duration":1,"skeleton":{"id":"game:test","version":1},
                 "sections":[{"id":"one","start":0,"end":0.4},{"id":"two","start":0.5,"end":1}],
                 "events":[],"states":[],"root_motion":{"enabled":false,"mode":"xz_yaw"}}
                """;
        var loaded = loader.load(CombatCueId.parse("test:gap"), json);
        assertFalse(loaded.warnings().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> loaded.value().sections().clear());
    }
}
