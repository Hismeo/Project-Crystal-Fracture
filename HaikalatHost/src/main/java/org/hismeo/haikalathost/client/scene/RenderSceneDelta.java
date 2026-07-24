package org.hismeo.haikalathost.client.scene;

import java.util.List;
import java.util.Objects;

public record RenderSceneDelta(List<Operation> operations) {
    public RenderSceneDelta {
        operations = List.copyOf(operations);
    }

    public static RenderSceneDelta empty() {
        return new RenderSceneDelta(List.of());
    }

    public sealed interface Operation permits Add, Update, Remove {
    }

    public record Add(long sourceKey, RenderObject object) implements Operation {
        public Add {
            Objects.requireNonNull(object, "object");
        }
    }

    public record Update(RenderObjectId id, RenderObject object) implements Operation {
        public Update {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(object, "object");
        }
    }

    public record Remove(RenderObjectId id) implements Operation {
        public Remove {
            Objects.requireNonNull(id, "id");
        }
    }
}
