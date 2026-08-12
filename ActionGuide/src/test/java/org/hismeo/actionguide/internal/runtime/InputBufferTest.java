package org.hismeo.actionguide.internal.runtime;

import org.hismeo.actionguide.api.action.ActionIntentId;
import org.hismeo.actionguide.api.action.ActionIntentRequest;
import org.hismeo.actionguide.api.action.IntentPhase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputBufferTest {
    @Test
    void rejectsDuplicateAndStaleSequencesAndExpiresEntries() {
        InputBuffer buffer = new InputBuffer(2, 2);
        ActionIntentId intent = ActionIntentId.parse("test:light_attack");
        assertEquals(InputOfferResult.ACCEPTED, buffer.offer(new ActionIntentRequest(intent, IntentPhase.PRESS, 2, 0), 10));
        assertEquals(InputOfferResult.DUPLICATE_OR_STALE, buffer.offer(new ActionIntentRequest(intent, IntentPhase.PRESS, 2, 0), 10));
        assertEquals(InputOfferResult.DUPLICATE_OR_STALE, buffer.offer(new ActionIntentRequest(intent, IntentPhase.PRESS, 1, 0), 10));
        buffer.expire(13);
        assertTrue(buffer.snapshot().isEmpty());
    }

    @Test
    void consumesOneEntryAtMostOnce() {
        InputBuffer buffer = new InputBuffer(2, 5);
        ActionIntentId intent = ActionIntentId.parse("test:combo");
        buffer.offer(new ActionIntentRequest(intent, IntentPhase.PRESS, 1, 0), 0);
        assertTrue(buffer.consumeFirst(value -> value.intent().equals(intent), 0).isPresent());
        assertTrue(buffer.consumeFirst(value -> value.intent().equals(intent), 0).isEmpty());
    }
}
