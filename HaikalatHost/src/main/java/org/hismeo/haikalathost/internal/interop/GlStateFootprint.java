package org.hismeo.haikalathost.internal.interop;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.TreeSet;

/**
 * The finite set of indexed OpenGL bindings an interop call is allowed to touch.
 *
 * <p>The footprint is deliberately explicit. It prevents the Host from querying every slot
 * reported by {@code GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS} on every frame while still allowing a
 * custom integration to extend the audited standard set. A command containing an arbitrary
 * Haikalat {@code custom(...)} callback must extend this footprint for every indexed binding that
 * callback changes.</p>
 */
public final class GlStateFootprint {
    /**
     * The current debug marker only changes the framebuffer, viewport, scissor and clear state.
     */
    public static final GlStateFootprint MINIMAL_PROBE = builder().build();

    /**
     * Binding footprint used by Haikalat 0.20.1's built-in 3D, post-process, UI and preview paths.
     *
     * <p>The source audit found texture/sampler units 0-13, camera UBO binding 0, VFX/glTF SSBO
     * bindings 0/7/8/9 and environment-preprocessing image unit 0. The standard command executor
     * can also select a read buffer during a blit, and its instanced path can change the generic
     * array-buffer binding.</p>
     */
    public static final GlStateFootprint STANDARD_PIPELINE = builder()
            .textureUnits(closedRange(0, 13))
            .uniformBufferBindings(0)
            .storageBufferBindings(0, 7, 8, 9)
            .imageUnits(0)
            .captureArrayBufferBinding()
            .captureReadBufferSelection()
            .build();

    private final int[] textureUnits;
    private final int[] uniformBufferBindings;
    private final int[] storageBufferBindings;
    private final int[] imageUnits;
    private final boolean arrayBufferBinding;
    private final boolean readBufferSelection;

    private GlStateFootprint(Builder builder) {
        textureUnits = toArray(builder.textureUnits);
        uniformBufferBindings = toArray(builder.uniformBufferBindings);
        storageBufferBindings = toArray(builder.storageBufferBindings);
        imageUnits = toArray(builder.imageUnits);
        arrayBufferBinding = builder.arrayBufferBinding;
        readBufferSelection = builder.readBufferSelection;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return builder()
                .textureUnits(textureUnits)
                .uniformBufferBindings(uniformBufferBindings)
                .storageBufferBindings(storageBufferBindings)
                .imageUnits(imageUnits)
                .captureArrayBufferBinding(arrayBufferBinding)
                .captureReadBufferSelection(readBufferSelection);
    }

    public int[] textureUnits() {
        return textureUnits.clone();
    }

    public int[] uniformBufferBindings() {
        return uniformBufferBindings.clone();
    }

    public int[] storageBufferBindings() {
        return storageBufferBindings.clone();
    }

    public int[] imageUnits() {
        return imageUnits.clone();
    }

    public boolean capturesArrayBufferBinding() {
        return arrayBufferBinding;
    }

    public boolean capturesReadBufferSelection() {
        return readBufferSelection;
    }

    boolean hasTextureUnits() {
        return textureUnits.length != 0;
    }

    boolean hasUniformBufferBindings() {
        return uniformBufferBindings.length != 0;
    }

    boolean hasStorageBufferBindings() {
        return storageBufferBindings.length != 0;
    }

    boolean hasImageUnits() {
        return imageUnits.length != 0;
    }

    void validateAgainstLimits(
            int textureUnitLimit,
            int uniformBufferBindingLimit,
            int storageBufferBindingLimit,
            int imageUnitLimit
    ) {
        validateIndexes(textureUnits, textureUnitLimit, "texture/sampler unit");
        validateIndexes(
                uniformBufferBindings,
                uniformBufferBindingLimit,
                "uniform-buffer binding");
        validateIndexes(
                storageBufferBindings,
                storageBufferBindingLimit,
                "shader-storage-buffer binding");
        validateIndexes(imageUnits, imageUnitLimit, "image unit");
    }

    private static void validateIndexes(int[] indexes, int limit, String kind) {
        if (indexes.length == 0) {
            return;
        }
        if (limit <= 0) {
            throw new IllegalStateException("OpenGL reported no " + kind + " slots");
        }
        int highest = indexes[indexes.length - 1];
        if (highest >= limit) {
            throw new IllegalStateException(
                    kind + " " + highest + " exceeds the OpenGL limit " + limit);
        }
    }

    private static int[] toArray(Collection<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int[] closedRange(int first, int last) {
        int[] values = new int[last - first + 1];
        Arrays.setAll(values, index -> first + index);
        return values;
    }

    public static final class Builder {
        private final TreeSet<Integer> textureUnits = new TreeSet<>();
        private final TreeSet<Integer> uniformBufferBindings = new TreeSet<>();
        private final TreeSet<Integer> storageBufferBindings = new TreeSet<>();
        private final TreeSet<Integer> imageUnits = new TreeSet<>();
        private boolean arrayBufferBinding;
        private boolean readBufferSelection;

        private Builder() {
        }

        public Builder textureUnits(int... units) {
            addAll(textureUnits, units, "texture/sampler unit");
            return this;
        }

        public Builder uniformBufferBindings(int... bindings) {
            addAll(uniformBufferBindings, bindings, "uniform-buffer binding");
            return this;
        }

        public Builder storageBufferBindings(int... bindings) {
            addAll(storageBufferBindings, bindings, "shader-storage-buffer binding");
            return this;
        }

        public Builder imageUnits(int... units) {
            addAll(imageUnits, units, "image unit");
            return this;
        }

        public Builder captureArrayBufferBinding() {
            return captureArrayBufferBinding(true);
        }

        public Builder captureArrayBufferBinding(boolean capture) {
            arrayBufferBinding = capture;
            return this;
        }

        public Builder captureReadBufferSelection() {
            return captureReadBufferSelection(true);
        }

        public Builder captureReadBufferSelection(boolean capture) {
            readBufferSelection = capture;
            return this;
        }

        public GlStateFootprint build() {
            return new GlStateFootprint(this);
        }

        private static void addAll(TreeSet<Integer> target, int[] values, String kind) {
            Objects.requireNonNull(values, kind + " indexes");
            for (int value : values) {
                if (value < 0) {
                    throw new IllegalArgumentException(kind + " must be non-negative: " + value);
                }
                target.add(value);
            }
        }
    }
}
