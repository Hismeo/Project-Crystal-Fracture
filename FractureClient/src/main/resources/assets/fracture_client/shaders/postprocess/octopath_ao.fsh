#version 460 core

in vec2 vUv;
layout(location = 0) out vec4 fragColor;

uniform sampler2D uDepth;
uniform mat4 uInverseViewProjection;
uniform vec3 uCameraPosition;
uniform float uStrength;
uniform float uRadiusPixels;

const vec2 AO_DIRECTIONS[8] = vec2[](
    vec2(1.0, 0.0), vec2(0.7071068, 0.7071068),
    vec2(0.0, 1.0), vec2(-0.7071068, 0.7071068),
    vec2(-1.0, 0.0), vec2(-0.7071068, -0.7071068),
    vec2(0.0, -1.0), vec2(0.7071068, -0.7071068)
);

vec2 texelUv(ivec2 texel, ivec2 resolution) {
    return (vec2(texel) + vec2(0.5)) / vec2(resolution);
}

vec3 reconstructWorldPositionAt(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 world = uInverseViewProjection * clip;
    return world.xyz / max(abs(world.w), 0.00001);
}

vec3 reconstructWorldPosition(ivec2 texel, ivec2 resolution, float depth) {
    return reconstructWorldPositionAt(texelUv(texel, resolution), depth);
}

void main() {
    float requestedStrength = clamp(uStrength, 0.0, 0.55);
    ivec2 depthResolution = textureSize(uDepth, 0);
    ivec2 centerTexel = clamp(
            ivec2(vUv * vec2(depthResolution)),
            ivec2(0),
            depthResolution - ivec2(1));
    float centerDepth = texelFetch(uDepth, centerTexel, 0).r;
    if (requestedStrength <= 0.0001 || centerDepth >= 0.999999
            || centerTexel.x < 1 || centerTexel.y < 1
            || centerTexel.x >= depthResolution.x - 1
            || centerTexel.y >= depthResolution.y - 1) {
        // G stores the original depth and A marks this texel as an AO receiver. Lighting uses
        // both during the full-resolution bilateral upsample, so an invalid cell never bleeds.
        fragColor = vec4(0.0, centerDepth, 0.0, 0.0);
        return;
    }

    ivec2 rightTexel = centerTexel + ivec2(1, 0);
    ivec2 leftTexel = centerTexel - ivec2(1, 0);
    ivec2 upTexel = centerTexel + ivec2(0, 1);
    ivec2 downTexel = centerTexel - ivec2(0, 1);
    float rightDepth = texelFetch(uDepth, rightTexel, 0).r;
    float leftDepth = texelFetch(uDepth, leftTexel, 0).r;
    float upDepth = texelFetch(uDepth, upTexel, 0).r;
    float downDepth = texelFetch(uDepth, downTexel, 0).r;
    if (rightDepth >= 0.999999 || leftDepth >= 0.999999
            || upDepth >= 0.999999 || downDepth >= 0.999999) {
        fragColor = vec4(0.0, centerDepth, 0.0, 0.0);
        return;
    }

    vec3 center = reconstructWorldPosition(centerTexel, depthResolution, centerDepth);
    vec3 right = reconstructWorldPosition(rightTexel, depthResolution, rightDepth);
    vec3 left = reconstructWorldPosition(leftTexel, depthResolution, leftDepth);
    vec3 up = reconstructWorldPosition(upTexel, depthResolution, upDepth);
    vec3 down = reconstructWorldPosition(downTexel, depthResolution, downDepth);
    vec3 sameDepthRight = reconstructWorldPosition(rightTexel, depthResolution, centerDepth);
    vec3 sameDepthUp = reconstructWorldPosition(upTexel, depthResolution, centerDepth);
    float pixelScale = max(
            0.0025,
            0.5 * (length(sameDepthRight - center) + length(sameDepthUp - center)));
    float continuityLimit = max(0.08, min(0.75, pixelScale * 7.0));
    float rightDistance = length(right - center);
    float leftDistance = length(left - center);
    float upDistance = length(up - center);
    float downDistance = length(down - center);
    bool rightContinuous = rightDistance <= continuityLimit;
    bool leftContinuous = leftDistance <= continuityLimit;
    bool upContinuous = upDistance <= continuityLimit;
    bool downContinuous = downDistance <= continuityLimit;
    int continuousCount = (rightContinuous ? 1 : 0)
            + (leftContinuous ? 1 : 0)
            + (upContinuous ? 1 : 0)
            + (downContinuous ? 1 : 0);
    if (continuousCount < 3 || (!rightContinuous && !leftContinuous)
            || (!upContinuous && !downContinuous)) {
        fragColor = vec4(0.0, centerDepth, 0.0, 0.0);
        return;
    }

    // Choose the uninterrupted side for each derivative. A terrain pixel beside a wall keeps
    // its floor normal from the other side, while a cutout or entity silhouette loses too many
    // neighbours to become an AO receiver.
    vec3 dx = rightContinuous && (!leftContinuous || rightDistance <= leftDistance)
            ? right - center
            : center - left;
    vec3 dy = upContinuous && (!downContinuous || upDistance <= downDistance)
            ? up - center
            : center - down;
    vec3 normal = cross(dx, dy);
    float normalLength = length(normal);
    if (normalLength <= 0.00001) {
        fragColor = vec4(0.0, centerDepth, 0.0, 0.0);
        return;
    }
    normal /= normalLength;
    vec3 toCamera = uCameraPosition - center;
    float cameraDistance = length(toCamera);
    if (cameraDistance <= 0.0001) {
        fragColor = vec4(0.0, centerDepth, 0.0, 0.0);
        return;
    }
    vec3 cameraDirection = toCamera / cameraDistance;
    if (dot(normal, cameraDirection) < 0.0) {
        normal = -normal;
    }

    // The final Minecraft target has no material/opacity buffer. Restricting receivers to a
    // stable upward plane is what keeps rails, glass, foliage borders and character silhouettes
    // from acquiring a dirty SSAO rim.
    float receiverGate = smoothstep(0.58, 0.80, normal.y);
    float planeTolerance = max(0.035, min(0.22, pixelScale * 2.2));
    if (receiverGate <= 0.0001
            || (rightContinuous && abs(dot(normal, right - center)) > planeTolerance)
            || (leftContinuous && abs(dot(normal, left - center)) > planeTolerance)
            || (upContinuous && abs(dot(normal, up - center)) > planeTolerance)
            || (downContinuous && abs(dot(normal, down - center)) > planeTolerance)) {
        fragColor = vec4(0.0, centerDepth, 0.0, 0.0);
        return;
    }

    float screenRadius = clamp(uRadiusPixels, 1.0, 12.0);
    // Keep the visual reach around one block despite camera altitude; this reads as contact and
    // cavity depth rather than another broad lighting gradient.
    float worldRadius = clamp(pixelScale * screenRadius, 0.75, 1.35);
    float occlusion = 0.0;
    for (int directionIndex = 0; directionIndex < 8; directionIndex++) {
        float directionOcclusion = 0.0;
        for (int ringIndex = 0; ringIndex < 2; ringIndex++) {
            float ringRadius = screenRadius * (ringIndex == 0 ? 0.5 : 1.0);
            ivec2 sampleTexel = centerTexel + ivec2(round(AO_DIRECTIONS[directionIndex] * ringRadius));
            if (sampleTexel.x < 0 || sampleTexel.y < 0
                    || sampleTexel.x >= depthResolution.x || sampleTexel.y >= depthResolution.y) {
                continue;
            }
            float sampleDepth = texelFetch(uDepth, sampleTexel, 0).r;
            if (sampleDepth >= 0.999999) {
                continue;
            }
            vec3 samplePosition = reconstructWorldPosition(sampleTexel, depthResolution, sampleDepth);
            vec3 delta = samplePosition - center;
            float heightAboveReceiver = dot(normal, delta);
            vec3 planeOffset = delta - normal * heightAboveReceiver;
            float planeDistance = length(planeOffset);
            // A valid occluder is above the locally flat receiver, remains in the cavity-sized
            // neighbourhood, and is not a far background pixel seen through a screen edge.
            if (heightAboveReceiver <= worldRadius * 0.04
                    || planeDistance >= worldRadius * 1.10
                    || dot(cameraDirection, delta) <= -pixelScale) {
                continue;
            }
            float heightTerm = smoothstep(
                    worldRadius * 0.04,
                    worldRadius * 0.24,
                    heightAboveReceiver);
            float rangeTerm = 1.0 - smoothstep(
                    worldRadius * 0.30,
                    worldRadius * 1.10,
                    planeDistance);
            directionOcclusion = max(directionOcclusion, heightTerm * rangeTerm);
        }
        occlusion += directionOcclusion;
    }

    // Max per direction prevents a stair edge from accumulating into a black band. The cap
    // leaves the result as ambient contact depth, not a replacement for the shadow map.
    float ambientOcclusion = min(0.18, occlusion * 0.125 * requestedStrength * receiverGate);
    fragColor = vec4(ambientOcclusion, centerDepth, 0.0, 1.0);
}
