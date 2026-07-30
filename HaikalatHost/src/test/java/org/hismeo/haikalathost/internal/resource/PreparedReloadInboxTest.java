package org.hismeo.haikalathost.internal.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreparedReloadInboxTest {
    @Test
    void keepsOnlyTheLatestPreparedGeneration() {
        PreparedReloadInbox inbox = new PreparedReloadInbox();
        long first = inbox.begin();
        long second = inbox.begin();

        inbox.enqueue(second);
        inbox.enqueue(first);

        assertEquals(second, inbox.newerThan(0L).orElseThrow());
        assertTrue(inbox.newerThan(second).isEmpty());
    }

    @Test
    void rejectsUnknownAndInvalidGenerations() {
        PreparedReloadInbox inbox = new PreparedReloadInbox();
        long known = inbox.begin();

        assertThrows(IllegalArgumentException.class, () -> inbox.enqueue(0L));
        assertThrows(IllegalArgumentException.class, () -> inbox.enqueue(known + 1L));
        assertThrows(IllegalArgumentException.class, () -> inbox.newerThan(-1L));
    }
}
