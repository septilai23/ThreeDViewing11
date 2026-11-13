#version 330 core
in vec2 vUV;
out vec4 FragColor;

void main() {
    vec3 horizon = vec3(0.90, 0.94, 0.98);
    vec3 zenith  = vec3(0.20, 0.45, 0.82);
    float t = clamp(vUV.y, 0.0, 1.0);
    FragColor = vec4(mix(horizon, zenith, pow(t, 0.75)), 1.0);
}
