package org.hismeo.fractureclient.client.datagen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.hismeo.crystalfracture.CrystalFracture;

public class ChineseLanguageProvider extends LanguageProvider {
    public ChineseLanguageProvider(PackOutput output) {
        super(output, CrystalFracture.MODID, "zh_cn");
    }

    @Override
    protected void addTranslations() {
        //TODO
    }

    private void objectLang(DeferredHolder<?,?> object, String value) {
        add(key(object), value);
    }

    private String key(DeferredHolder<?,?> object) {
        return object.getId().toLanguageKey(object.getKey().registry().getPath());
    }
}
