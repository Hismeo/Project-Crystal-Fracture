#version 460 core

in vec2 vUv;
layout(location = 0) out vec4 fragColor;

uniform sampler2D uScene;
uniform sampler2D uDepth;
uniform mat4 uInverseViewProjection;
uniform vec3 uCameraPosition;
uniform vec3 uFocusPosition;
uniform vec3 uFocusPlaneNormal;
uniform vec2 uFocusUv;
uniform float uFocusDistance;
uniform float uFocusRange;
uniform float uDepthOfFieldStrength;
uniform float uMaximumBlurPixels;
uniform float uTiltShiftStrength;
uniform float uTiltShiftFocusBand;
uniform float uTiltShiftTransition;
uniform float uTiltShiftFocusLineTilt;
uniform float uTiltShiftEdgeBlur;
uniform vec2 uInverseResolution;

const vec2 POISSON[12] = vec2[](
    vec2(-0.326, -0.406), vec2(-0.840, -0.074), vec2(-0.696, 0.457),
    vec2(-0.203, 0.621), vec2(0.962, -0.195), vec2(0.473, -0.480),
    vec2(0.519, 0.767), vec2(0.185, -0.893), vec2(0.507, 0.064),
    vec2(0.896, 0.412), vec2(-0.322, -0.933), vec2(-0.792, -0.598)
);

vec3 reconstructWorldPosition(float depth) {
    vec4 clip = vec4(vUv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 world = uInverseViewProjection * clip;
    return world.xyz / max(abs(world.w), 0.00001);
}

// Retaining the previous radial camera-distance model as one side of the blend lets players
// smoothly dial the miniature treatment in, instead of turning a familiar focus behaviour into a
// hard binary change.
float legacyCoc(vec3 worldPosition) {
    float distanceToCamera = length(worldPosition - uCameraPosition);
    float focusError = abs(distanceToCamera - uFocusDistance);
    return smoothstep(
            max(0.1, uFocusRange * 0.40),
            max(0.2, uFocusRange * 2.10),
            focusError);
}

// A player-centred, slanted horizontal focus band gives the image its miniature-camera grammar.
// The x component is aspect corrected so the configured line slope stays consistent at 16:9,
// ultrawide, and narrow windows.
float screenCoc() {
    float aspect = max(uInverseResolution.y / max(uInverseResolution.x, 0.000001), 0.0001);
    float focusLineY = uFocusUv.y
            + (vUv.x - uFocusUv.x) * aspect * clamp(uTiltShiftFocusLineTilt, -0.5, 0.5);
    float lineDistance = abs(vUv.y - focusLineY);
    float lineCoc = smoothstep(
            max(0.01, uTiltShiftFocusBand),
            max(0.02, uTiltShiftFocusBand + uTiltShiftTransition),
            lineDistance);

    // Fade toward the physical viewport edges, but preserve the actor's sharp focus band so a
    // camera pan that places the actor near an edge does not abruptly blur the actor itself.
    vec2 edge = min(vUv, vec2(1.0) - vUv);
    float edgeDistance = min(edge.x * aspect, edge.y);
    float edgeCoc = 1.0 - smoothstep(0.02, 0.18, edgeDistance);
    edgeCoc *= mix(0.35, 1.0, lineCoc);
    return max(lineCoc, edgeCoc * clamp(uTiltShiftEdgeBlur, 0.0, 1.0));
}

// This is a true world-space focal plane through the actor, not a spherical distance shell.
// Its normal is assembled once on the CPU from the camera orientation and then tilted vertically.
float miniatureCoc(vec3 worldPosition) {
    float planeError = abs(dot(
            worldPosition - uFocusPosition,
            normalize(uFocusPlaneNormal)));
    float depthCoc = smoothstep(
            max(0.10, uFocusRange * 0.18),
            max(0.20, uFocusRange * 0.90),
            planeError);
    return max(depthCoc, screenCoc());
}

void main() {
    vec4 center = texture(uScene, vUv);
    float depth = texture(uDepth, vUv).r;
    float tiltShift = clamp(uTiltShiftStrength, 0.0, 1.0);
    float coc;
    if (depth >= 0.999999) {
        coc = mix(0.56, max(0.66, screenCoc()), tiltShift);
    } else {
        vec3 worldPosition = reconstructWorldPosition(depth);
        coc = mix(legacyCoc(worldPosition), miniatureCoc(worldPosition), tiltShift);
    }
    coc *= clamp(uDepthOfFieldStrength, 0.0, 1.0);

    float radius = coc * max(uMaximumBlurPixels, 0.0);
    vec4 result = center;
    if (radius < 0.05) {
        result = center;
    } else {
        vec4 accumulated = center;
        float totalWeight = 1.0;
        for (int index = 0; index < 12; index++) {
            vec2 sampleUv = clamp(vUv + POISSON[index] * radius * uInverseResolution, 0.001, 0.999);
            float sampleDepth = texture(uDepth, sampleUv).r;
            float depthSimilarity = 1.0 - smoothstep(0.0012, 0.016, abs(sampleDepth - depth));
            // Never pull a foreground leaf or a dark block face through an unrelated depth edge.
            // The former minimum weight smeared those pixels into nearby terrain as a false shadow.
            float weight = depthSimilarity;
            accumulated += texture(uScene, sampleUv) * weight;
            totalWeight += weight;
        }
        result = accumulated / totalWeight;
    }
    fragColor = result;
}
