package com.grafkom.viewing3d;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBEasyFont;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33.*;

/** Menggambar teks (STBEasyFont) dan kotak berwarna untuk HUD di atas scene 3D. */
public class TextRenderer {
    private static final int BUFFER_BYTES = 65536;
    private static final int MAX_QUADS = BUFFER_BYTES / 64;   // 1 quad = 4 vertex x 16 byte

    private final ShaderProgram shader = new ShaderProgram("/shaders/hud.vert", "/shaders/hud.frag");
    private final ByteBuffer textBuffer = BufferUtils.createByteBuffer(BUFFER_BYTES);
    private final int textVao, textVbo, rectVao;

    public TextRenderer() {
        // ---- VAO teks (vertex dari stb_easy_font: x, y, z, warna = 16 byte) ----
        textVao = glGenVertexArrays();
        textVbo = glGenBuffers();
        int ebo = glGenBuffers();
        glBindVertexArray(textVao);
        glBindBuffer(GL_ARRAY_BUFFER, textVbo);
        glBufferData(GL_ARRAY_BUFFER, (long) BUFFER_BYTES, GL_STREAM_DRAW);
        int[] idx = new int[MAX_QUADS * 6];
        for (int q = 0; q < MAX_QUADS; q++) {
            int v = q * 4;
            int i = q * 6;
            idx[i] = v; idx[i + 1] = v + 1; idx[i + 2] = v + 2;
            idx[i + 3] = v; idx[i + 4] = v + 2; idx[i + 5] = v + 3;
        }
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 16, 0L);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);

        // ---- VAO kotak (quad satuan 0..1) ----
        rectVao = glGenVertexArrays();
        int rvbo = glGenBuffers();
        int rebo = glGenBuffers();
        glBindVertexArray(rectVao);
        glBindBuffer(GL_ARRAY_BUFFER, rvbo);
        glBufferData(GL_ARRAY_BUFFER, new float[]{0, 0, 1, 0, 1, 1, 0, 1}, GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, rebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, new int[]{0, 1, 2, 0, 2, 3}, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 8, 0L);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);
    }

    /** Lebar teks dalam piksel pada skala 1. */
    public int width(String text) {
        return STBEasyFont.stb_easy_font_width(text);
    }

    public void drawText(String text, float x, float y, float scale, float[] c, int screenW, int screenH) {
        if (text.isEmpty()) return;
        textBuffer.clear();
        int quads = STBEasyFont.stb_easy_font_print(0f, 0f, text, null, textBuffer);
        if (quads <= 0) return;
        textBuffer.position(0);
        textBuffer.limit(quads * 64);

        shader.use();
        shader.setVec2("uScreen", screenW, screenH);
        shader.setVec4("uRect", x, y, scale, scale);
        shader.setVec4("uColor", c[0], c[1], c[2], c[3]);
        glBindVertexArray(textVao);
        glBindBuffer(GL_ARRAY_BUFFER, textVbo);
        glBufferData(GL_ARRAY_BUFFER, textBuffer, GL_STREAM_DRAW);
        glDrawElements(GL_TRIANGLES, quads * 6, GL_UNSIGNED_INT, 0L);
        glBindVertexArray(0);
        textBuffer.clear();
    }

    public void drawRect(float x, float y, float w, float h, float[] c, int screenW, int screenH) {
        shader.use();
        shader.setVec2("uScreen", screenW, screenH);
        shader.setVec4("uRect", x, y, w, h);
        shader.setVec4("uColor", c[0], c[1], c[2], c[3]);
        glBindVertexArray(rectVao);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0L);
        glBindVertexArray(0);
    }
}
