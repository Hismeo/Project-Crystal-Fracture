package org.hismeo.fractureclient.client.weapon;

import com.kaleblangley.haikalat.subsystems.render3d.MeshRenderer;
import com.kaleblangley.haikalat.subsystems.resources.ResourceCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import org.hismeo.crystalfracture.CrystalFracture;
import org.hismeo.crystalfracture.weapon.WeaponAuthority;
import org.hismeo.crystalfracture.weapon.api.MarkerName;
import org.hismeo.crystalfracture.weapon.api.WeaponAssemblies;
import org.hismeo.crystalfracture.weapon.api.WeaponAssembly;
import org.hismeo.fractureclient.FractureClient;
import org.hismeo.haikalathost.api.client.advanced.HaikalatEngineContext;
import org.hismeo.haikalathost.api.client.advanced.HaikalatFrameContext;
import org.hismeo.haikalathost.api.client.advanced.HaikalatReloadContext;
import org.hismeo.haikalathost.api.client.advanced.HaikalatRenderExtension;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Phase-5 client bridge: authoritative Assembly IDs become per-player GLB assemblies. */
public final class PlayerWeaponExtension implements HaikalatRenderExtension {
    private static volatile PlayerWeaponExtension active;

    private final PlayerWeaponOverlayRenderer overlayRenderer = new PlayerWeaponOverlayRenderer();
    private final Map<WeaponAssembly, WeaponPartVisualCatalog> catalogs = new HashMap<>();
    private final Set<WeaponAssembly> rejectedCatalogs = new HashSet<>();
    private final Set<String> reportedRegistryMismatches = new HashSet<>();
    private PlayerWeaponManager manager;
    private ClientLevel activeLevel;
    private ResourceCatalog resources;
    private WeaponPartVisualLibrary visualLibrary;
    private WeaponRenderedAssembly preview;
    private WeaponPartVisualCatalog previewCatalog;
    private final Matrix4f previewProjection = new Matrix4f();
    private final Matrix4f previewView = new Matrix4f();
    private final Matrix4f previewSocket = new Matrix4f();
    private final Matrix4f previewWeaponRoot = new Matrix4f();
    private long definitionGeneration = Long.MIN_VALUE;
    private long resourceGeneration = Long.MIN_VALUE;

    @Override
    public void initialize(HaikalatEngineContext context) {
        active = this;
        refreshContext(context, true);
    }

    @Override
    public void render(HaikalatFrameContext frame) {
        refreshContext(frame, false);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof WeaponPreviewScreen) {
            return;
        }
        closePreview();
        ClientLevel level = minecraft.level;
        if (resources == null || level == null || minecraft.player == null) {
            return;
        }
        ensureWorld(level);
        manager.update(level);
        List<MeshRenderer> renderers = manager.readyRenderers();
        overlayRenderer.render(frame, renderers);
    }

    static void renderPreviewPass(HaikalatFrameContext frame) {
        PlayerWeaponExtension extension = active;
        if (extension == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof WeaponPreviewScreen screen) {
            extension.refreshContext(frame, false);
            extension.renderPreview(frame, screen);
        } else {
            extension.closePreview();
        }
    }

    @Override
    public void resourcesReloaded(HaikalatReloadContext context) {
        refreshContext(context, true);
    }

    @Override
    public void worldClosed(HaikalatEngineContext context) {
        closeWorldObjects();
        closeCatalogs();
        closeVisualLibrary();
    }

    @Override
    public void close(HaikalatEngineContext context) {
        closeWorldObjects();
        closeCatalogs();
        closeVisualLibrary();
        resources = null;
        if (active == this) {
            active = null;
        }
    }

    public static boolean previewAvailable() {
        PlayerWeaponExtension extension = active;
        WeaponAssembly assembly = extension == null ? null : extension.previewAssembly();
        return extension != null
                && assembly != null
                && extension.resources != null
                && extension.previewRegistryCompatible()
                && CrystalFracture.weaponSchemas().compile(assembly).isPresent();
    }

    public static String previewStatus() {
        PlayerWeaponExtension extension = active;
        if (extension == null) {
            return "Haikalat 武器扩展尚未初始化";
        }
        WeaponAssembly assembly = extension.previewAssembly();
        if (assembly == null) {
            return "Weapon Registry 中没有可装配的蓝图";
        }
        boolean editing = Minecraft.getInstance().screen instanceof WeaponPreviewScreen;
        boolean authoritative = !editing && extension.authoritativePreviewAssembly() != null;
        String selection = assembly.parts().values().stream()
                .map(part -> part.value().getPath())
                .collect(Collectors.joining(" + "));
        if (extension.rejectedCatalogs.contains(assembly)) {
            return selection + " · GLB 编译失败（请查看日志）";
        }
        if (!extension.previewRegistryCompatible()) {
            return selection + " · 服务端/客户端 Weapon Registry 不匹配";
        }
        if (editing) {
            return assembly.schema().value().getPath() + " · 调试装配";
        }
        return selection + (authoritative ? " · 服务端权威 Assembly" : " · 等待同步，使用默认预览");
    }

    static boolean markerWorld(UUID playerId, MarkerName marker, Matrix4f destination) {
        PlayerWeaponExtension extension = active;
        return extension != null
                && extension.manager != null
                && extension.manager.markerWorld(playerId, marker, destination);
    }

    private void refreshContext(HaikalatEngineContext context, boolean force) {
        long nextDefinitionGeneration = CrystalFracture.weaponSchemas().generation();
        long nextResourceGeneration = context.resourceGeneration();
        if (!force
                && definitionGeneration == nextDefinitionGeneration
                && resourceGeneration == nextResourceGeneration) {
            resources = context.resources();
            return;
        }
        closeWorldObjects();
        closeCatalogs();
        closeVisualLibrary();
        resources = context.resources();
        definitionGeneration = nextDefinitionGeneration;
        resourceGeneration = nextResourceGeneration;
        FractureClient.LOGGER.info(
                "Weapon client context changed: definitions={}, resources={}",
                definitionGeneration,
                resourceGeneration);
    }

    private WeaponPartVisualCatalog catalogFor(WeaponAssembly assembly) {
        WeaponPartVisualCatalog existing = catalogs.get(assembly);
        if (existing != null || rejectedCatalogs.contains(assembly) || resources == null) {
            return existing;
        }
        var logical = CrystalFracture.weaponSchemas().compile(assembly);
        if (logical.isEmpty()) {
            rejectedCatalogs.add(assembly);
            FractureClient.LOGGER.error(
                    "Server-authoritative weapon Assembly is incompatible with client definition generation {}: {}",
                    definitionGeneration,
                    assembly);
            return null;
        }
        try {
            WeaponPartVisualCatalog loaded = WeaponPartVisualCatalog.load(
                    logical.orElseThrow(), visualLibrary());
            catalogs.put(assembly, loaded);
            FractureClient.LOGGER.info(
                    "Compiled authoritative weapon visual {} from {}",
                    logical.orElseThrow().contentHash(),
                    assembly);
            return loaded;
        } catch (WeaponVisualCompilationException failure) {
            failure.problems().forEach(problem -> FractureClient.LOGGER.error(
                    "Weapon visual {} slot={} part={} node='{}': {}",
                    problem.errorCode(), problem.slot(), problem.part(),
                    problem.nodeName(), problem.message()));
            rejectedCatalogs.add(assembly);
            return null;
        } catch (RuntimeException failure) {
            rejectedCatalogs.add(assembly);
            FractureClient.LOGGER.error("Could not load authoritative weapon visual " + assembly, failure);
            return null;
        }
    }

    private WeaponPartVisualCatalog catalogFor(
            AbstractClientPlayer player,
            WeaponAssembly assembly
    ) {
        String serverHash = WeaponAuthority.registryHash(player).orElse("");
        String clientHash = CrystalFracture.weaponSchemas().contentHash();
        if (!serverHash.isBlank() && !serverHash.equals(clientHash)) {
            String mismatch = player.getUUID() + ":" + serverHash + ":" + clientHash;
            if (reportedRegistryMismatches.add(mismatch)) {
                FractureClient.LOGGER.error(
                        "Refusing weapon visual for {}: server Registry {} != client Registry {}",
                        player.getUUID(), serverHash, clientHash);
            }
            return null;
        }
        return catalogFor(assembly);
    }

    private void ensureWorld(ClientLevel level) {
        if (activeLevel == level && manager != null) {
            return;
        }
        closeWorldObjects();
        activeLevel = level;
        manager = new PlayerWeaponManager(this::catalogFor);
    }

    private void renderPreview(HaikalatFrameContext frame, WeaponPreviewScreen screen) {
        if (!previewRegistryCompatible()) {
            closePreview();
            return;
        }
        WeaponAssembly selectedAssembly = previewAssembly();
        if (selectedAssembly == null) {
            closePreview();
            return;
        }
        WeaponPartVisualCatalog selectedCatalog = catalogFor(selectedAssembly);
        if (selectedCatalog == null) {
            closePreview();
            return;
        }
        if (preview == null || previewCatalog != selectedCatalog) {
            closePreview();
            previewCatalog = selectedCatalog;
            preview = new WeaponRenderedAssembly(selectedCatalog);
        }
        screen.advance(frame.deltaSeconds());
        float aspect = frame.target().width() / (float) Math.max(1, frame.target().height());
        float halfHeight = 2.25F / screen.zoom();
        previewProjection.setOrtho(
                -halfHeight * aspect,
                halfHeight * aspect,
                -halfHeight,
                halfHeight,
                -20.0F,
                20.0F);
        previewView.identity();
        float horizontalOffset = (screen.previewCenterFraction() - 0.5F)
                * 2.0F * halfHeight * aspect;
        previewSocket.identity()
                .translate(horizontalOffset, -1.15F, 0.0F)
                .rotateX(screen.pitchRadians())
                .rotateY(screen.yawRadians());
        selectedCatalog.weaponRootAtSocket(previewSocket, previewWeaponRoot);
        preview.updateSocket(previewWeaponRoot);
        overlayRenderer.renderPreview(
                frame,
                preview.renderers(),
                previewProjection,
                previewView);
    }

    private WeaponAssembly previewAssembly() {
        if (Minecraft.getInstance().screen instanceof WeaponPreviewScreen screen) {
            return screen.currentAssembly();
        }
        WeaponAssembly authority = authoritativePreviewAssembly();
        return authority == null ? WeaponAssemblies.DEFAULT_SWORD : authority;
    }

    private WeaponAssembly authoritativePreviewAssembly() {
        var player = Minecraft.getInstance().player;
        return player == null ? null : WeaponAuthority.current(player).orElse(null);
    }

    private boolean previewRegistryCompatible() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return true;
        }
        return WeaponAuthority.registryHash(player)
                .map(serverHash -> serverHash.equals(CrystalFracture.weaponSchemas().contentHash()))
                .orElse(true);
    }

    private void closePreview() {
        if (preview != null) {
            preview.close();
            preview = null;
        }
        previewCatalog = null;
    }

    private void closeWorldObjects() {
        closePreview();
        overlayRenderer.reset();
        if (manager != null) {
            manager.close();
            manager = null;
        }
        activeLevel = null;
    }

    private void closeCatalogs() {
        catalogs.values().forEach(WeaponPartVisualCatalog::close);
        catalogs.clear();
        rejectedCatalogs.clear();
        reportedRegistryMismatches.clear();
    }

    private WeaponPartVisualLibrary visualLibrary() {
        if (visualLibrary == null) {
            if (resources == null) {
                throw new IllegalStateException("Weapon resources are unavailable");
            }
            visualLibrary = new WeaponPartVisualLibrary(resources, resourceGeneration);
        }
        return visualLibrary;
    }

    private void closeVisualLibrary() {
        if (visualLibrary != null) {
            visualLibrary.close();
            visualLibrary = null;
        }
    }
}
