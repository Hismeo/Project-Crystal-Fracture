package org.hismeo.crystallib.api;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// TODO 将枪械的新轨迹应用于此
/**
 * 存储一个Vec3列表，用于轨迹运算
 */
public interface TrailSaver {
    List<Vec3> trail = new ArrayList<>();

    default List<Vec3> getPastPositions(){
        return trail;
    }

    void savePos();
}