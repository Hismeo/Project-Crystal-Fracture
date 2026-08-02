package org.hismeo.fractureclient.client.render.octopath;

import org.joml.Vector3f;

import java.util.List;

/**
 * CPU-side values sampled from Minecraft once per post-processing frame.
 */
record OctopathFrameState(
        Vector3f skyColor,
        float daylight,
        float twilight,
        float rain,
        float thunder,
        Vector3f focusPosition,
        float focusDistance,
        OctopathToneProfile toneProfile,
        List<OctopathLocalLight> localLights,
        List<OctopathWaterMist> waterMists,
        List<OctopathEntityGroundShadow> entityGroundShadows,
        List<OctopathSunlightFilter> sunlightFilters,
        OctopathDirectionalShadowSnapshot directionalShadow,
        List<OctopathPointLightShadowSnapshot> localLightShadows
) {
    OctopathFrameState {
        skyColor = new Vector3f(skyColor);
        focusPosition = new Vector3f(focusPosition);
        toneProfile = toneProfile == null
                ? OctopathToneProfile.forPreset(OctopathTonePreset.DAY)
                : toneProfile;
        localLights = List.copyOf(localLights);
        waterMists = List.copyOf(waterMists);
        entityGroundShadows = List.copyOf(entityGroundShadows);
        sunlightFilters = sunlightFilters == null
                ? List.of()
                : List.copyOf(sunlightFilters);
        directionalShadow = directionalShadow == null
                ? OctopathDirectionalShadowSnapshot.unavailable()
                : directionalShadow;
        localLightShadows = localLightShadows == null
                ? List.of()
                : List.copyOf(localLightShadows);
    }
}
