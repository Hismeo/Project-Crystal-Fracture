package org.hismeo.bendsanimator.client.api;

import net.minecraft.client.model.geom.builders.PartDefinition;

import java.util.Map;

public interface IAnimator {
    void setX(int x);
    void setY(int y);
    void setZ(int z);
    void setChildren();
    Map<String, PartDefinition> getChildren();
}
