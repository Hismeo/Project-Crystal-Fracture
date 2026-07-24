package org.hismeo.haikalathost.client.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FrameSnapshotMailboxTest {
    @Test
    void publicationIsLatestWinsAndNeverWaitsForIntermediateSnapshots() {
        FrameSnapshotMailbox<String> mailbox = new FrameSnapshotMailbox<>();
        mailbox.publish("old");
        long latestSequence = mailbox.publish("latest");

        FrameSnapshotMailbox.Published<String> latest = mailbox.latestAfter(0L);

        assertEquals(latestSequence, latest.sequence());
        assertEquals("latest", latest.snapshot());
        assertNull(mailbox.latestAfter(latestSequence));
    }
}
