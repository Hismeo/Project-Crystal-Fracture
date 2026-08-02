#version 460 core

in vec2 vUv;
layout(location = 0) out vec4 fragColor;

uniform sampler2D uDepth;
uniform sampler2D uDirectionalShadowDepth;
uniform mat4 uInverseViewProjection;
uniform mat4 uDirectionalShadowLightViewProjection;
uniform vec3 uDirectionalShadowLightDirection;
uniform vec3 uCameraPosition;
uniform vec2 uDirectionalShadowInverseResolution;
uniform float uDaylight;
uniform float uTwilight;
uniform float uRain;
uniform float uThunder;
uniform float uStrength;
uniform float uMaximumDistance;
uniform int uSampleCount;
const int MAX_STAINED_GLASS_FILTERS = 12;
uniform float uStainedGlassStrength;
uniform int uStainedGlassFilterCount;
uniform vec3 uStainedGlassFilterPositions[MAX_STAINED_GLASS_FILTERS];
uniform vec3 uStainedGlassFilterColors[MAX_STAINED_GLASS_FILTERS];
uniform float uStainedGlassFilterRadii[MAX_STAINED_GLASS_FILTERS];

vec3 reconstructWorldPosition(float depth) {
    vec4 clip = vec4(vUv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 world = uInverseViewProjection * clip;
    return world.xyz / max(abs(world.w), 0.00001);
}

float interleavedGradientNoise(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

// This is deliberately separate from the ground-shadow test. Air has no stable receiver normal
// or material colour, so it only needs a light-space depth comparison: opaque proxy geometry
// removes sunlight; glass/panes are omitted by the CPU scanner and therefore transmit it.
float directionalAirLightVisibility(vec3 worldPosition) {
    vec4 lightClip = uDirectionalShadowLightViewProjection * vec4(worldPosition, 1.0);
    if (abs(lightClip.w) <= 0.00001) {
        return -1.0;
    }
    vec3 lightCoordinates = lightClip.xyz / lightClip.w * 0.5 + 0.5;
    if (lightCoordinates.x <= 0.004 || lightCoordinates.x >= 0.996
            || lightCoordinates.y <= 0.004 || lightCoordinates.y >= 0.996
            || lightCoordinates.z <= 0.0001 || lightCoordinates.z >= 0.9999) {
        return -1.0;
    }

    // Air must not inherit the hard one-texel edge of a terrain shadow. A small 3x3 PCF kernel
    // turns a roof/tree transition into a volume boundary instead of a bright diagonal drawn on
    // the ground after the ray march is composited.
    vec2 filterOffset = uDirectionalShadowInverseResolution * 4.5;
    float comparisonDepth = lightCoordinates.z - 0.00075;
    float occludedSamples = 0.0;
    for (int pcfY = -1; pcfY <= 1; pcfY++) {
        for (int pcfX = -1; pcfX <= 1; pcfX++) {
            float closestDepth = texture(
                    uDirectionalShadowDepth,
                    lightCoordinates.xy + vec2(float(pcfX), float(pcfY)) * filterOffset).r;
            // A soft comparison is important here: this is air attenuation, not the hard
            // receiver test used by the terrain shadow. It prevents low-resolution proxy depth
            // from appearing as a single razor-sharp diagonal through the volume.
            occludedSamples += smoothstep(
                    -0.0025,
                    0.0025,
                    comparisonDepth - closestDepth);
        }
    }
    return 1.0 - occludedSamples / 9.0;
}

// A stained pane is a small coloured aperture. Once sunlight crosses that aperture it continues
// along the incoming sun direction, producing a parallel, softly widening dyed shaft. This is
// intentionally an air-light filter rather than a fake screen overlay: an opaque blocker after
// the glass still removes the sample through directionalAirLightVisibility above.
vec3 stainedGlassTransmission(vec3 samplePosition, vec3 incomingLight) {
    float requestedStrength = clamp(uStainedGlassStrength, 0.0, 1.0);
    if (requestedStrength <= 0.0001 || uStainedGlassFilterCount <= 0) {
        return vec3(1.0);
    }

    vec3 transmission = vec3(1.0);
    for (int index = 0; index < MAX_STAINED_GLASS_FILTERS; index++) {
        if (index >= uStainedGlassFilterCount) {
            break;
        }
        vec3 fromFilter = samplePosition - uStainedGlassFilterPositions[index];
        float downstreamDistance = dot(fromFilter, incomingLight);
        if (downstreamDistance <= 0.02) {
            continue;
        }
        vec3 lateral = fromFilter - incomingLight * downstreamDistance;
        float sourceRadius = max(uStainedGlassFilterRadii[index], 0.10);
        float beamRadius = sourceRadius + downstreamDistance * 0.045;
        float shaftMask = 1.0 - smoothstep(sourceRadius * 0.45, beamRadius, length(lateral));
        if (shaftMask <= 0.0001) {
            continue;
        }
        // Dye colours are sent in linear space. Normalising the peak before applying a restrained
        // transmission floor keeps deep blue/red panes visibly coloured in air; raw texture
        // albedo made those shafts almost black even though the sunlight itself transmitted.
        vec3 dye = max(uStainedGlassFilterColors[index], vec3(0.025));
        float dyePeak = max(max(dye.r, dye.g), dye.b);
        vec3 visibleDye = mix(dye, dye / max(dyePeak, 0.025) * 0.72, 0.82);
        transmission = mix(transmission, visibleDye, shaftMask * requestedStrength);
    }
    return transmission;
}

void main() {
    float requestedStrength = clamp(uStrength, 0.0, 0.50);
    float depth = texture(uDepth, vUv).r;
    if (requestedStrength <= 0.0001 || depth >= 0.999999) {
        fragColor = vec4(0.0);
        return;
    }

    vec3 surfacePosition = reconstructWorldPosition(depth);
    vec3 cameraToSurface = surfacePosition - uCameraPosition;
    float surfaceDistance = length(cameraToSurface);
    int sampleCount = clamp(uSampleCount, 4, 12);
    // Without TAA, letting a 10-sample ray jump five or six blocks turns a single shadow-map
    // transition into an obvious band. Cap the physical spacing instead of trusting a very large
    // config distance; nearby playable-scale shafts remain stable and continuous.
    float stableMarchDistance = float(sampleCount) * 2.25;
    float marchDistance = min(
            surfaceDistance,
            min(max(uMaximumDistance, 0.01), stableMarchDistance));
    if (surfaceDistance <= 0.15 || marchDistance <= 0.15) {
        fragColor = vec4(0.0);
        return;
    }

    vec3 viewDirection = cameraToSurface / surfaceDistance;
    vec3 incomingLight = normalize(uDirectionalShadowLightDirection);
    // This elevated HD-2D camera looks down along the incoming sun direction, rather than toward
    // the sky as a first-person camera would. The former -viewDirection dot product therefore
    // stayed near zero and locked all shafts to their dimmest phase. Use the along-sun alignment
    // so diagonal morning light remains readable in the miniature composition.
    float alongSunView = pow(max(dot(incomingLight, viewDirection), 0.0), 1.35);
    float phase = mix(0.42, 1.10, alongSunView);
    float litAir = 0.0;
    vec3 filteredLitAir = vec3(0.0);
    float mappedSamples = 0.0;
    float jitter = (interleavedGradientNoise(gl_FragCoord.xy) - 0.5) * 0.28;
    for (int index = 0; index < 12; index++) {
        if (index >= sampleCount) {
            break;
        }
        float progress = clamp((float(index) + 0.5 + jitter) / float(sampleCount), 0.02, 0.98);
        vec3 samplePosition = uCameraPosition + viewDirection * (marchDistance * progress);
        float sunlight = directionalAirLightVisibility(samplePosition);
        if (sunlight < 0.0) {
            continue;
        }

        // Match the existing low ground haze: shafts accumulate around the playable layers and
        // fall away in high empty air, preserving the miniature stage read.
        float heightDensity = 1.0 - smoothstep(
                uCameraPosition.y + 8.0,
                uCameraPosition.y + 26.0,
                samplePosition.y);
        float airSample = sunlight * mix(0.45, 1.0, heightDensity);
        litAir += airSample;
        filteredLitAir += airSample * stainedGlassTransmission(samplePosition, incomingLight);
        mappedSamples += 1.0;
    }
    if (mappedSamples <= 0.5) {
        fragColor = vec4(0.0);
        return;
    }

    float daylightGate = smoothstep(0.025, 0.18, clamp(uDaylight, 0.0, 1.0));
    float weatherGate = 1.0 - clamp(max(uRain, uThunder), 0.0, 1.0) * 0.78;
    float sunlightCoverage = clamp(litAir / mappedSamples, 0.0, 1.0);
    // A previous boundary-only boost made the visibility transition itself brighter than the
    // surrounding air, which read as an artificial diagonal shadow line on the grass. Keep a
    // restrained, continuous air density instead; occluded samples naturally remove it and the
    // softened PCF above supplies the broad edge of a real shaft.
    float shaftEmphasis = 0.28;
    float opticalDepth = marchDistance * 0.034 * sunlightCoverage;
    float scattering = 1.0 - exp(-opticalDepth);
    vec3 airColor = mix(vec3(0.43, 0.56, 0.78), vec3(1.00, 0.79, 0.49), clamp(uDaylight, 0.0, 1.0));
    airColor = mix(airColor, vec3(1.06, 0.67, 0.38), clamp(uTwilight, 0.0, 1.0) * 0.16);
    vec3 transmission = filteredLitAir / max(litAir, 0.00001);
    // Glass is a deliberate small aperture rather than a world-shadow boundary. Give its dyed
    // shaft a visible floor too, otherwise pure coloured panes disappear against a bright
    // daytime surface; this is deliberately smooth rather than an edge-only stripe.
    float tintPresence = smoothstep(0.04, 0.28, length(transmission - vec3(1.0)));
    shaftEmphasis = max(shaftEmphasis, mix(0.28, 0.46, tintPresence));
    fragColor = vec4(airColor * transmission * scattering * shaftEmphasis
            * phase * daylightGate * weatherGate * requestedStrength, 1.0);
}
