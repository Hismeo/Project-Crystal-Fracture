package org.hismeo.crystalfracture.dash;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DashCombatCueResourceTest {
    @Test
    void dashKeepsTheAuthoredCombatCueRootTrack() throws Exception {
        var stream = getClass().getResourceAsStream(
                "/data/crystal_fracture/action_guide/combat_cues/dash.combat.json");
        assertNotNull(stream);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var cue = JsonParser.parseReader(reader).getAsJsonObject();
            var rootMotion = cue.getAsJsonObject("root_motion");
            var keyframes = rootMotion.getAsJsonArray("keyframes");
            assertTrue(rootMotion.get("enabled").getAsBoolean());
            assertEquals("bone", rootMotion.get("bone").getAsString());
            assertEquals("xz_yaw", rootMotion.get("mode").getAsString());
            assertEquals(14, keyframes.size());
            var end = keyframes.get(keyframes.size() - 1).getAsJsonObject();
            assertEquals(-2.3125, end.get("z").getAsDouble(), 0.000001);
            assertEquals(cue.get("duration").getAsDouble(),
                    end.get("time").getAsDouble(), 0.000001);
        }
    }
}
