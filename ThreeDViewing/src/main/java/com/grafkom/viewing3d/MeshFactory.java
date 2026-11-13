package com.grafkom.viewing3d;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pembuat mesh. Satu data geometri (posisi, UV, indeks) dapat dibuat menjadi
 * dua versi: SMOOTH (vertex normal averaging) dan FLAT (normal per-face).
 */
public final class MeshFactory {
    private MeshFactory() {}

    /** Data geometri mentah: pos (3 float/vertex), uv (2 float/vertex), idx (segitiga CCW). */
    public static class Data {
        public final float[] pos;
        public final float[] uv;
        public final int[] idx;

        public Data(float[] pos, float[] uv, int[] idx) {
            this.pos = pos;
            this.uv = uv;
            this.idx = idx;
        }
    }

    // ------------------------------------------------------------------
    //  Pembuatan Mesh dari Data
    // ------------------------------------------------------------------

    /** Smooth shading: N_vertex = normalize( sum(N_face) ) dari semua face yang berbagi vertex. */
    public static Mesh smooth(Data d) {
        return new Mesh(d.pos, smoothNormals(d.pos, d.idx), d.uv, d.idx);
    }

    /** Flat shading: setiap segitiga memiliki 3 vertex sendiri dengan normal face yang sama. */
    public static Mesh flat(Data d) {
        List<Float> p = new ArrayList<>();
        List<Float> n = new ArrayList<>();
        List<Float> t = new ArrayList<>();
        int count = 0;
        for (int i = 0; i < d.idx.length; i += 3) {
            Vector3f a = vec(d.pos, d.idx[i]);
            Vector3f b = vec(d.pos, d.idx[i + 1]);
            Vector3f c = vec(d.pos, d.idx[i + 2]);
            Vector3f fn = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
            if (fn.length() < 1e-8f) continue;   // segitiga degenerate (kutub bola)
            fn.normalize();
            for (int k = 0; k < 3; k++) {
                int v = d.idx[i + k];
                p.add(d.pos[v * 3]); p.add(d.pos[v * 3 + 1]); p.add(d.pos[v * 3 + 2]);
                n.add(fn.x); n.add(fn.y); n.add(fn.z);
                t.add(d.uv[v * 2]); t.add(d.uv[v * 2 + 1]);
            }
            count += 3;
        }
        int[] idx = new int[count];
        for (int i = 0; i < count; i++) idx[i] = i;
        return new Mesh(toArray(p), toArray(n), toArray(t), idx);
    }

    private static float[] smoothNormals(float[] pos, int[] idx) {
        int n = pos.length / 3;
        String[] keys = new String[n];
        Map<String, float[]> acc = new HashMap<>();
        for (int i = 0; i < n; i++) {
            // vertex pada posisi yang sama (mis. jahitan UV bola) digabung agar normalnya mulus
            keys[i] = Math.round(pos[i * 3] * 10000f) + "," + Math.round(pos[i * 3 + 1] * 10000f)
                    + "," + Math.round(pos[i * 3 + 2] * 10000f);
            acc.putIfAbsent(keys[i], new float[3]);
        }
        for (int t = 0; t < idx.length; t += 3) {
            Vector3f a = vec(pos, idx[t]);
            Vector3f b = vec(pos, idx[t + 1]);
            Vector3f c = vec(pos, idx[t + 2]);
            Vector3f fn = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
            if (fn.length() < 1e-8f) continue;
            fn.normalize();
            for (int k = 0; k < 3; k++) {
                float[] s = acc.get(keys[idx[t + k]]);
                s[0] += fn.x; s[1] += fn.y; s[2] += fn.z;
            }
        }
        float[] nrm = new float[pos.length];
        for (int i = 0; i < n; i++) {
            float[] s = acc.get(keys[i]);
            Vector3f v = new Vector3f(s[0], s[1], s[2]);
            if (v.lengthSquared() > 1e-12f) v.normalize(); else v.set(0f, 1f, 0f);
            nrm[i * 3] = v.x; nrm[i * 3 + 1] = v.y; nrm[i * 3 + 2] = v.z;
        }
        return nrm;
    }

    private static Vector3f vec(float[] pos, int i) {
        return new Vector3f(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]);
    }

    private static float[] toArray(List<Float> list) {
        float[] a = new float[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return a;
    }

    // ------------------------------------------------------------------
    //  Geometri
    // ------------------------------------------------------------------

    /** UV Sphere. stacks = pembagian lintang, slices = pembagian bujur. */
    public static Data sphere(float radius, int stacks, int slices) {
        int vc = (stacks + 1) * (slices + 1);
        float[] pos = new float[vc * 3];
        float[] uv = new float[vc * 2];
        int p = 0, q = 0;
        for (int i = 0; i <= stacks; i++) {
            double phi = Math.PI * i / stacks;
            float y = (float) Math.cos(phi);
            float r = (float) Math.sin(phi);
            for (int j = 0; j <= slices; j++) {
                double theta = 2.0 * Math.PI * j / slices;
                pos[p++] = radius * r * (float) Math.cos(theta);
                pos[p++] = radius * y;
                pos[p++] = radius * r * (float) Math.sin(theta);
                uv[q++] = (float) j / slices;
                uv[q++] = 1f - (float) i / stacks;
            }
        }
        int[] idx = new int[stacks * slices * 6];
        int k = 0;
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                int a = i * (slices + 1) + j;
                int b = a + slices + 1;
                idx[k++] = a;     idx[k++] = a + 1; idx[k++] = b;       // CCW dilihat dari luar
                idx[k++] = a + 1; idx[k++] = b + 1; idx[k++] = b;
            }
        }
        return new Data(pos, uv, idx);
    }

    /** Kubus dengan 24 vertex (4 per sisi) berpusat di (0,0,0), sisi = size. */
    public static Data cube(float size) {
        float h = size / 2f;
        // tiap sisi: normal n, vektor u, vektor v dengan u x v = n  -> urutan CCW dari luar
        float[][] faces = {
                { 0, 0, 1,   1, 0, 0,   0, 1, 0},
                { 0, 0,-1,  -1, 0, 0,   0, 1, 0},
                { 1, 0, 0,   0, 0,-1,   0, 1, 0},
                {-1, 0, 0,   0, 0, 1,   0, 1, 0},
                { 0, 1, 0,   1, 0, 0,   0, 0,-1},
                { 0,-1, 0,   1, 0, 0,   0, 0, 1},
        };
        float[] pos = new float[24 * 3];
        float[] uv = new float[24 * 2];
        int[] idx = new int[36];
        float[][] corner = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        float[][] tex = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int f = 0; f < 6; f++) {
            float[] F = faces[f];
            for (int c = 0; c < 4; c++) {
                int v = f * 4 + c;
                for (int a = 0; a < 3; a++) {
                    pos[v * 3 + a] = h * (F[a] + corner[c][0] * F[3 + a] + corner[c][1] * F[6 + a]);
                }
                uv[v * 2] = tex[c][0];
                uv[v * 2 + 1] = tex[c][1];
            }
            int b = f * 4, o = f * 6;
            idx[o] = b; idx[o + 1] = b + 1; idx[o + 2] = b + 2;
            idx[o + 3] = b; idx[o + 4] = b + 2; idx[o + 5] = b + 3;
        }
        return new Data(pos, uv, idx);
    }

    /** Bidang datar (lantai) menghadap +Y, UV berulang sebanyak uvRepeat kali. */
    public static Data plane(float half, float uvRepeat) {
        float[] pos = {
                -half, 0f,  half,
                 half, 0f,  half,
                 half, 0f, -half,
                -half, 0f, -half
        };
        float[] uv = {
                0f, 0f,
                uvRepeat, 0f,
                uvRepeat, uvRepeat,
                0f, uvRepeat
        };
        int[] idx = {0, 1, 2, 0, 2, 3};
        return new Data(pos, uv, idx);
    }

    // ------------------------------------------------------------------
    //  Builder sederhana untuk geometri prosedural
    // ------------------------------------------------------------------
    private static final class Builder {
        final List<Float> pos = new ArrayList<>();
        final List<Float> uv = new ArrayList<>();
        final List<Integer> idx = new ArrayList<>();

        int vertex(float x, float y, float z, float u, float v) {
            pos.add(x); pos.add(y); pos.add(z);
            uv.add(u); uv.add(v);
            return pos.size() / 3 - 1;
        }

        void tri(int a, int b, int c) {
            idx.add(a); idx.add(b); idx.add(c);
        }

        Vector3f p(int i) {
            return new Vector3f(pos.get(i * 3), pos.get(i * 3 + 1), pos.get(i * 3 + 2));
        }

        /** Menambah segitiga; urutan dibalik jika normalnya berlawanan dengan arah 'outward'. */
        void triOut(int a, int b, int c, Vector3f outward) {
            Vector3f fn = new Vector3f(p(b)).sub(p(a)).cross(new Vector3f(p(c)).sub(p(a)));
            if (fn.dot(outward) < 0f) tri(a, c, b); else tri(a, b, c);
        }

        Data build() {
            float[] P = new float[pos.size()];
            for (int i = 0; i < P.length; i++) P[i] = pos.get(i);
            float[] U = new float[uv.size()];
            for (int i = 0; i < U.length; i++) U[i] = uv.get(i);
            int[] I = new int[idx.size()];
            for (int i = 0; i < I.length; i++) I[i] = idx.get(i);
            return new Data(P, U, I);
        }
    }

    private static float catmull(float p0, float p1, float p2, float p3, float t) {
        return 0.5f * ((2f * p1) + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t * t
                + (-p0 + 3f * p1 - 3f * p2 + p3) * t * t * t);
    }

    // ------------------------------------------------------------------
    //  Geometri kompleks
    // ------------------------------------------------------------------

    /**
     * Benda putar (surface of revolution / lathe) dari profil {radius, tinggi} yang disusun
     * dari bawah ke atas, dihaluskan dengan spline Catmull-Rom. Dipakai untuk badan dan tutup teko.
     */
    public static Data lathe(float[][] profile, int slices, int subdiv, float uRepeat) {
        List<float[]> samples = new ArrayList<>();
        int n = profile.length;
        for (int i = 0; i < n - 1; i++) {
            float[] p0 = profile[Math.max(i - 1, 0)];
            float[] p1 = profile[i];
            float[] p2 = profile[i + 1];
            float[] p3 = profile[Math.min(i + 2, n - 1)];
            for (int s = 0; s < subdiv; s++) {
                float t = s / (float) subdiv;
                samples.add(new float[]{
                        Math.max(0f, catmull(p0[0], p1[0], p2[0], p3[0], t)),
                        catmull(p0[1], p1[1], p2[1], p3[1], t)});
            }
        }
        samples.add(new float[]{Math.max(0f, profile[n - 1][0]), profile[n - 1][1]});

        int m = samples.size();
        float[] cum = new float[m];
        for (int k = 1; k < m; k++) {
            float dr = samples.get(k)[0] - samples.get(k - 1)[0];
            float dy = samples.get(k)[1] - samples.get(k - 1)[1];
            cum[k] = cum[k - 1] + (float) Math.sqrt(dr * dr + dy * dy);
        }
        Builder b = new Builder();
        for (int k = 0; k < m; k++) {
            float r = samples.get(k)[0];
            float y = samples.get(k)[1];
            float v = cum[k] / cum[m - 1];
            for (int j = 0; j <= slices; j++) {
                double theta = 2.0 * Math.PI * j / slices;
                b.vertex(r * (float) Math.cos(theta), y, r * (float) Math.sin(theta),
                        uRepeat * j / slices, v);
            }
        }
        for (int k = 0; k < m - 1; k++) {
            for (int j = 0; j < slices; j++) {
                int a = k * (slices + 1) + j;
                int c = a + slices + 1;
                b.tri(a, c, a + 1);          // CCW dari luar (profil bawah -> atas)
                b.tri(a + 1, c, c + 1);
            }
        }
        return b.build();
    }

    /** Titik pada kurva Bezier kubik. */
    public static Vector3f bezier(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float t) {
        float u = 1f - t;
        Vector3f r = new Vector3f();
        r.fma(u * u * u, p0).fma(3f * u * u * t, p1).fma(3f * u * t * t, p2).fma(t * t * t, p3);
        return r;
    }

    /** Pipa yang menyapu sepanjang jalur (path) dengan jari-jari berubah. Dipakai untuk cerat dan gagang teko. */
    public static Data tube(Vector3f[] path, float[] radius, int slices, float uRepeat, float vRepeat, boolean capEnd) {
        int n = path.length;
        Builder b = new Builder();
        Vector3f nrm = new Vector3f(0f, 0f, 1f);
        Vector3f[] tangent = new Vector3f[n];
        for (int i = 0; i < n; i++) {
            Vector3f t = new Vector3f(path[Math.min(i + 1, n - 1)]).sub(path[Math.max(i - 1, 0)]).normalize();
            tangent[i] = t;
            // parallel transport: pertahankan normal agar tegak lurus terhadap tangen
            nrm = new Vector3f(nrm).sub(new Vector3f(t).mul(nrm.dot(t))).normalize();
            Vector3f bin = new Vector3f(t).cross(nrm).normalize();
            for (int j = 0; j <= slices; j++) {
                double th = 2.0 * Math.PI * j / slices;
                Vector3f off = new Vector3f(nrm).mul((float) Math.cos(th)).fma((float) Math.sin(th), bin).mul(radius[i]);
                b.vertex(path[i].x + off.x, path[i].y + off.y, path[i].z + off.z,
                        uRepeat * j / slices, vRepeat * i / (n - 1));
            }
        }
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < slices; j++) {
                int a = i * (slices + 1) + j;
                int bb = a + 1;
                int c = a + slices + 1;
                int d = c + 1;
                Vector3f out = new Vector3f(b.p(a)).sub(path[i]);
                b.triOut(a, bb, c, out);
                b.triOut(bb, d, c, out);
            }
        }
        if (capEnd) {
            int center = b.vertex(path[n - 1].x, path[n - 1].y, path[n - 1].z, 0.5f, vRepeat);
            int base = (n - 1) * (slices + 1);
            for (int j = 0; j < slices; j++) {
                b.triOut(center, base + j, base + j + 1, tangent[n - 1]);
            }
        }
        return b.build();
    }

    /**
     * Balok berpusat di (0,0,0). uvScale > 0: tekstur berulang tiap uvScale satuan dunia;
     * uvScale <= 0: setiap sisi memuat satu tekstur penuh (UV 0..1).
     */
    public static Data box(float w, float h, float d, float uvScale) {
        float[] hv = {w / 2f, h / 2f, d / 2f};
        float[][] faces = {
                { 0, 0, 1,   1, 0, 0,   0, 1, 0},
                { 0, 0,-1,  -1, 0, 0,   0, 1, 0},
                { 1, 0, 0,   0, 0,-1,   0, 1, 0},
                {-1, 0, 0,   0, 0, 1,   0, 1, 0},
                { 0, 1, 0,   1, 0, 0,   0, 0,-1},
                { 0,-1, 0,   1, 0, 0,   0, 0, 1},
        };
        float[][] corner = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        float[][] tex = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        Builder b = new Builder();
        for (float[] F : faces) {
            float su = 0f, sv = 0f;
            for (int a = 0; a < 3; a++) {
                su += 2f * Math.abs(F[3 + a]) * hv[a];
                sv += 2f * Math.abs(F[6 + a]) * hv[a];
            }
            float uMax = uvScale > 0f ? su / uvScale : 1f;
            float vMax = uvScale > 0f ? sv / uvScale : 1f;
            int base = b.pos.size() / 3;
            for (int c = 0; c < 4; c++) {
                float[] p = new float[3];
                for (int a = 0; a < 3; a++) {
                    p[a] = hv[a] * (F[a] + corner[c][0] * F[3 + a] + corner[c][1] * F[6 + a]);
                }
                b.vertex(p[0], p[1], p[2], tex[c][0] * uMax, tex[c][1] * vMax);
            }
            b.tri(base, base + 1, base + 2);
            b.tri(base, base + 2, base + 3);
        }
        return b.build();
    }

    /**
     * Prisma segitiga (bagian atap/loteng rumah): lebar w sepanjang sumbu X, alas d sepanjang sumbu Z
     * dengan tinggi puncak r. Alas berada di y = 0 dan tidak digambar.
     */
    public static Data prism(float w, float d, float r, float uvScale) {
        float x0 = -w / 2f, x1 = w / 2f, z0 = -d / 2f, z1 = d / 2f;
        Builder b = new Builder();
        float k = 1f / uvScale;
        // sisi kiri (-X)
        int a = b.vertex(x0, 0, z0, 0f, 0f);
        int c = b.vertex(x0, 0, z1, d * k, 0f);
        int e = b.vertex(x0, r, 0, d * k / 2f, r * k);
        b.tri(a, c, e);
        // sisi kanan (+X)
        a = b.vertex(x1, 0, z0, 0f, 0f);
        c = b.vertex(x1, 0, z1, d * k, 0f);
        e = b.vertex(x1, r, 0, d * k / 2f, r * k);
        b.tri(a, e, c);
        // lereng +Z
        int s = (int) Math.ceil(Math.sqrt(r * r + d * d / 4f) * k);
        a = b.vertex(x0, 0, z1, 0f, 0f);
        c = b.vertex(x1, 0, z1, w * k, 0f);
        e = b.vertex(x1, r, 0, w * k, s);
        int f = b.vertex(x0, r, 0, 0f, s);
        b.tri(a, c, e);
        b.tri(a, e, f);
        // lereng -Z
        a = b.vertex(x1, 0, z0, 0f, 0f);
        c = b.vertex(x0, 0, z0, w * k, 0f);
        e = b.vertex(x0, r, 0, w * k, s);
        f = b.vertex(x1, r, 0, 0f, s);
        b.tri(a, c, e);
        b.tri(a, e, f);
        return b.build();
    }

    // ------------------------------------------------------------------
    //  Bagian-bagian teko (Teapot) prosedural
    // ------------------------------------------------------------------
    public static Data teapotBody() {
        float[][] profile = {
                {0.00f, 0.00f}, {0.70f, 0.00f}, {1.05f, 0.10f}, {1.30f, 0.45f}, {1.42f, 0.95f},
                {1.38f, 1.40f}, {1.15f, 1.75f}, {0.85f, 1.93f}, {0.78f, 2.00f}};
        return lathe(profile, 48, 8, 2f);
    }

    public static Data teapotLid() {
        float[][] profile = {
                {0.78f, 2.00f}, {0.80f, 2.04f}, {0.70f, 2.14f}, {0.50f, 2.26f}, {0.25f, 2.33f},
                {0.14f, 2.37f}, {0.17f, 2.45f}, {0.15f, 2.55f}, {0.00f, 2.62f}};
        return lathe(profile, 48, 8, 2f);
    }

    public static Data teapotSpout() {
        Vector3f p0 = new Vector3f(1.20f, 0.50f, 0f), p1 = new Vector3f(2.30f, 0.55f, 0f);
        Vector3f p2 = new Vector3f(2.00f, 1.55f, 0f), p3 = new Vector3f(2.65f, 2.05f, 0f);
        int n = 24;
        Vector3f[] path = new Vector3f[n];
        float[] rad = new float[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            path[i] = bezier(p0, p1, p2, p3, t);
            rad[i] = 0.36f + (0.13f - 0.36f) * t;
        }
        return tube(path, rad, 20, 1f, 1f, true);
    }

    public static Data teapotHandle() {
        Vector3f p0 = new Vector3f(-1.10f, 1.65f, 0f), p1 = new Vector3f(-3.00f, 2.15f, 0f);
        Vector3f p2 = new Vector3f(-3.05f, 0.30f, 0f), p3 = new Vector3f(-1.15f, 0.55f, 0f);
        int n = 28;
        Vector3f[] path = new Vector3f[n];
        float[] rad = new float[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            path[i] = bezier(p0, p1, p2, p3, t);
            rad[i] = 0.17f;
        }
        return tube(path, rad, 16, 1f, 3f, false);
    }
}
