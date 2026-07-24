package org.hismeo.haikalathost.client.extraction;

import org.hismeo.haikalathost.client.submission.PassKey;

import java.util.Arrays;
import java.util.Objects;

/** Fixed-capacity, allocation-free call-site context for direct BufferUploader captures. */
final class DirectPassOverrideStack {
    private final PassKey[] entries;
    private int depth;

    DirectPassOverrideStack(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        entries = new PassKey[capacity];
    }

    void push(PassKey pass) {
        Objects.requireNonNull(pass, "pass");
        if (depth == entries.length) {
            throw new IllegalStateException("Direct draw pass override stack overflow");
        }
        entries[depth++] = pass;
    }

    void pop(PassKey expected) {
        Objects.requireNonNull(expected, "expected");
        if (depth == 0) {
            throw new IllegalStateException("Direct draw pass override stack underflow");
        }
        PassKey actual = entries[--depth];
        entries[depth] = null;
        if (actual != expected) {
            clear();
            throw new IllegalStateException(
                    "Direct draw pass override mismatch: expected " + expected + ", found " + actual);
        }
    }

    PassKey current() {
        return depth == 0 ? null : entries[depth - 1];
    }

    void clear() {
        Arrays.fill(entries, null);
        depth = 0;
    }
}
