package org.hismeo.haikalathost.api.content;

import net.minecraft.resources.ResourceLocation;

@FunctionalInterface
public interface HaikalatAssetRegistrar {
    HaikalatAssetDefinition register(ResourceLocation id);
}
