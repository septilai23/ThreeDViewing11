package com.grafkom.viewing3d;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Kumpulan bagian (part) objek 3D dalam scene. Tiap part memiliki mesh smooth dan flat,
 * tekstur, matriks model, warna dasar (saat tekstur OFF), dan kekuatan specular.
 */
public class Scene {

    private static class Part {
        Mesh smoothMesh;
        Mesh flatMesh;
        Texture2D texture;
        Matrix4f model;
        Vector3f color;
        float specular;
    }

    private final List<Part> parts = new ArrayList<>();
    private int triangleCount = 0;

    /**
     * @param smoothable true  = bentuk lengkung: dibuat versi smooth dan flat (tombol F berpengaruh);
     *                   false = bentuk bersisi tajam (balok, prisma): selalu memakai normal per-face.
     */
    public void add(MeshFactory.Data data, boolean smoothable, Texture2D texture,
                    Matrix4f model, Vector3f color, float specular) {
        Part p = new Part();
        p.flatMesh = MeshFactory.flat(data);
        p.smoothMesh = smoothable ? MeshFactory.smooth(data) : p.flatMesh;
        p.texture = texture;
        p.model = new Matrix4f(model);
        p.color = color;
        p.specular = specular;
        parts.add(p);
        triangleCount += data.idx.length / 3;
    }

    public int getTriangleCount() {
        return triangleCount;
    }

    /** material = true pada Pass 2 (kamera); false pada Pass 1 (depth, tanpa tekstur dan warna). */
    public void render(ShaderProgram shader, boolean smooth, boolean material) {
        for (Part p : parts) {
            shader.setMat4("model", p.model);
            if (material) {
                shader.setVec3("objectColor", p.color);
                shader.setFloat("specularStrength", p.specular);
                p.texture.bind(0);
            }
            (smooth ? p.smoothMesh : p.flatMesh).draw();
        }
    }
}
