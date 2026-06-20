#version 150

in vec2 texCoord0;
out vec4 fragColor;

uniform vec2 Size;
uniform float Radius;
uniform vec4 Color;

float sdRoundRect(vec2 p, vec2 b, float r)
{
    vec2 q = abs(p) - b + vec2(r);
    return length(max(q, 0.0))
    + min(max(q.x, q.y), 0.0)
    - r;
}

void main()
{
    vec2 p = texCoord0 * Size - Size * 0.5;

    float d = sdRoundRect(
        p,
        Size * 0.5,
        Radius
    );

    float aa = fwidth(d);

    float alpha = 1.0 - smoothstep(
        0.0,
        aa,
        d
    );

    fragColor = vec4(
    Color.rgb,
    Color.a * alpha
    );
}