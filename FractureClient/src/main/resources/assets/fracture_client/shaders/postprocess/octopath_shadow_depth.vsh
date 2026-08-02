#version 460 core

layout(location = 0) in vec3 aPosition;
layout(location = 3) in mat4 aInstanceModel;

uniform mat4 uLightViewProjection;

void main() {
    gl_Position = uLightViewProjection * aInstanceModel * vec4(aPosition, 1.0);
}
