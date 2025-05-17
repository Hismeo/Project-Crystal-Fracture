package org.hismeo.crystallib.util;

import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.hismeo.crystallib.CrystalLib;
import org.objectweb.asm.Type;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class ReflectionUtil {
    @SuppressWarnings("unchecked")
    public static <R> List<R> getImplClass(Class<R> interfaceClazz){
        List<R> implClasses = new ArrayList<>();
        Type targetInterface = Type.getType(interfaceClazz);
        for (ModFileScanData scanData : ModList.get().getAllScanData()) {
            for (ModFileScanData.ClassData classData : scanData.getClasses()) {
                if (classData.interfaces().contains(targetInterface)) {
                    try {
                        Class<?> clazz = Class.forName(classData.clazz().getClassName());
                        if (!Modifier.isAbstract(clazz.getModifiers()) && interfaceClazz.isAssignableFrom(clazz)) {
                            R implClass = (R) clazz.getDeclaredConstructor().newInstance();
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
