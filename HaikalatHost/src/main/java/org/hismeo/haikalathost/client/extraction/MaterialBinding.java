package org.hismeo.haikalathost.client.extraction;

import org.hismeo.haikalathost.client.material.MaterialKey;
import org.hismeo.haikalathost.client.submission.PassKey;

import java.util.Objects;

public record MaterialBinding(PassKey pass, MaterialKey material, int pipelineId) {
    public MaterialBinding {
        Objects.requireNonNull(pass, "pass");
        Objects.requireNonNull(material, "material");
        if (pipelineId < 0) throw new IllegalArgumentException("pipelineId must not be negative");
    }
}
