package org.hismeo.nuquest.core.data.dialog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import it.unimi.dsi.fastutil.objects.Object2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceRBTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.hismeo.nuquest.NuQuest;
import org.hismeo.nuquest.core.dialog.context.config.DialogConfig;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DialogConfigLoader extends DialogLoader {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public DialogConfigLoader() {
        super(GSON, "nu_quest/dialog");
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> map, @NotNull ResourceManager resourceManager, ProfilerFiller profilerFiller) {
        var entries = new Object2ObjectRBTreeMap<>(map).reversed().entrySet();
        for (var entry : entries) {
            if (entry.getKey().getPath().equals("dialog_config")) {
                JsonElement configElement = entry.getValue();
                try {
                    DialogConfig dialogConfig = getDialogConfig(configElement);
                    DialogManager.replaceConfig(dialogConfig);
                } catch (NullPointerException e) {
                    NuQuest.LOGGER.error("[DialogReloadListener] Failed to load dialog config - {}", e.getMessage(), e);
                }
            }
        }
        NuQuest.LOGGER.info("[DialogReloadListener] Loaded dialog config.");
    }
}
