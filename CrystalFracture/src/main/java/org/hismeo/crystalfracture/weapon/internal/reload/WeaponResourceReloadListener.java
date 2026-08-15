package org.hismeo.crystalfracture.weapon.internal.reload;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.hismeo.crystalfracture.CrystalFracture;
import org.hismeo.crystalfracture.weapon.WeaponAuthority;
import org.hismeo.crystalfracture.weapon.internal.registry.WeaponRegistryManager;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/** Server data-pack reload listener. This class has no client or renderer dependencies. */
public final class WeaponResourceReloadListener
        extends SimplePreparableReloadListener<WeaponResourceBatchResult> {
    private final WeaponRegistryManager registry;
    private final WeaponResourceBatchLoader loader = new WeaponResourceBatchLoader();

    public WeaponResourceReloadListener(WeaponRegistryManager registry) {
        this.registry = registry;
    }

    @Override
    protected WeaponResourceBatchResult prepare(
            ResourceManager resources,
            ProfilerFiller profiler
    ) {
        Map<ResourceLocation, String> json = new TreeMap<>();
        resources.listResources(
                        WeaponResourceBatchLoader.ROOT,
                        WeaponResourceBatchLoader::isWeaponDefinition)
                .forEach((id, resource) -> {
                    try (var reader = resource.openAsReader()) {
                        StringBuilder text = new StringBuilder();
                        char[] buffer = new char[8192];
                        for (int read; (read = reader.read(buffer)) >= 0; ) {
                            text.append(buffer, 0, read);
                        }
                        json.put(id, text.toString());
                    } catch (IOException failure) {
                        throw new IllegalStateException("Could not read weapon definition " + id, failure);
                    }
                });
        return loader.load(json);
    }

    @Override
    protected void apply(
            WeaponResourceBatchResult prepared,
            ResourceManager resources,
            ProfilerFiller profiler
    ) {
        prepared.problems().forEach(problem -> {
            String detail = "{} {} {} [{}] {}";
            if (problem.severity().name().equals("ERROR")) {
                CrystalFracture.LOGGER.error(detail,
                        problem.resourceId(), problem.jsonPath(), problem.severity(),
                        problem.errorCode(), problem.message());
            } else {
                CrystalFracture.LOGGER.warn(detail,
                        problem.resourceId(), problem.jsonPath(), problem.severity(),
                        problem.errorCode(), problem.message());
            }
        });
        if (prepared.snapshot().isEmpty()) {
            CrystalFracture.LOGGER.error(
                    "Weapon definitions reload rejected; retaining generation {}",
                    registry.generation());
            return;
        }
        long generation = registry.publish(prepared.snapshot().orElseThrow());
        CrystalFracture.LOGGER.info(
                "Published weapon definition generation {} ({} types, {} parts, {} schemas)",
                generation,
                registry.partTypes().size(),
                registry.parts().size(),
                registry.schemas().size());
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.getPlayerList().getPlayers().forEach(WeaponAuthority::ensureAndSync);
        }
    }
}
