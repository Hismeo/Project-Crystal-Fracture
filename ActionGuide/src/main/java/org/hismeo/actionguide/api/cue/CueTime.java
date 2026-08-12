package org.hismeo.actionguide.api.cue;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record CueTime(long micros) implements Comparable<CueTime> {
    public static final CueTime ZERO = new CueTime(0);
    public static final CueTime SERVER_TICK = new CueTime(50_000);
    private static final BigDecimal MICROS_PER_SECOND = BigDecimal.valueOf(1_000_000);

    public CueTime {
        if (micros < 0) {
            throw new IllegalArgumentException("cue time must not be negative");
        }
    }

    public static CueTime fromSeconds(BigDecimal seconds) {
        if (seconds == null || seconds.signum() < 0) {
            throw new IllegalArgumentException("seconds must be finite and non-negative");
        }
        BigDecimal normalized = seconds.setScale(6, RoundingMode.HALF_UP);
        return new CueTime(normalized.multiply(MICROS_PER_SECOND).longValueExact());
    }

    public static CueTime fromSeconds(String seconds) {
        return fromSeconds(new BigDecimal(seconds));
    }

    public static CueTime fromSeconds(double seconds) {
        if (!Double.isFinite(seconds)) {
            throw new IllegalArgumentException("seconds must be finite");
        }
        return fromSeconds(BigDecimal.valueOf(seconds));
    }

    public BigDecimal seconds() {
        return BigDecimal.valueOf(micros, 6);
    }

    public CueTime plus(CueTime other) {
        return new CueTime(Math.addExact(micros, other.micros));
    }

    public CueTime minus(CueTime other) {
        return new CueTime(Math.subtractExact(micros, other.micros));
    }

    @Override
    public int compareTo(CueTime other) {
        return Long.compare(micros, other.micros);
    }

    public boolean isBefore(CueTime other) {
        return compareTo(other) < 0;
    }

    public boolean isBeforeOrEqual(CueTime other) {
        return compareTo(other) <= 0;
    }

    @Override
    public String toString() {
        return seconds().toPlainString() + "s";
    }
}
