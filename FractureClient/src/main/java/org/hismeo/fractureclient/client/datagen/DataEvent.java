package org.hismeo.fractureclient.client.datagen;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.hismeo.fractureclient.FractureClient;

//MODID 大部分使用CrystalFracture
@EventBusSubscriber(modid = FractureClient.MODID)
public class DataEvent {
    @SubscribeEvent
    public static void data(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        var lookupProvider = event.getLookupProvider();

        if (!event.includeClient()) return;
        generator.addProvider(true, new ChineseLanguageProvider(packOutput));
        generator.addProvider(true, new EnglishLanguageProvider(packOutput));
    }
}
