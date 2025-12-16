package org.hismeo.fractureclient.client.datagen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.hismeo.crystalfracture.CrystalFracture;

import java.util.Arrays;
import java.util.stream.Collectors;

public class EnglishLanguageProvider extends LanguageProvider {
    public EnglishLanguageProvider(PackOutput output) {
        super(output, CrystalFracture.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        //TODO
    }

    private void batchCase(DeferredHolder<?,?> object) {
        add(object.getId().toLanguageKey(object.getKey().registry().getPath()), toTitleCase(object));
    }

    private static String toTitleCase(DeferredHolder<?,?> object) {
        return toTitleCase(object.getId().getPath());
    }

    private static String toTitleCase(String raw) {
        return Arrays.stream(raw.split("_"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }
}
