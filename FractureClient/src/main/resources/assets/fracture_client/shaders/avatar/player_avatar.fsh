#version 460 core

struct DirectionalLight {
    vec3 direction;
    vec3 color;
    float intensity;
};

in vec2 vTexCoord;
in vec3 vNormal;
in vec4 vVertexColor;
layout(location = 0) out vec4 FragColor;

uniform sampler2D uBaseColorMap;
uniform vec4 uBaseColorFactor;
uniform int uHasVertexColor;
uniform int uDoubleSided;
uniform float uAlphaCutoff;
uniform int uEncodeSrgb;
uniform int uDirectionalLightCount;
uniform DirectionalLight uDirectionalLights[2];

vec3 linearToSrgb(vec3 value) {
    vec3 low = value * 12.92;
    vec3 high = 1.055 * pow(max(value, vec3(0.0)), vec3(1.0 / 2.4)) - 0.055;
    return mix(high, low, lessThanEqual(value, vec3(0.0031308)));
}

void main() {
    vec4 vertexColor = uHasVertexColor != 0 ? vVertexColor : vec4(1.0);
    vec4 baseColor = texture(uBaseColorMap, vTexCoord) * uBaseColorFactor * vertexColor;
    if (uAlphaCutoff > 0.0 && baseColor.a < uAlphaCutoff) {
        discard;
    }

    vec3 normal = normalize(vNormal);
    if (uDoubleSided != 0 && !gl_FrontFacing) {
        normal = -normal;
    }

    vec3 lighting = vec3(0.58);
    for (int index = 0; index < uDirectionalLightCount; index++) {
        vec3 direction = normalize(-uDirectionalLights[index].direction);
        float diffuse = max(dot(normal, direction), 0.0);
        lighting += uDirectionalLights[index].color
                * uDirectionalLights[index].intensity
                * diffuse
                * 0.22;
    }
    vec3 outputColor = baseColor.rgb * lighting;
    if (uEncodeSrgb != 0) {
        outputColor = linearToSrgb(outputColor);
    }
    FragColor = vec4(outputColor, baseColor.a);
}
