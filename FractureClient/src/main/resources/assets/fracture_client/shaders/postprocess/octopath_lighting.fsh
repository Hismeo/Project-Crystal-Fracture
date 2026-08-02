#version 460 core

in vec2 vUv;
layout(location = 0) out vec4 fragColor;

uniform sampler2D uScene;
uniform sampler2D uDepth;
uniform sampler2D uVolumetricSunlight;
uniform sampler2D uAmbientOcclusion;
uniform mat4 uInverseViewProjection;
uniform vec3 uCameraPosition;
uniform vec3 uSkyColor;
uniform float uDaylight;
uniform float uTwilight;
uniform float uRain;
uniform float uThunder;
uniform float uEffectStrength;
uniform float uVibrance;
uniform vec3 uToneMidTint;
uniform vec3 uToneShadowTint;
uniform vec3 uToneHighlightTint;
uniform vec3 uToneFogTint;
uniform vec3 uToneSkyTint;
uniform float uToneVibranceMultiplier;
uniform float uToneStrength;
uniform float uFogDensity;
uniform float uRainFogBoost;
uniform float uFogMaximumOpacity;
uniform float uWaterMistStrength;
uniform float uWaterMistHeight;
uniform int uWaterMistCount;
uniform vec3 uWaterMistPositions[12];
uniform float uWaterMistRadii[12];
uniform float uWaterMistCoverages[12];
uniform float uContactOcclusionStrength;
uniform float uContactOcclusionRadiusPixels;
uniform sampler2D uDirectionalShadowDepth;
uniform mat4 uDirectionalShadowLightViewProjection;
uniform vec3 uDirectionalShadowLightDirection;
uniform vec2 uDirectionalShadowInverseResolution;
uniform float uDirectionalShadowStrength;
uniform float uDirectionalShadowBias;
uniform float uDirectionalShadowSoftness;
uniform float uEntityGroundShadowStrength;
uniform int uEntityGroundShadowCount;
uniform vec3 uEntityGroundShadowPositions[12];
uniform float uEntityGroundShadowRadii[12];
uniform float uEntityGroundShadowOpacities[12];
uniform vec2 uStageFocusUv;
uniform float uStageSpotlightStrength;
uniform float uStageSpotlightRadius;
uniform float uLocalLightIntensity;
uniform float uLocalLightReach;
uniform float uLocalLightDaylightMultiplier;
const int MAX_LOCAL_LIGHTS = 16;
const int POINT_LIGHT_SHADOW_FACES = 6;
const int POINT_LIGHT_SHADOW_ATLAS_COLUMNS = 12;
uniform int uLocalLightCount;
uniform vec3 uLocalLightPositions[MAX_LOCAL_LIGHTS];
uniform vec3 uLocalLightColors[MAX_LOCAL_LIGHTS];
uniform float uLocalLightPowers[MAX_LOCAL_LIGHTS];
uniform sampler2D uLocalLightShadowAtlas;
uniform int uLocalLightShadowCount;
uniform int uLocalLightShadowActive[MAX_LOCAL_LIGHTS];
uniform vec3 uLocalLightShadowPositions[MAX_LOCAL_LIGHTS];
uniform float uLocalLightShadowRanges[MAX_LOCAL_LIGHTS];
uniform vec2 uLocalLightShadowInverseResolution;
uniform float uLocalLightShadowAtlasTileStride;
uniform float uLocalLightShadowAtlasGutter;
uniform float uLocalLightShadowStrength;
uniform float uLocalLightShadowBias;
uniform float uLocalLightShadowSoftness;

vec3 reconstructWorldPositionAt(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 world = uInverseViewProjection * clip;
    return world.xyz / max(abs(world.w), 0.00001);
}

vec3 reconstructWorldPosition(float depth) {
    return reconstructWorldPositionAt(vUv, depth);
}

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

// Change hue and saturation without sneaking additional exposure into the scene.
vec3 tintPreservingLuminance(vec3 color, vec3 tint, float amount) {
    float originalLuminance = luminance(color);
    vec3 tinted = color * tint;
    float tintedLuminance = max(luminance(tinted), 0.00001);
    tinted *= originalLuminance / tintedLuminance;
    return mix(color, tinted, clamp(amount, 0.0, 1.0));
}

// Boost subdued surface colors much more than already-bright or already-saturated pixels.
vec3 applyVibrance(vec3 color, float amount) {
    float lightness = luminance(color);
    float brightest = max(max(color.r, color.g), color.b);
    float darkest = min(min(color.r, color.g), color.b);
    float chroma = max(brightest - darkest, 0.0);
    float highlightProtection = 1.0 - smoothstep(0.58, 0.98, lightness);
    float mutedProtection = 1.0 - smoothstep(0.28, 0.86, chroma);
    float saturation = 1.0 + amount * highlightProtection * (0.32 + mutedProtection * 0.68);
    return mix(vec3(lightness), color, saturation);
}

const vec2 CONTACT_OFFSETS[8] = vec2[](
    vec2(1.0, 0.0), vec2(0.7071, 0.7071), vec2(0.0, 1.0), vec2(-0.7071, 0.7071),
    vec2(-1.0, 0.0), vec2(-0.7071, -0.7071), vec2(0.0, -1.0), vec2(0.7071, -0.7071)
);

// This is deliberately not a reconstructed-normal AO pass. Final Minecraft depth contains
// leaves, cutouts and entities, so derivatives or fake directional shading turn into dark slabs.
// Instead, only a locally continuous receiver is allowed to see a nearby foreground depth edge.
float conservativeContactOcclusion(vec3 sourceLinear) {
    float requestedStrength = clamp(uContactOcclusionStrength, 0.0, 0.18);
    if (requestedStrength <= 0.0001) {
        return 0.0;
    }

    float sourceLuminance = luminance(sourceLinear);
    // Do not carve a dark halo into emissive/bright blocks, and leave near-black foliage alone.
    float brightSourceGate = 1.0 - smoothstep(0.42, 0.78, sourceLuminance);
    float darkReceiverGate = smoothstep(0.035, 0.14, sourceLuminance);
    if (brightSourceGate * darkReceiverGate <= 0.0001) {
        return 0.0;
    }

    float radiusPixels = clamp(uContactOcclusionRadiusPixels, 0.5, 4.0);
    ivec2 resolution = textureSize(uDepth, 0);
    ivec2 centerTexel = ivec2(vUv * vec2(resolution));
    int viewportMargin = int(ceil(radiusPixels)) + 2;
    if (centerTexel.x < viewportMargin || centerTexel.y < viewportMargin
            || centerTexel.x >= resolution.x - viewportMargin
            || centerTexel.y >= resolution.y - viewportMargin) {
        return 0.0;
    }

    // The one-pixel cross must be on one receiver surface. A block edge, cutout or entity
    // silhouette fails here before it can become a false shadow receiver.
    ivec2 texelRight = centerTexel + ivec2(1, 0);
    ivec2 texelLeft = centerTexel + ivec2(-1, 0);
    ivec2 texelUp = centerTexel + ivec2(0, 1);
    ivec2 texelDown = centerTexel + ivec2(0, -1);
    float centerDepth = texelFetch(uDepth, centerTexel, 0).r;
    float depthRight = texelFetch(uDepth, texelRight, 0).r;
    float depthLeft = texelFetch(uDepth, texelLeft, 0).r;
    float depthUp = texelFetch(uDepth, texelUp, 0).r;
    float depthDown = texelFetch(uDepth, texelDown, 0).r;
    if (centerDepth >= 0.999999 || depthRight >= 0.999999 || depthLeft >= 0.999999
            || depthUp >= 0.999999 || depthDown >= 0.999999) {
        return 0.0;
    }

    vec2 centerUv = (vec2(centerTexel) + vec2(0.5)) / vec2(resolution);
    vec3 centerPosition = reconstructWorldPositionAt(centerUv, centerDepth);
    vec3 right = reconstructWorldPositionAt(
            (vec2(texelRight) + vec2(0.5)) / vec2(resolution), depthRight);
    vec3 left = reconstructWorldPositionAt(
            (vec2(texelLeft) + vec2(0.5)) / vec2(resolution), depthLeft);
    vec3 up = reconstructWorldPositionAt(
            (vec2(texelUp) + vec2(0.5)) / vec2(resolution), depthUp);
    vec3 down = reconstructWorldPositionAt(
            (vec2(texelDown) + vec2(0.5)) / vec2(resolution), depthDown);
    // Measure a pixel footprint at the center depth, rather than from the neighbouring samples.
    // A one-block edge must not inflate this threshold and accidentally qualify as "continuous".
    vec3 sameDepthRight = reconstructWorldPositionAt(
            (vec2(texelRight) + vec2(0.5)) / vec2(resolution), centerDepth);
    vec3 sameDepthUp = reconstructWorldPositionAt(
            (vec2(texelUp) + vec2(0.5)) / vec2(resolution), centerDepth);
    float localPixelScale = max(
            0.0025,
            0.5 * (length(sameDepthRight - centerPosition)
                    + length(sameDepthUp - centerPosition)));
    float receiverJump = max(
            max(length(right - centerPosition), length(left - centerPosition)),
            max(length(up - centerPosition), length(down - centerPosition)));
    float continuityScale = min(localPixelScale, 0.08);
    float continuousReceiver = 1.0 - smoothstep(
            max(0.025, continuityScale * 2.5),
            max(0.12, continuityScale * 6.0),
            receiverJump);
    if (continuousReceiver <= 0.0001) {
        return 0.0;
    }

    float centerCameraDistance = length(centerPosition - uCameraPosition);
    float foregroundBias = max(0.05, localPixelScale * 4.0);
    float foregroundSoftness = max(0.14, localPixelScale * 7.0);
    float occlusion = 0.0;
    for (int index = 0; index < 8; index++) {
        ivec2 tapOffset = ivec2(round(CONTACT_OFFSETS[index] * radiusPixels));
        ivec2 sampleTexel = centerTexel + tapOffset;
        float sampleDepth = texelFetch(uDepth, sampleTexel, 0).r;
        if (sampleDepth >= 0.999999) {
            continue;
        }

        vec2 sampleUv = (vec2(sampleTexel) + vec2(0.5)) / vec2(resolution);
        vec3 samplePosition = reconstructWorldPositionAt(sampleUv, sampleDepth);
        float separation = length(samplePosition - centerPosition);
        // Keep the depth test inside a small physical range even when a low-resolution screen
        // makes the three-pixel footprint cover more world space.
        float closeRange = 1.0 - smoothstep(0.85, 1.20, separation);
        float foregroundDistance = centerCameraDistance
                - length(samplePosition - uCameraPosition);
        float foreground = smoothstep(
                foregroundBias,
                foregroundSoftness,
                foregroundDistance);

        // The color side of the bilateral test rejects most entity and emissive-boundary samples.
        vec3 sampleLinear = pow(clamp(texelFetch(uScene, sampleTexel, 0).rgb, 0.0, 1.0), vec3(2.2));
        float colorSimilarity = 1.0 - smoothstep(
                0.24,
                0.60,
                abs(luminance(sampleLinear) - sourceLuminance));
        occlusion += foreground * closeRange * colorSimilarity;
    }

    // A wall contributes through several adjacent taps; a lone leaf/depth speck does not become
    // opaque. The configured value stays capped at 18% and defaults to a restrained 10%.
    return clamp(occlusion * 0.22, 0.0, 1.0)
            * continuousReceiver * brightSourceGate * darkReceiverGate * requestedStrength;
}

// The AO pass is half resolution for stability and cost. It stores both the source depth and a
// receiver-valid bit, so the full-resolution composition can reject samples that lie across a
// step, actor or foliage silhouette instead of bilinearly smearing a dark halo over it.
float depthAwareAmbientOcclusion(vec3 worldPosition, float centerDepth) {
    ivec2 ambientResolution = textureSize(uAmbientOcclusion, 0);
    ivec2 depthResolution = textureSize(uDepth, 0);
    vec2 ambientCoordinate = vUv * vec2(ambientResolution) - vec2(0.5);
    ivec2 baseTexel = ivec2(floor(ambientCoordinate));
    vec2 blend = fract(ambientCoordinate);

    ivec2 centerTexel = clamp(
            ivec2(vUv * vec2(depthResolution)),
            ivec2(0),
            depthResolution - ivec2(1));
    ivec2 rightTexel = min(centerTexel + ivec2(1, 0), depthResolution - ivec2(1));
    ivec2 upTexel = min(centerTexel + ivec2(0, 1), depthResolution - ivec2(1));
    vec3 sameDepthRight = reconstructWorldPositionAt(
            (vec2(rightTexel) + vec2(0.5)) / vec2(depthResolution),
            centerDepth);
    vec3 sameDepthUp = reconstructWorldPositionAt(
            (vec2(upTexel) + vec2(0.5)) / vec2(depthResolution),
            centerDepth);
    float pixelScale = max(
            0.0025,
            0.5 * (length(sameDepthRight - worldPosition)
                    + length(sameDepthUp - worldPosition)));
    float nearCompatibility = max(0.10, pixelScale * 3.0);
    float farCompatibility = max(0.30, pixelScale * 10.0);

    float weightedOcclusion = 0.0;
    float totalWeight = 0.0;
    for (int tapY = 0; tapY < 2; tapY++) {
        for (int tapX = 0; tapX < 2; tapX++) {
            ivec2 sampleTexel = clamp(
                    baseTexel + ivec2(tapX, tapY),
                    ivec2(0),
                    ambientResolution - ivec2(1));
            vec4 aoSample = texelFetch(uAmbientOcclusion, sampleTexel, 0);
            if (aoSample.a <= 0.0001 || aoSample.g >= 0.999999) {
                continue;
            }
            float horizontalWeight = tapX == 0 ? 1.0 - blend.x : blend.x;
            float verticalWeight = tapY == 0 ? 1.0 - blend.y : blend.y;
            vec2 sampleUv = (vec2(sampleTexel) + vec2(0.5)) / vec2(ambientResolution);
            vec3 samplePosition = reconstructWorldPositionAt(sampleUv, aoSample.g);
            float depthCompatibility = 1.0 - smoothstep(
                    nearCompatibility,
                    farCompatibility,
                    length(samplePosition - worldPosition));
            float weight = horizontalWeight * verticalWeight * aoSample.a * depthCompatibility;
            weightedOcclusion += aoSample.r * weight;
            totalWeight += weight;
        }
    }
    return totalWeight > 0.0001 ? weightedOcclusion / totalWeight : 0.0;
}

// Water is identified on the CPU from actual FluidTags.WATER surface blocks, then condensed into
// a few world-space anchors. The shader takes their union rather than summing circles, so an
// ocean or a narrow river remains a low, continuous layer instead of becoming a grid of white fog.
float waterMistMask(vec3 worldPosition) {
    float mistMask = 0.0;
    for (int index = 0; index < uWaterMistCount; index++) {
        vec3 offset = worldPosition - uWaterMistPositions[index];
        float radius = max(uWaterMistRadii[index], 0.01);
        float radial = 1.0 - smoothstep(radius * 0.35, radius, length(offset.xz));
        float belowSurface = 1.0 - smoothstep(0.20, 0.85, max(-offset.y, 0.0));
        float aboveSurface = 1.0 - smoothstep(
                max(0.45, uWaterMistHeight * 0.55),
                max(0.75, uWaterMistHeight),
                max(offset.y, 0.0));
        mistMask = max(mistMask, radial * belowSurface * aboveSurface
                * clamp(uWaterMistCoverages[index], 0.0, 1.0));
    }
    return mistMask;
}

// This is a deliberately small ground-contact treatment, not a replacement for Minecraft's
// directional shadow map. The CPU raycasts each candidate straight down and supplies the real
// receiving height, so the post pass only darkens the visible surface at that height. That keeps
// the soft footprint off the entity body and avoids projecting a decal across nearby walls.
//
// It is only evaluated after a pixel has fallen inside a small candidate footprint. The final
// colour/depth target has no G-buffer normal, so derive a normal solely as a rejection test from
// a continuous one-pixel neighbourhood: horizontal ground survives; walls, cutouts and entity
// silhouettes return zero instead of becoming a new source of post-process dark slabs.
float stableHorizontalReceiver(float centerDepth, out vec3 receiverNormal) {
    receiverNormal = vec3(0.0, 1.0, 0.0);
    ivec2 resolution = textureSize(uDepth, 0);
    ivec2 centerTexel = ivec2(vUv * vec2(resolution));
    const int margin = 2;
    if (centerTexel.x < margin || centerTexel.y < margin
            || centerTexel.x >= resolution.x - margin || centerTexel.y >= resolution.y - margin) {
        return 0.0;
    }

    ivec2 texelRight = centerTexel + ivec2(1, 0);
    ivec2 texelLeft = centerTexel + ivec2(-1, 0);
    ivec2 texelUp = centerTexel + ivec2(0, 1);
    ivec2 texelDown = centerTexel + ivec2(0, -1);
    float depthRight = texelFetch(uDepth, texelRight, 0).r;
    float depthLeft = texelFetch(uDepth, texelLeft, 0).r;
    float depthUp = texelFetch(uDepth, texelUp, 0).r;
    float depthDown = texelFetch(uDepth, texelDown, 0).r;
    if (centerDepth >= 0.999999 || depthRight >= 0.999999 || depthLeft >= 0.999999
            || depthUp >= 0.999999 || depthDown >= 0.999999) {
        return 0.0;
    }

    vec2 centerUv = (vec2(centerTexel) + vec2(0.5)) / vec2(resolution);
    vec3 center = reconstructWorldPositionAt(centerUv, centerDepth);
    vec3 right = reconstructWorldPositionAt(
            (vec2(texelRight) + vec2(0.5)) / vec2(resolution), depthRight);
    vec3 left = reconstructWorldPositionAt(
            (vec2(texelLeft) + vec2(0.5)) / vec2(resolution), depthLeft);
    vec3 up = reconstructWorldPositionAt(
            (vec2(texelUp) + vec2(0.5)) / vec2(resolution), depthUp);
    vec3 down = reconstructWorldPositionAt(
            (vec2(texelDown) + vec2(0.5)) / vec2(resolution), depthDown);
    vec3 sameDepthRight = reconstructWorldPositionAt(
            (vec2(texelRight) + vec2(0.5)) / vec2(resolution), centerDepth);
    vec3 sameDepthUp = reconstructWorldPositionAt(
            (vec2(texelUp) + vec2(0.5)) / vec2(resolution), centerDepth);
    float localPixelScale = max(
            0.0025,
            0.5 * (length(sameDepthRight - center) + length(sameDepthUp - center)));
    float receiverJump = max(
            max(length(right - center), length(left - center)),
            max(length(up - center), length(down - center)));
    float continuityScale = min(localPixelScale, 0.08);
    float continuousReceiver = 1.0 - smoothstep(
            max(0.025, continuityScale * 2.5),
            max(0.12, continuityScale * 6.0),
            receiverJump);
    if (continuousReceiver <= 0.0001) {
        return 0.0;
    }

    vec3 normal = cross(right - left, up - down);
    float normalLength = length(normal);
    if (normalLength <= 0.00001) {
        return 0.0;
    }
    receiverNormal = normal / normalLength;
    float horizontal = smoothstep(0.62, 0.86, abs(receiverNormal.y));
    return continuousReceiver * horizontal;
}

float stableHorizontalReceiver(float centerDepth) {
    vec3 ignoredNormal;
    return stableHorizontalReceiver(centerDepth, ignoredNormal);
}

// Standard shadow-map comparison: transform the reconstructed receiver into the light's clip
// space, then ask whether a closer light-space depth was rasterized there. This deliberately
// operates on world position rather than an actor footprint, so a tree or living entity can cast
// a long shadow across the surrounding terrain as the sun direction changes.
float directionalShadowMask(vec3 worldPosition, float centerDepth, vec3 sourceLinear) {
    float requestedStrength = clamp(uDirectionalShadowStrength, 0.0, 0.70);
    if (requestedStrength <= 0.0001) {
        return 0.0;
    }

    // Reject pixels that are already too dark/bright before doing the light-space matrix
    // transform. This keeps the map a restrained ground-composition tool and avoids spending
    // nine depth taps on foliage and sky-adjacent silhouettes at 4K render targets.
    float sourceLuminance = luminance(sourceLinear);
    float darkReceiverGate = smoothstep(0.025, 0.13, sourceLuminance);
    float highlightReceiverGate = 1.0 - smoothstep(1.10, 1.80, sourceLuminance);
    if (darkReceiverGate * highlightReceiverGate <= 0.0001) {
        return 0.0;
    }

    vec4 lightClip = uDirectionalShadowLightViewProjection * vec4(worldPosition, 1.0);
    if (abs(lightClip.w) <= 0.00001) {
        return 0.0;
    }
    vec3 lightCoordinates = lightClip.xyz / lightClip.w * 0.5 + 0.5;
    if (lightCoordinates.x <= 0.002 || lightCoordinates.x >= 0.998
            || lightCoordinates.y <= 0.002 || lightCoordinates.y >= 0.998
            || lightCoordinates.z <= 0.0001 || lightCoordinates.z >= 0.9999) {
        return 0.0;
    }

    // A single base-bias tap is a cheap rejection for the overwhelmingly common lit case.
    // Only candidates pay for the reconstructed receiver normal and remaining PCF taps.
    float baseComparisonDepth = lightCoordinates.z - max(uDirectionalShadowBias, 0.00001);
    float centerShadow = baseComparisonDepth > texture(uDirectionalShadowDepth, lightCoordinates.xy).r
            ? 1.0
            : 0.0;
    if (centerShadow <= 0.0001) {
        return 0.0;
    }

    // Final colour+depth has no G-buffer normal. Keep the physical depth test, but restrict its
    // first HD-2D version to continuous ground-like receivers so approximate foliage columns do
    // not paint a dark silhouette across leaves, entity bodies or vertical block faces.
    vec3 receiverNormal;
    float receiver = stableHorizontalReceiver(centerDepth, receiverNormal);
    if (receiver <= 0.0001) {
        return 0.0;
    }

    // A low sun makes even a horizontal path oblique in light space. Add a small normal-based
    // slope bias only there; unlike a screen derivative this remains stable across branch and
    // material edges, and avoids self-shadow acne reading as a diagonal road stripe.
    vec3 lightToSurface = normalize(uDirectionalShadowLightDirection);
    float normalToLight = clamp(dot(receiverNormal, -lightToSurface), 0.0, 1.0);
    float slopeScaledBias = (1.0 - normalToLight) * 0.0016;
    float comparisonDepth = lightCoordinates.z
            - max(max(uDirectionalShadowBias, 0.00001), slopeScaledBias);
    centerShadow = comparisonDepth > texture(uDirectionalShadowDepth, lightCoordinates.xy).r
            ? 1.0
            : 0.0;
    if (centerShadow <= 0.0001) {
        return 0.0;
    }

    float occludedSamples = centerShadow;
    const int kernelRadius = 1;
    for (int y = -kernelRadius; y <= kernelRadius; y++) {
        for (int x = -kernelRadius; x <= kernelRadius; x++) {
            if (x == 0 && y == 0) {
                continue;
            }
            vec2 offset = vec2(float(x), float(y))
                    * uDirectionalShadowInverseResolution * uDirectionalShadowSoftness;
            float closestDepth = texture(uDirectionalShadowDepth, lightCoordinates.xy + offset).r;
            occludedSamples += comparisonDepth > closestDepth ? 1.0 : 0.0;
        }
    }
    float pcfShadow = occludedSamples / 9.0;
    float weatherGate = 1.0 - clamp(max(uRain, uThunder), 0.0, 1.0) * 0.58;
    float daylightGate = smoothstep(0.04, 0.30, uDaylight);
    return pcfShadow * receiver * darkReceiverGate * highlightReceiverGate
            * weatherGate * daylightGate * requestedStrength;
}

int pointLightShadowFace(vec3 direction) {
    vec3 absoluteDirection = abs(direction);
    if (absoluteDirection.x >= absoluteDirection.y && absoluteDirection.x >= absoluteDirection.z) {
        return direction.x >= 0.0 ? 0 : 1;
    }
    if (absoluteDirection.y >= absoluteDirection.z) {
        return direction.y >= 0.0 ? 2 : 3;
    }
    return direction.z >= 0.0 ? 4 : 5;
}

vec3 pointLightShadowFaceForward(int face) {
    if (face == 0) return vec3(1.0, 0.0, 0.0);
    if (face == 1) return vec3(-1.0, 0.0, 0.0);
    if (face == 2) return vec3(0.0, 1.0, 0.0);
    if (face == 3) return vec3(0.0, -1.0, 0.0);
    if (face == 4) return vec3(0.0, 0.0, 1.0);
    return vec3(0.0, 0.0, -1.0);
}

vec3 pointLightShadowFaceUp(int face) {
    if (face == 2) return vec3(0.0, 0.0, 1.0);
    if (face == 3) return vec3(0.0, 0.0, -1.0);
    return vec3(0.0, -1.0, 0.0);
}

vec2 pointLightShadowFaceUv(vec3 fromLight, int face) {
    vec3 forward = pointLightShadowFaceForward(face);
    vec3 up = pointLightShadowFaceUp(face);
    vec3 right = normalize(cross(forward, up));
    float forwardDistance = max(dot(fromLight, forward), 0.00001);
    return vec2(dot(fromLight, right), dot(fromLight, up)) / forwardDistance * 0.5 + 0.5;
}

float pointLightShadowDepth(int shadowIndex, int face, vec2 faceUv) {
    int faceResolution = max(1, int(round(1.0 / max(uLocalLightShadowInverseResolution.x, 0.00001))));
    int tileStride = max(1, int(round(uLocalLightShadowAtlasTileStride)));
    int gutter = max(0, int(round(uLocalLightShadowAtlasGutter)));
    int tile = shadowIndex * POINT_LIGHT_SHADOW_FACES + face;
    ivec2 innerOrigin = ivec2(
            tile % POINT_LIGHT_SHADOW_ATLAS_COLUMNS,
            tile / POINT_LIGHT_SHADOW_ATLAS_COLUMNS) * tileStride + ivec2(gutter);
    float halfTexel = 0.5 / float(faceResolution);
    vec2 safeFaceUv = clamp(faceUv, vec2(halfTexel), vec2(1.0 - halfTexel));
    ivec2 texel = innerOrigin + ivec2(floor(safeFaceUv * float(faceResolution)));
    ivec2 maximumTexel = innerOrigin + ivec2(faceResolution - 1);
    return texelFetch(uLocalLightShadowAtlas, clamp(texel, innerOrigin, maximumTexel), 0).r;
}

float pointLightShadowProjectedDepth(float forwardDistance, float farPlane) {
    const float nearPlane = 0.20;
    float safeFarPlane = max(farPlane, nearPlane + 0.10);
    return safeFarPlane / (safeFarPlane - nearPlane)
            - safeFarPlane * nearPlane / ((safeFarPlane - nearPlane) * max(forwardDistance, nearPlane));
}

// A true perspective point-light depth comparison. Up to sixteen score-sorted lamps may
// contribute. All six faces live in a shared atlas, and their shadows soften/fade before the
// cube boundary so an actor proxy reads as local contact, never as a hard expanding fan.
float localLightShadowMask(int shadowIndex, vec3 worldPosition, float centerDepth) {
    if (shadowIndex < 0 || shadowIndex >= uLocalLightShadowCount) {
        return 0.0;
    }
    if (uLocalLightShadowActive[shadowIndex] == 0) {
        return 0.0;
    }

    float requestedStrength = clamp(uLocalLightShadowStrength, 0.0, 1.0);
    float shadowRange = uLocalLightShadowRanges[shadowIndex];
    if (requestedStrength <= 0.0001 || shadowRange <= 0.01) {
        return 0.0;
    }

    vec3 lightPosition = uLocalLightShadowPositions[shadowIndex];
    vec3 fromLight = worldPosition - lightPosition;
    float distanceToLight = length(fromLight);
    if (distanceToLight <= 0.28 || distanceToLight >= shadowRange * 0.995) {
        return 0.0;
    }

    vec3 receiverNormal;
    float receiver = stableHorizontalReceiver(centerDepth, receiverNormal);
    if (receiver <= 0.0001) {
        return 0.0;
    }

    int face = pointLightShadowFace(fromLight);
    vec3 forward = pointLightShadowFaceForward(face);
    float forwardDistance = dot(fromLight, forward);
    vec2 faceUv = pointLightShadowFaceUv(fromLight, face);
    float receiverDepth = pointLightShadowProjectedDepth(forwardDistance, shadowRange);
    if (faceUv.x <= 0.003 || faceUv.x >= 0.997
            || faceUv.y <= 0.003 || faceUv.y >= 0.997
            || receiverDepth <= 0.0001 || receiverDepth >= 0.9999) {
        return 0.0;
    }

    vec3 directionToLight = -fromLight / max(distanceToLight, 0.00001);
    float receiverSlope = 1.0 - clamp(dot(receiverNormal, directionToLight), 0.0, 1.0);
    float comparisonBias = max(
            max(uLocalLightShadowBias, 0.00001),
            receiverSlope * 0.0015);
    float comparisonDepth = receiverDepth - comparisonBias;
    float centerShadow = comparisonDepth > pointLightShadowDepth(shadowIndex, face, faceUv)
            ? 1.0
            : 0.0;
    if (centerShadow <= 0.0001) {
        return 0.0;
    }

    float occludedSamples = centerShadow;
    float penumbraScale = mix(1.0, 2.4, smoothstep(
            shadowRange * 0.22,
            shadowRange * 0.82,
            distanceToLight));
    const int kernelRadius = 1;
    for (int y = -kernelRadius; y <= kernelRadius; y++) {
        for (int x = -kernelRadius; x <= kernelRadius; x++) {
            if (x == 0 && y == 0) {
                continue;
            }
            vec2 offset = vec2(float(x), float(y))
                    * uLocalLightShadowInverseResolution
                    * uLocalLightShadowSoftness * penumbraScale;
            float closestDepth = pointLightShadowDepth(shadowIndex, face, faceUv + offset);
            occludedSamples += comparisonDepth > closestDepth ? 1.0 : 0.0;
        }
    }
    float distanceFade = 1.0 - smoothstep(
            shadowRange * 0.48,
            shadowRange * 0.88,
            distanceToLight);
    return occludedSamples / 9.0 * receiver * requestedStrength * distanceFade;
}

float entityGroundShadowMask(vec3 worldPosition, vec3 sourceLinear) {
    float requestedStrength = clamp(uEntityGroundShadowStrength, 0.0, 0.35);
    if (requestedStrength <= 0.0001 || uEntityGroundShadowCount <= 0) {
        return 0.0;
    }

    float sourceLuminance = luminance(sourceLinear);
    // Preserve dark foliage/crevices and emissive blocks: this should read as a soft contact
    // beneath an actor, never as a second black sticker stamped over existing lighting.
    float darkReceiverGate = smoothstep(0.028, 0.15, sourceLuminance);
    float brightReceiverGate = 1.0 - smoothstep(0.92, 1.55, sourceLuminance);
    if (darkReceiverGate * brightReceiverGate <= 0.0001) {
        return 0.0;
    }

    float combinedMask = 0.0;
    for (int index = 0; index < uEntityGroundShadowCount; index++) {
        vec3 offset = worldPosition - uEntityGroundShadowPositions[index];
        float radius = max(uEntityGroundShadowRadii[index], 0.05);
        // Ground positions come from a vertical collision ray. A loose vertical tolerance makes
        // the effect survive depth reconstruction precision without climbing a block wall.
        float receivingHeight = 1.0 - smoothstep(0.075, 0.26, abs(offset.y));
        float footprint = 1.0 - smoothstep(radius * 0.24, radius, length(offset.xz));
        float candidate = footprint * receivingHeight
                * clamp(uEntityGroundShadowOpacities[index], 0.0, 1.0);
        combinedMask = max(combinedMask, candidate);
    }

    if (combinedMask <= 0.0001) {
        return 0.0;
    }

    float depth = texture(uDepth, vUv).r;
    return clamp(combinedMask, 0.0, 1.0)
            * stableHorizontalReceiver(depth)
            * darkReceiverGate * brightReceiverGate * requestedStrength;
}

void main() {
    vec4 source = texture(uScene, vUv);
    // Minecraft's ordinary RGBA8 target contains display-referred color. Work in linear space
    // throughout this extension, then encode exactly once in octopath_present.fsh.
    vec3 sourceLinear = pow(clamp(source.rgb, 0.0, 1.0), vec3(2.2));
    float depth = texture(uDepth, vUv).r;
    float daylight = clamp(uDaylight, 0.0, 1.0);
    float twilight = clamp(uTwilight, 0.0, 1.0);
    float weather = clamp(max(uRain, uThunder), 0.0, 1.0);

    if (depth >= 0.999999) {
        vec3 skyGrade = mix(vec3(0.72, 0.82, 1.16), vec3(1.08, 1.01, 0.88), daylight);
        skyGrade = mix(skyGrade, vec3(1.22, 0.70, 0.46), twilight * 0.34);
        skyGrade = mix(skyGrade, vec3(0.66, 0.75, 0.92), weather * 0.28);
        vec3 horizon = mix(uSkyColor, vec3(0.98, 0.63, 0.35), twilight * 0.32) * 0.05;
        vec3 styledSky = sourceLinear * skyGrade + horizon;
        styledSky = tintPreservingLuminance(styledSky, uToneSkyTint, uToneStrength);
        fragColor = vec4(mix(sourceLinear, styledSky, uEffectStrength), source.a);
        return;
    }

    vec3 worldPosition = reconstructWorldPosition(depth);
    vec3 coolAmbient = mix(vec3(0.15, 0.24, 0.43), vec3(0.43, 0.55, 0.72), daylight);
    vec3 warmSun = mix(vec3(0.92, 0.44, 0.18), vec3(1.00, 0.80, 0.51), daylight);
    vec3 colorGrade = mix(vec3(0.77, 0.86, 1.14), vec3(1.08, 1.02, 0.88), daylight);
    colorGrade = mix(colorGrade, vec3(1.18, 0.79, 0.52), twilight * 0.25);
    colorGrade = mix(colorGrade, vec3(0.78, 0.84, 0.96), weather * 0.22);

    vec3 localLight = vec3(0.0);
    for (int index = 0; index < uLocalLightCount; index++) {
        vec3 toLight = uLocalLightPositions[index] - worldPosition;
        float distanceSquared = dot(toLight, toLight);
        // Final colour+depth does not contain a stable material normal for cutout leaves or
        // entity boundaries. A soft isotropic contribution is safer than deriving a false normal
        // from depth derivatives and creating black/bright slabs across foliage.
        // Keep the source's immediate pool readable, but deliberately stop it within a few
        // blocks. The post pass sees several interior lamps through the final colour target;
        // a broad falloff makes those hidden lamps add up into an orange full-screen wash.
        float reach = clamp(uLocalLightReach, 0.4, 2.5);
        float distanceToLight = sqrt(max(distanceSquared, 0.0));
        float compactCore = 0.50 / (1.0 + distanceSquared * 0.20 / (reach * reach));
        float outerPool = 1.0 - smoothstep(1.5 * reach, 6.5 * reach, distanceToLight);
        float attenuation = (compactCore + 0.022 * outerPool * outerPool)
                * uLocalLightPowers[index];
        // The first two entries in the score-sorted local-light list may have a depth cube.
        // They are intentionally independent: overlapping lamp pools should each be occluded
        // by their own source rather than inheriting the nearest lamp's shadow.
        if (index < uLocalLightShadowCount) {
            attenuation *= 1.0 - localLightShadowMask(index, worldPosition, depth);
        }
        localLight += uLocalLightColors[index] * attenuation;
    }

    // Daylight already illuminates the environment and supplies the readable surface contrast.
    // Retain only a restrained warm accent from block lights at noon; rain and thunder ease this
    // suppression so lamps regain presence as the world actually gets darker.
    // Lamps should fall away as soon as the sun clears the horizon. Their native emissive
    // pixels remain visible, but the large post-light pool no longer reads as early-morning fog.
    float clearDaylight = smoothstep(0.01, 0.12, daylight) * (1.0 - weather * 0.62);
    float environmentLightScale = mix(
            1.0,
            clamp(uLocalLightDaylightMultiplier, 0.0, 1.0),
            clearDaylight);
    localLight *= environmentLightScale;

    // Preserve weak, isolated light exactly, but smoothly shoulder clusters of glowstone,
    // torches or interior lamps. This is a cap on the *added post light* only; the block's
    // native emissive texture and its Bloom are untouched, so the source never looks muted.
    float localLightLuminance = luminance(localLight);
    const float localLightSoftCap = 0.30;
    const float localLightShoulder = 0.12;
    if (localLightLuminance > localLightSoftCap) {
        float excess = localLightLuminance - localLightSoftCap;
        float compressedLuminance = localLightSoftCap
                + excess / (1.0 + excess / localLightShoulder);
        localLight *= compressedLuminance / max(localLightLuminance, 0.00001);
    }

    // Minecraft has already lit this colour target. Re-lighting it from a normal reconstructed
    // out of final depth created false dark slabs on leaves, cutouts and block edges; retain the
    // source lighting and reserve the reconstructed normal for gentle local-light orientation.
    vec3 styled = sourceLinear * colorGrade;
    // The legacy micro-contact test and the broader half-resolution AO describe the same
    // ambient-cavity phenomenon. Taking their maximum avoids stacking them into black seams;
    // local lamps are added below, so they can still lift a shaded floor naturally.
    float contactOcclusion = conservativeContactOcclusion(sourceLinear);
    float ambientOcclusion = depthAwareAmbientOcclusion(worldPosition, depth);
    styled *= 1.0 - max(contactOcclusion, ambientOcclusion);
    // The standard light-space depth comparison owns projected shadows. The legacy contact
    // footprint is gated off by Java whenever the map is active, but remains as an opt-in
    // compatibility fallback for users who explicitly disable the map.
    styled *= 1.0 - entityGroundShadowMask(worldPosition, sourceLinear);
    styled *= 1.0 - directionalShadowMask(worldPosition, depth, sourceLinear);
    styled = tintPreservingLuminance(styled, uToneMidTint, uToneStrength * 0.62);

    float sceneLuminance = luminance(styled);
    float litSurface = smoothstep(0.24, 0.86, sceneLuminance);
    vec3 shadowTint = mix(vec3(0.76, 0.88, 1.15), vec3(0.88, 0.94, 1.08), daylight);
    vec3 highlightTint = mix(vec3(1.16, 0.78, 0.50), vec3(1.10, 0.95, 0.79), daylight);
    highlightTint = mix(highlightTint, vec3(1.22, 0.70, 0.43), twilight * 0.48);
    shadowTint *= mix(vec3(1.0), uToneShadowTint, uToneStrength);
    highlightTint *= mix(vec3(1.0), uToneHighlightTint, uToneStrength);
    styled = tintPreservingLuminance(
            styled,
            mix(shadowTint, highlightTint, litSurface),
            0.42);

    // Keep authored emissive hues out of the global day/night palette. A cyan soul lantern or
    // warm glowstone should remain a small, readable colour event in a blue night rather than
    // being pushed toward the same scene tint as the terrain around it. Fog, vibrance and the
    // final highlight shoulder still act on this contribution below, so it cannot bypass the
    // eye-protection part of the pipeline.
    styled += localLight * uLocalLightIntensity;

    float distanceToCamera = length(worldPosition - uCameraPosition);
    float fogDensity = max(0.0, uFogDensity + uRainFogBoost * uRain);
    float heightWeight = mix(1.12, 0.76, smoothstep(
            uCameraPosition.y - 8.0,
            uCameraPosition.y + 22.0,
            worldPosition.y));
    float fog = 1.0 - exp(-distanceToCamera * fogDensity * heightWeight);
    fog = min(fog, clamp(uFogMaximumOpacity, 0.0, 1.0));
    vec3 fogColor = mix(coolAmbient, uSkyColor, 0.48);
    fogColor = mix(fogColor, warmSun, twilight * 0.18);
    fogColor = mix(fogColor, vec3(0.39, 0.47, 0.61), weather * 0.36);
    fogColor = tintPreservingLuminance(fogColor, uToneFogTint, uToneStrength);
    styled = mix(styled, fogColor, fog);

    // The sunlight shaft pass is rendered at quarter resolution, then deliberately upsampled
    // as a soft air layer. Keeping the ray march out of this full-resolution composition pass
    // makes the effect practical alongside the sixteen-light shadow atlas. Compensate for the
    // final artistic blend below: the world grade may be partial, but a configured shaft should
    // not silently lose the same fraction of its already restrained air-scattering energy.
    float volumetricMixCompensation = uEffectStrength <= 0.0001
            ? 0.0
            : min(1.0 / uEffectStrength, 6.6667);
    styled += texture(uVolumetricSunlight, vUv).rgb * volumetricMixCompensation;

    // Unlike broad height fog, this is restricted to real visible water and a short height above
    // its surface. Deep original shadows receive only a small fraction, preserving the scene's
    // source lighting instead of using mist to repaint it.
    float waterMistShadowGate = mix(
            0.32,
            1.0,
            smoothstep(0.035, 0.24, luminance(sourceLinear)));
    float waterMistDistanceGate = mix(
            0.58,
            1.0,
            smoothstep(5.0, 34.0, distanceToCamera));
    float localWaterMist = waterMistMask(worldPosition) * waterMistShadowGate
            * waterMistDistanceGate * (1.0 - fog * 0.45);
    float waterMistOpacity = min(
            0.12,
            0.12 * clamp(uWaterMistStrength, 0.0, 1.0) * localWaterMist);
    vec3 waterMistColor = mix(fogColor, vec3(0.28, 0.42, 0.56), 0.42 + weather * 0.10);
    styled = mix(styled, waterMistColor, waterMistOpacity);

    styled = applyVibrance(
            styled,
            uVibrance * (1.0 - weather * 0.40)
                    * mix(1.0, uToneVibranceMultiplier, uToneStrength));

    // A stage light is compositional rather than a fake physical lamp: it only gives restrained
    // mid-tone lift and a luminance-preserving tint around the actor. Bright pixels deliberately
    // opt out, then the later Bloom and shoulder compression continue to protect the eye.
    float stageDistance = length((vUv - uStageFocusUv) * 2.0);
    float stageMask = 1.0 - smoothstep(
            uStageSpotlightRadius * 0.28,
            uStageSpotlightRadius * 1.15,
            stageDistance);
    float stageLuminance = luminance(styled);
    float stageMidtones = smoothstep(0.04, 0.24, stageLuminance)
            * (1.0 - smoothstep(0.50, 0.82, stageLuminance));
    float stageLift = 0.13 * clamp(uStageSpotlightStrength, 0.0, 1.0)
            * stageMask * stageMidtones;
    styled *= 1.0 + stageLift;

    vec3 stageTint = mix(
            vec3(1.10, 0.91, 0.71),
            vec3(1.04, 0.98, 0.90),
            daylight);
    stageTint = mix(stageTint, vec3(1.16, 0.82, 0.58), twilight * 0.35);
    styled = tintPreservingLuminance(
            styled,
            stageTint,
            0.12 * clamp(uStageSpotlightStrength, 0.0, 1.0) * stageMask * stageMidtones);

    fragColor = vec4(mix(sourceLinear, styled, uEffectStrength), source.a);
}
