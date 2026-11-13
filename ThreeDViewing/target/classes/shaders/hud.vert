#version 330 core
layout (location = 0) in vec2 aPos;

uniform vec4 uRect;     // x, y (piksel, origin kiri-atas), skala/lebar x, skala/tinggi y
uniform vec2 uScreen;   // ukuran framebuffer (piksel)

void main() {
    vec2 p = aPos * uRect.zw + uRect.xy;
    gl_Position = vec4(p.x / uScreen.x * 2.0 - 1.0, 1.0 - p.y / uScreen.y * 2.0, 0.0, 1.0);
}
