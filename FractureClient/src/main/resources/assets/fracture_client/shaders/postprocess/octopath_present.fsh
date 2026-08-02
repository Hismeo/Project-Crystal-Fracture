#version 460 core

in vec2 vUv;
layout(location = 0) out vec4 fragColor;

uniform sampler2D uScene;
uniform sampler2D uBloom;
uniform float uExposure;
uniform float uBloomIntensity;
uniform float uHighlightCompression;
uniform float uStageVignetteStrength;
uniform vec2 uStageFocusUv;

// Keeps Minecraft's existing mid-tones intact while smoothly rolling off only over-bright HDR
// additions. This avoids applying a second full ACES curve after the input has been decoded into
// linear space.
float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

// Roll off high luminance while retaining its hue. Per-channel compression would turn sunlight
// and warm lamps white; the luminance form keeps their colourfulness intact.
vec3 preserveMidtonesAndCompressHighlights(vec3 color) {
    const float shoulderStart = 0.70;
    float originalLuminance = luminance(color);
    float highlights = max(originalLuminance - shoulderStart, 0.0);
    float compressedLuminance = shoulderStart
            + highlights / (1.0 + highlights / max(uHighlightCompression, 0.001));
    if (originalLuminance <= shoulderStart) {
        compressedLuminance = originalLuminance;
    }
    return color * (compressedLuminance / max(originalLuminance, 0.00001));
}

// Apply the peripheral falloff after Bloom has joined the scene. It follows the actor rather
// than being locked to the monitor centre, so a camera pan keeps the character as the visual
// anchor and does not make a bright edge glow look detached from the stage.
float stageVignetteMask(vec2 uv) {
    vec2 centered = (uv - uStageFocusUv) * 2.0;
    ivec2 resolution = textureSize(uScene, 0);
    centered.x *= float(resolution.x) / max(float(resolution.y), 1.0);
    return smoothstep(0.20, 1.75, dot(centered, centered));
}

void main() {
    vec3 hdrColor = texture(uScene, vUv).rgb * max(uExposure, 0.001);
    hdrColor += texture(uBloom, vUv).rgb * max(uBloomIntensity, 0.0);
    hdrColor *= 1.0 - 0.30 * clamp(uStageVignetteStrength, 0.0, 1.0)
            * stageVignetteMask(vUv);
    vec3 displayLinear = clamp(preserveMidtonesAndCompressHighlights(hdrColor), 0.0, 1.0);
    fragColor = vec4(pow(displayLinear, vec3(1.0 / 2.2)), 1.0);
}
