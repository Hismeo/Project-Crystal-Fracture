package org.hismeo.fractureclient.client.render.octopath;

import com.kaleblangley.haikalat.subsystems.render3d.ExternalCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.hismeo.fractureclient.client.config.OctopathVisualConfig;
import org.hismeo.fractureclient.client.control.CameraModeController;
import org.hismeo.haikalathost.api.client.advanced.HaikalatEngineContext;
import org.hismeo.haikalathost.api.client.advanced.HaikalatFrameContext;
import org.hismeo.haikalathost.api.client.advanced.HaikalatReloadContext;
import org.hismeo.haikalathost.api.client.advanced.HaikalatRenderExtension;
import org.joml.Vector3f;

import java.util.List;

/**
 * HaikalatHost extension that applies the HD-2D-inspired presentation after Minecraft renders a
 * world frame. It only borrows the current Minecraft target and owns all intermediate resources.
 */
public final class OctopathVisualExtension implements HaikalatRenderExtension {
    private static final int MAX_LOCAL_LIGHT_SHADOWS = OctopathPointLightShadowMap.MAX_LIGHTS;

    private final OctopathLocalLightScanner lightScanner = new OctopathLocalLightScanner();
    private final OctopathWaterMistScanner waterMistScanner = new OctopathWaterMistScanner();
    private final OctopathEntityGroundShadowScanner entityGroundShadowScanner = new OctopathEntityGroundShadowScanner();
    private final OctopathDirectionalShadowScanner directionalShadowScanner = new OctopathDirectionalShadowScanner();
    private final OctopathPointLightShadowScanner pointLightShadowScanner = new OctopathPointLightShadowScanner();
    private final OctopathSunlightFilterScanner sunlightFilterScanner = new OctopathSunlightFilterScanner();
    private final OctopathToneSelector toneSelector = new OctopathToneSelector();

    private OctopathPostProcessor postProcessor;
    private OctopathDirectionalShadowMap directionalShadowMap;
    private long directionalShadowMapGameTime = Long.MIN_VALUE;
    private int directionalShadowMapResolution = -1;
    private OctopathDirectionalShadowSnapshot directionalShadowSnapshot =
            OctopathDirectionalShadowSnapshot.unavailable();
    private OctopathPointLightShadowMap pointLightShadowMap;
    private int pointLightShadowAtlasResolution = -1;
    private long pointLightShadowBudgetGameTime = Long.MIN_VALUE;
    private int pointLightShadowUpdatesThisTick;
    private int pointLightShadowRefreshCursor;
    private final PointLightShadowCache[] pointLightShadowCaches = createPointLightShadowCaches();

    @Override
    public void render(HaikalatFrameContext frame) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!CameraModeController.isOrthographic() || !OctopathVisualConfig.enabled) {
            restoreNativeEntityShadows(minecraft);
            closeProcessor();
            closeDirectionalShadowMap();
            closePointLightShadowMap();
            return;
        }
        applyNativeEntityShadowPolicy(minecraft);
        if (!frame.target().isRenderable() || frame.target().depth().isEmpty()) {
            return;
        }

        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }

        if (postProcessor == null) {
            postProcessor = new OctopathPostProcessor();
        }
        postProcessor.render(frame, captureFrameState(frame, level, player));
    }

    @Override
    public void resourcesReloaded(HaikalatReloadContext context) {
        // Rebuild classpath shaders and targets on the next frame, keeping reload ownership simple.
        closeProcessor();
        closeDirectionalShadowMap();
        closePointLightShadowMap();
    }

    @Override
    public void worldClosed(HaikalatEngineContext context) {
        lightScanner.reset();
        waterMistScanner.reset();
        entityGroundShadowScanner.reset();
        directionalShadowScanner.reset();
        sunlightFilterScanner.reset();
        toneSelector.reset();
        restoreNativeEntityShadows(Minecraft.getInstance());
        closeProcessor();
        closeDirectionalShadowMap();
        closePointLightShadowMap();
    }

    @Override
    public void close(HaikalatEngineContext context) {
        lightScanner.reset();
        waterMistScanner.reset();
        entityGroundShadowScanner.reset();
        directionalShadowScanner.reset();
        sunlightFilterScanner.reset();
        toneSelector.reset();
        restoreNativeEntityShadows(Minecraft.getInstance());
        closeProcessor();
        closeDirectionalShadowMap();
        closePointLightShadowMap();
    }

    private OctopathFrameState captureFrameState(
            HaikalatFrameContext frame,
            ClientLevel level,
            LocalPlayer player
    ) {ExternalCamera camera = frame.camera();
    float partialTick = clamp(camera.partialTick(), 0.0F, 1.0F);
    Vector3f cameraPosition = camera.position();
    Vec3 minecraftCameraPosition = new Vec3(
            cameraPosition.x,
            cameraPosition.y,
            cameraPosition.z);
    Vec3 sky = level.getSkyColor(minecraftCameraPosition, partialTick);
    Vec3 playerPosition = player.getPosition(partialTick);
    Vector3f focusPosition = new Vector3f(
            (float) playerPosition.x,
            (float) (playerPosition.y + player.getBbHeight() * 0.45D),
            (float) playerPosition.z);

    double worldTime = Math.floorMod(level.getDayTime(), 24_000L) / 24_000.0D;
    float sunHeight = (float) Math.sin(worldTime * Math.PI * 2.0D);
    float daylight = Math.max(0.0F, sunHeight);
    float twilight = 1.0F - smoothstep(0.06F, 0.48F, Math.abs(sunHeight));
    float rain = clamp(level.getRainLevel(partialTick), 0.0F, 1.0F);
    float thunder = clamp(level.getThunderLevel(partialTick), 0.0F, 1.0F);
    float focusDistance = Math.max(1.0F, cameraPosition.distance(focusPosition));
    OctopathToneProfile toneProfile = toneSelector.select(
            level,
            player,
            daylight,
            twilight,
            OctopathVisualConfig.tonePresetMode,
            OctopathVisualConfig.tonePresetTransitionTicks);
    List<OctopathLocalLight> lights = lightScanner.scan(
            level,
            player,
            camera,
            OctopathVisualConfig.localLightScanRadius,
            OctopathVisualConfig.localLightScanIntervalTicks);
    OctopathDirectionalShadowSnapshot directionalShadow = captureDirectionalShadow(
            frame,
            level,
            player,
            camera,
            partialTick,
            daylight);
    List<OctopathPointLightShadowSnapshot> localLightShadows = capturePointLightShadows(
            frame,
            level,
            player,
            lights);
    List<OctopathSunlightFilter> sunlightFilters;
    if (OctopathVisualConfig.volumetricSunlightEnabled
            && OctopathVisualConfig.stainedGlassSunlightTintStrength > 0.0001F
            && daylight > 0.0125F) {
        sunlightFilters = sunlightFilterScanner.scan(level, player);
    } else {
        sunlightFilterScanner.reset();
        sunlightFilters = List.of();
    }
    List<OctopathWaterMist> waterMists;
        if(OctopathVisualConfig.waterMistEnabled)

    {
        waterMists = waterMistScanner.scan(
                level,
                player,
                camera,
                OctopathVisualConfig.waterMistScanIntervalTicks,
                OctopathVisualConfig.waterMistPatchRadius);
    } else

    {
        waterMistScanner.reset();
        waterMists = List.of();
    }

    List<OctopathEntityGroundShadow> entityGroundShadows;
        if(OctopathVisualConfig.entityGroundShadows
                &&!OctopathVisualConfig.directionalShadowMapEnabled
                &&OctopathVisualConfig.suppressNativeEntityShadows)

    {
        entityGroundShadows = entityGroundShadowScanner.scan(
                level,
                player,
                camera,
                partialTick,
                OctopathVisualConfig.entityGroundShadowMaximumDrop,
                OctopathVisualConfig.entityGroundShadowRadius);
    } else

    {
        entityGroundShadowScanner.reset();
        entityGroundShadows = List.of();
    }

        return new

    OctopathFrameState(
                new Vector3f((float) sky.x,(float)sky.y,(float)sky.z),
    daylight,
    twilight,
    rain,
    thunder,
    focusPosition,
    focusDistance,
    toneProfile,
    lights,
    waterMists,
    entityGroundShadows,
    sunlightFilters,
    directionalShadow,
    localLightShadows);
}

private OctopathDirectionalShadowSnapshot captureDirectionalShadow(
        HaikalatFrameContext frame,
        ClientLevel level,
        LocalPlayer player,
        ExternalCamera camera,
        float partialTick,
        float daylight
) {
    if (!OctopathVisualConfig.directionalShadowMapEnabled || daylight <= 0.0125F) {
        resetDirectionalShadowCache();
        return OctopathDirectionalShadowSnapshot.unavailable();
    }

    int requestedResolution = OctopathVisualConfig.directionalShadowMapResolution;
    long gameTime = level.getGameTime();
    // Camera rotation does not change the light-space scene. Reuse the texture for the rest
    // of the tick; rebuilding thousands of proxy columns for every render frame can make a
    // high-resolution (2560px+) presentation stall while the player turns the camera.
    if (directionalShadowMap != null
            && directionalShadowMapGameTime == gameTime
            && directionalShadowMapResolution == requestedResolution) {
        return directionalShadowSnapshot;
    }

    OctopathDirectionalShadowFrame shadowFrame = directionalShadowScanner.scan(
            level,
            player,
            camera,
            partialTick,
            OctopathVisualConfig.directionalShadowWorldExtent,
            OctopathVisualConfig.directionalShadowMaximumTerrainColumns,
            OctopathVisualConfig.directionalShadowTerrainScanIntervalTicks,
            requestedResolution);
    if (directionalShadowMap == null) {
        directionalShadowMap = new OctopathDirectionalShadowMap();
    }
    directionalShadowSnapshot = directionalShadowMap.render(
            frame,
            shadowFrame,
            requestedResolution);
    directionalShadowMapGameTime = gameTime;
    directionalShadowMapResolution = requestedResolution;
    return directionalShadowSnapshot;
}

private List<OctopathPointLightShadowSnapshot> capturePointLightShadows(
        HaikalatFrameContext frame,
        ClientLevel level,
        LocalPlayer player,
        List<OctopathLocalLight> lights
) {
    if (!OctopathVisualConfig.localLightShadowsEnabled || lights.isEmpty()) {
        resetPointLightShadowCache();
        return List.of();
    }

    int requestedResolution = OctopathVisualConfig.localLightShadowMapResolution;
    if (pointLightShadowAtlasResolution != requestedResolution) {
        // A reallocated atlas invalidates every old texture id. Keep slots inactive until their
        // new tile has actually been rendered rather than letting stale snapshots sample an
        // empty/reused atlas region.
        resetPointLightShadowCache();
        pointLightShadowAtlasResolution = requestedResolution;
    }
    float requestedRange = OctopathVisualConfig.localLightShadowRange;
    int updateInterval = Math.max(1, OctopathVisualConfig.localLightShadowUpdateIntervalTicks);
    int updateBudget = Math.min(
            MAX_LOCAL_LIGHT_SHADOWS,
            Math.max(1, OctopathVisualConfig.localLightShadowUpdatesPerTick));
    long gameTime = level.getGameTime();
    int shadowCount = Math.min(
            Math.min(MAX_LOCAL_LIGHT_SHADOWS, Math.max(1, OctopathVisualConfig.localLightShadowCount)),
            lights.size());
    List<OctopathPointLightShadowSnapshot> snapshots = new java.util.ArrayList<>(shadowCount);
    for (int index = 0; index < shadowCount; index++) {
        OctopathLocalLight selectedLight = lights.get(index);
        PointLightShadowCache cache = pointLightShadowCaches[index];
        snapshots.add(cache.matchesSource(
                selectedLight.stableKey(),
                selectedLight.position(),
                requestedResolution,
                requestedRange)
                ? cache.snapshot
                : OctopathPointLightShadowSnapshot.unavailable());
    }
    for (int index = shadowCount; index < MAX_LOCAL_LIGHT_SHADOWS; index++) {
        pointLightShadowCaches[index].reset();
    }

    if (pointLightShadowBudgetGameTime != gameTime) {
        pointLightShadowBudgetGameTime = gameTime;
        pointLightShadowUpdatesThisTick = 0;
    }
    int cursor = Math.floorMod(pointLightShadowRefreshCursor, shadowCount);
    int examined = 0;
    while (examined < shadowCount && pointLightShadowUpdatesThisTick < updateBudget) {
        int index = cursor;
        cursor = (cursor + 1) % shadowCount;
        examined++;
        OctopathLocalLight selectedLight = lights.get(index);
        Vector3f selectedPosition = selectedLight.position();
        PointLightShadowCache cache = pointLightShadowCaches[index];
        if (cache.isFresh(
                selectedLight.stableKey(),
                selectedPosition,
                requestedResolution,
                requestedRange,
                gameTime,
                updateInterval)) {
            continue;
        }
        OctopathPointLightShadowFrame shadowFrame = pointLightShadowScanner.scan(
                level,
                player,
                selectedLight,
                requestedRange);
        if (pointLightShadowMap == null) {
            pointLightShadowMap = new OctopathPointLightShadowMap();
        }
        cache.snapshot = pointLightShadowMap.render(
                frame,
                shadowFrame,
                requestedResolution,
                index);
        cache.gameTime = gameTime;
        cache.resolution = requestedResolution;
        cache.range = requestedRange;
        cache.stableKey = selectedLight.stableKey();
        cache.position = new Vector3f(selectedPosition);
        snapshots.set(index, cache.snapshot);
        pointLightShadowUpdatesThisTick++;
    }
    pointLightShadowRefreshCursor = cursor;
    return List.copyOf(snapshots);
}

private void closeProcessor() {
    if (postProcessor != null) {
        postProcessor.close();
        postProcessor = null;
    }
}

private void closeDirectionalShadowMap() {
    if (directionalShadowMap != null) {
        directionalShadowMap.close();
        directionalShadowMap = null;
    }
    resetDirectionalShadowCache();
}

private void closePointLightShadowMap() {
    if (pointLightShadowMap != null) {
        pointLightShadowMap.close();
        pointLightShadowMap = null;
    }
    pointLightShadowAtlasResolution = -1;
    resetPointLightShadowCache();
}

private void resetDirectionalShadowCache() {
    directionalShadowMapGameTime = Long.MIN_VALUE;
    directionalShadowMapResolution = -1;
    directionalShadowSnapshot = OctopathDirectionalShadowSnapshot.unavailable();
}

private void resetPointLightShadowCache() {
    for (PointLightShadowCache cache : pointLightShadowCaches) {
        cache.reset();
    }
    pointLightShadowBudgetGameTime = Long.MIN_VALUE;
    pointLightShadowUpdatesThisTick = 0;
    pointLightShadowRefreshCursor = 0;
}

private static PointLightShadowCache[] createPointLightShadowCaches() {
    PointLightShadowCache[] caches = new PointLightShadowCache[MAX_LOCAL_LIGHT_SHADOWS];
    for (int index = 0; index < caches.length; index++) {
        caches[index] = new PointLightShadowCache();
    }
    return caches;
}

private static final class PointLightShadowCache {
    /**
     * A scanner cluster has a stable grid key even when its weighted centre shifts by a few
     * centimetres as nearby emissive blocks enter/leave the scan footprint.  Keeping this key
     * stops a valid atlas tile from being discarded for harmless sub-block motion.
     */
    private static final float SOURCE_POSITION_TOLERANCE_SQUARED = 0.0625F;

    private long gameTime = Long.MIN_VALUE;
    private long stableKey = Long.MIN_VALUE;
    private int resolution = -1;
    private float range = Float.NaN;
    private Vector3f position;
    private OctopathPointLightShadowSnapshot snapshot = OctopathPointLightShadowSnapshot.unavailable();

    private boolean isFresh(
            long requestedStableKey,
            Vector3f requestedPosition,
            int requestedResolution,
            float requestedRange,
            long requestedGameTime,
            int updateInterval
    ) {
        return matchesSource(requestedStableKey, requestedPosition, requestedResolution, requestedRange)
                && requestedGameTime >= gameTime
                && requestedGameTime - gameTime < updateInterval;
    }

    private boolean matchesSource(
            long requestedStableKey,
            Vector3f requestedPosition,
            int requestedResolution,
            float requestedRange
    ) {
        return snapshot.available()
                && stableKey == requestedStableKey
                && position != null
                && position.distanceSquared(requestedPosition) <= SOURCE_POSITION_TOLERANCE_SQUARED
                && resolution == requestedResolution
                && Math.abs(range - requestedRange) <= 0.001F;
    }

    private void reset() {
        gameTime = Long.MIN_VALUE;
        stableKey = Long.MIN_VALUE;
        resolution = -1;
        range = Float.NaN;
        position = null;
        snapshot = OctopathPointLightShadowSnapshot.unavailable();
    }

}

/**
 * The vanilla entity renderer writes a hard, circular shadow decal into the final scene. It
 * reads as a black sticker from the orthographic HD-2D camera and cannot be isolated safely
 * after the fact, so suppress it while this presentation extension owns the composition.
 */
private static void applyNativeEntityShadowPolicy(Minecraft minecraft) {
    boolean suppressNative = OctopathVisualConfig.suppressNativeEntityShadows
            && (OctopathVisualConfig.entityGroundShadows
            || OctopathVisualConfig.directionalShadowMapEnabled);
    boolean renderNativeShadows = !suppressNative
            && minecraft.options.entityShadows().get();
    minecraft.getEntityRenderDispatcher().setRenderShadow(renderNativeShadows);
}

private static void restoreNativeEntityShadows(Minecraft minecraft) {
    minecraft.getEntityRenderDispatcher().setRenderShadow(minecraft.options.entityShadows().get());
}

private static float clamp(float value, float min, float max) {
    if (!Float.isFinite(value)) {
        return min;
    }
    return Math.max(min, Math.min(max, value));
}

private static float smoothstep(float edge0, float edge1, float value) {
    float t = clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
    return t * t * (3.0F - 2.0F * t);
}
}
