package org.hismeo.crystallib.util;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.hismeo.crystallib.CrystalLib;
import org.objectweb.asm.Type;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class ReflectionUtil {
    public static <R, T extends R> List<Class<T>> getImplClass(Class<R> interfaceClazz){
        List<Class<T>> implClasses = new ArrayList<>();
        Type targetInterface = Type.getType(interfaceClazz);
        for (ModFileScanData scanData : ModList.get().getAllScanData()) {
            for (ModFileScanData.ClassData classData : scanData.getClasses()) {
                if (classData.interfaces().contains(targetInterface)) {
                    try {
                        Class<?> clazz = Class.forName(classData.clazz().getClassName());
                        if (!Modifier.isAbstract(clazz.getModifiers()) && interfaceClazz.isAssignableFrom(clazz)) {
                            Class<T> implClass = (Class<T>) clazz.getDeclaredConstructor().newInstance();
                            implClasses.add(implClass);
                        }
                    } catch (Exception e) {
                        CrystalLib.LOGGER.error("Failed to load ITextEffect: {}", classData.clazz().getClassName(), e);
                    }
                }
            }
        }
        return implClasses;
    }
}
