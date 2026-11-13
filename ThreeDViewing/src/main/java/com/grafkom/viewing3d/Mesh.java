package com.grafkom.viewing3d;

import static org.lwjgl.opengl.GL33.*;

/** Mesh 3D: posisi (location 0), normal (location 1), koordinat UV (location 2). */
public class Mesh {
    private final int vao;
    private final int indexCount;

    public Mesh(float[] pos, float[] nrm, float[] uv, int[] idx) {
        int n = pos.length / 3;
        float[] data = new float[n * 8];
        for (int i = 0; i < n; i++) {
            data[i * 8]     = pos[i * 3];
            data[i * 8 + 1] = pos[i * 3 + 1];
            data[i * 8 + 2] = pos[i * 3 + 2];
            data[i * 8 + 3] = nrm[i * 3];
            data[i * 8 + 4] = nrm[i * 3 + 1];
            data[i * 8 + 5] = nrm[i * 3 + 2];
            data[i * 8 + 6] = uv[i * 2];
            data[i * 8 + 7] = uv[i * 2 + 1];
        }
        indexCount = idx.length;

        vao = glGenVertexArrays();
        int vbo = glGenBuffers();
        int ebo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx, GL_STATIC_DRAW);

        int stride = 8 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glBindVertexArray(0);
    }

    public void draw() {
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L);
        glBindVertexArray(0);
    }
}
