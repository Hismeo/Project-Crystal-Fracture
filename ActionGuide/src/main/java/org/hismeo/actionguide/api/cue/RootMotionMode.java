package org.hismeo.actionguide.api.cue;

public enum RootMotionMode {
    XZ_YAW("xz_yaw"),
    XYZ_YAW("xyz_yaw"),
    FULL("full");

    private final String serializedName;

    RootMotionMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static RootMotionMode parse(String value) {
        for (RootMotionMode mode : values()) {
            if (mode.serializedName.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("invalid root motion mode: " + value);
    }
}
