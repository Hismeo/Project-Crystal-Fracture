package org.hismeo.actionguide.api.runtime;

public record ActionInstanceId(long value) implements Comparable<ActionInstanceId> {
    public ActionInstanceId {
        if (value <= 0) {
            throw new IllegalArgumentException("action instance id must be positive");
        }
    }

    @Override
    public int compareTo(ActionInstanceId other) {
        return Long.compare(value, other.value);
    }

    @Override
    public String toString() {
        return Long.toUnsignedString(value);
    }
}
