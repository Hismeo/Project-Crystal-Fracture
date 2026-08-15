#version 460 core

layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aTexCoord;
layout(location = 2) in vec3 aNormal;
layout(location = 4) in vec4 aColor;
layout(location = 5) in vec4 aJoints;
layout(location = 6) in vec4 aWeights;

layout(std430, binding = 7) readonly buffer JointPaletteBlock {
    mat4 uJointMatrices[];
};

layout(std430, binding = 8) readonly buffer MorphDeltaBlock {
    vec4 uMorphDeltas[];
};

layout(std430, binding = 9) readonly buffer MorphWeightBlock {
    float uMorphWeights[];
};

uniform mat4 uProjection;
uniform mat4 uView;
uniform mat4 uModel;
uniform int uSkinningEnabled;
uniform int uMorphTargetCount;
uniform int uMorphVertexCount;

out vec2 vTexCoord;
out vec3 vNormal;
out vec4 vVertexColor;

mat4 skinMatrix() {
    if (uSkinningEnabled == 0) {
        return mat4(1.0);
    }
    uvec4 joints = uvec4(aJoints + vec4(0.5));
    vec4 weights = max(aWeights, vec4(0.0));
    float totalWeight = dot(weights, vec4(1.0));
    weights = totalWeight > 0.0
            ? weights / totalWeight
            : vec4(1.0, 0.0, 0.0, 0.0);
    return uJointMatrices[joints.x] * weights.x
        + uJointMatrices[joints.y] * weights.y
        + uJointMatrices[joints.z] * weights.z
        + uJointMatrices[joints.w] * weights.w;
}

void applyMorph(inout vec3 position, inout vec3 normal) {
    for (int target = 0; target < uMorphTargetCount; target++) {
        float weight = uMorphWeights[target];
        if (weight == 0.0) {
            continue;
        }
        int base = (target * uMorphVertexCount + gl_VertexID) * 3;
        position += uMorphDeltas[base].xyz * weight;
        normal += uMorphDeltas[base + 1].xyz * weight;
    }
}

void main() {
    vec3 position = aPosition;
    vec3 localNormal = aNormal;
    applyMorph(position, localNormal);
    if (dot(localNormal, localNormal) <= 1.0e-12) {
        localNormal = aNormal;
    }
    mat4 modelSkin = uModel * skinMatrix();
    vec4 world = modelSkin * vec4(position, 1.0);
    vTexCoord = aTexCoord;
    vNormal = normalize(transpose(inverse(mat3(modelSkin))) * localNormal);
    vVertexColor = aColor;
    gl_Position = uProjection * uView * world;
}
