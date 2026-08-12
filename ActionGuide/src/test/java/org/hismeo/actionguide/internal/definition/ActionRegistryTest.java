package org.hismeo.actionguide.internal.definition;

import org.hismeo.actionguide.api.action.ActionDefinition;
import org.hismeo.actionguide.api.action.ActionId;
import org.hismeo.actionguide.api.cue.CombatCueDefinition;
import org.hismeo.actionguide.api.cue.CombatCueId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActionRegistryTest {
    @Test
    void invalidPublishLeavesPreviousGenerationUntouched() {
        CombatCueDefinition cue = cue();
        ActionDefinition action = action("test:sword", "test:sword_cue", "[]");
        ActionRegistry registry = new ActionRegistry();
        RegistrySnapshot published = registry.publish(List.of(cue), List.of(action));

        ActionDefinition invalid = action("test:invalid", "test:sword_cue", """
                [{"from_section":"all","input_state":"missing","intent":"test:combo",
                  "target_action":"test:missing","mode":"direct"}]
                """);
        assertThrows(DefinitionLoadException.class, () -> registry.publish(List.of(cue), List.of(action, invalid)));
        assertEquals(published, registry.snapshot());
    }

    private static CombatCueDefinition cue() {
        return new CombatCueLoader().load(CombatCueId.parse("test:sword_cue"), """
                {"schema_version":4,"duration":1,"skeleton":{"id":"game:test","version":1},
                 "sections":[{"id":"all","start":0,"end":1}],"events":[],"states":[],
                 "root_motion":{"enabled":false,"mode":"xz_yaw"}}
                """).value();
    }

    private static ActionDefinition action(String id, String cue, String transitions) {
        return new ActionDefinitionLoader().load(ActionId.parse(id), """
                {"cue":"%s","entry_section":"all","transitions":%s}
                """.formatted(cue, transitions));
    }
}
