package org.hismeo.bendsanimator.client.impl;

import net.minecraft.client.model.geom.ModelPart;

public class BendsModelPart {
    private int x;
    private int y;
    private int z;
    public int xOld;
    public int yOld;
    public int zOld;
    private int xRot;
    private int yRot;
    private int zRot;
    public int xRotOld;
    public int yRotOld;
    public int zRotOld;
    public BendsModelPart copyFromModelPart(ModelPart origin){
        return this;
    }
}
