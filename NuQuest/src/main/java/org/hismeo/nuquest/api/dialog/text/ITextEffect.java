package org.hismeo.nuquest.api.dialog.text;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.hismeo.nuquest.NuQuest;
import org.hismeo.nuquest.core.dialog.text.effect.NoneEffect;
import org.objectweb.asm.Type;

import java.lang.reflect.Modifier;
import java.util.ServiceLoader;

public interface ITextEffect {
    void effectApply(GuiGraphics guiGraphics, Component text, float partialTick, int textHeight, int textWeight);
    String getEffect();

    static ITextEffect getEffect(String name){
        Type targetInterface = Type.getType(ITextEffect.class);
        for (ModFileScanData scanData : ModList.get().getAllScanData()) {
            for (ModFileScanData.ClassData classData : scanData.getClasses()) {
                if (classData.interfaces().contains(targetInterface)) {
                    try {
                        Class<?> clazz = Class.forName(classData.clazz().getClassName());
                        if (!Modifier.isAbstract(clazz.getModifiers()) && ITextEffect.class.isAssignableFrom(clazz)) {
                            ITextEffect effect = (ITextEffect) clazz.getDeclaredConstructor().newInstance();
                            if (effect.getEffect().equals(name)) {
                                return effect;
                            }
                        }
                    } catch (Exception e) {
                        NuQuest.LOGGER.error("Failed to load ITextEffect: {}", classData.clazz().getClassName(), e);
                    }
                }
            }
        }
        return new NoneEffect();
    }
}
