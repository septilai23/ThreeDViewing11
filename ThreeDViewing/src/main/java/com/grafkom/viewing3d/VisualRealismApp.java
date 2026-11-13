package com.grafkom.viewing3d;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.stb.STBImageWrite.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * 3D Visual Realism Suite (Grafika Komputer Minggu 11).
 *
 * Kontrol:
 *  B = Phong / Blinn-Phong     F = Flat / Smooth        Z = Z-Buffer ON/OFF   C = Back-Face Culling ON/OFF
 *  T = Tekstur ON/OFF          N = Nearest / Mipmap     H = Shadow ON/OFF     K = ganti nilai Shadow Bias
 *  U = PCF (bayangan halus)    V = Wireframe            L = cahaya berputar   I = tampilkan/sembunyikan HUD
 *  P = simpan screenshot       G = benchmark FPS otomatis
 *  Mouse drag kiri / panah = putar kamera, scroll / W,S = zoom, ESC = keluar
 */
public class VisualRealismApp {

    // ---------- window ----------
    private long window;
    private int fbW = 1280, fbH = 720;   // ukuran framebuffer (piksel)
    private int winW = 1280;             // ukuran window (untuk skala HUD pada layar HiDPI)

    // ---------- state fitur (sesuai tombol) ----------
    private boolean blinn = true;      // B
    private boolean smooth = true;     // F
    private boolean depthOn = true;    // Z
    private boolean cullOn = true;     // C
    private boolean texOn = true;      // T
    private boolean mipOn = true;      // N
    private boolean shadowOn = true;   // H
    private int biasMode = 2;          // K  (0 = tanpa bias, 1 = 0.005, 2 = dinamis, 3 = 0.05)
    private boolean pcfOn = true;      // U
    private boolean wire = false;      // V
    private boolean lightOrbit = false;// L
    private boolean showHud = true;    // I
    private static final String[] BIAS_NAMES = {"0 (tanpa bias)", "0.005 konstan", "dinamis", "0.05 konstan"};
    private static final String[] BIAS_SHORT = {"0", "0.005", "dinamis", "0.05"};

    // ---------- resource ----------
    private ShaderProgram phong, depthShader, skyShader;
    private ShadowMapFBO shadowFBO;
    private TextRenderer hud;
    private final Camera camera = new Camera();
    private final Scene scene = new Scene();
    private final List<Texture2D> textures = new ArrayList<>();
    private int skyVao;

    // ---------- cahaya ----------
    private float lightAngle = 0f;
    private final Vector3f lightPos = new Vector3f(-8f, 8f, 6f);
    private static final float LIGHT_RADIUS = 10f, LIGHT_HEIGHT = 8f;
    private static final float LIGHT_BASE = (float) Math.atan2(6.0, -8.0);

    // ---------- input ----------
    private boolean dragging = false;
    private double lastX, lastY;
    private boolean screenshotRequested = false;
    private int shotCount = 0;

    // ---------- FPS & benchmark ----------
    private int fpsFrames = 0;
    private double fpsTimer = 0, currentFps = 0;
    private static final String[] BENCH_NAMES = {
            "Baseline (semua fitur aktif)", "Phong (B = OFF)", "Flat Shading (F = OFF)",
            "Culling OFF (C = OFF)", "Texture Nearest (N = OFF)", "Shadow Mapping OFF (H = OFF)",
            "Z-Buffer OFF (Z = OFF)"};
    private static final double BENCH_SECONDS = 10.0;
    private boolean benchActive = false;
    private int benchIndex = 0, benchFrames = 0;
    private double benchStart = 0;
    private final double[] benchFps = new double[BENCH_NAMES.length];

    // ---------- warna HUD ----------
    private static final float[] C_TITLE = {0.45f, 0.85f, 1.00f, 1f};
    private static final float[] C_HEAD  = {1.00f, 0.85f, 0.35f, 1f};
    private static final float[] C_ON    = {0.45f, 1.00f, 0.55f, 1f};
    private static final float[] C_OFF   = {1.00f, 0.50f, 0.50f, 1f};
    private static final float[] C_FORM  = {0.78f, 0.88f, 1.00f, 1f};
    private static final float[] C_TEXT  = {0.92f, 0.92f, 0.92f, 1f};
    private static final float[] C_PANEL = {0.04f, 0.06f, 0.10f, 0.72f};

    public static void main(String[] args) {
        new VisualRealismApp().run();
    }

    private void run() {
        init();
        loop();
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    // =====================================================================
    //  Inisialisasi
    // =====================================================================
    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("GLFW gagal diinisialisasi");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);   // wajib untuk macOS
        glfwWindowHint(GLFW_SAMPLES, 4);                          // anti-aliasing MSAA 4x

        window = glfwCreateWindow(1280, 720, "3D Visual Realism Suite", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Window GLFW gagal dibuat");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        glfwSwapInterval(0);   // VSync mati agar FPS dapat diukur
        glEnable(GL_MULTISAMPLE);

        // ---- shader & FBO ----
        phong = new ShaderProgram("/shaders/phong.vert", "/shaders/phong.frag");
        depthShader = new ShaderProgram("/shaders/depth.vert", "/shaders/depth.frag");
        skyShader = new ShaderProgram("/shaders/sky.vert", "/shaders/sky.frag");
        shadowFBO = new ShadowMapFBO();
        hud = new TextRenderer();
        skyVao = glGenVertexArrays();

        buildScene();

        // ---- state OpenGL awal ----
        HSRManager.setupDepthTest(depthOn);
        HSRManager.setupBackFaceCulling(cullOn);

        // ---- callback input ----
        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (action != GLFW_PRESS) return;
            if (key == GLFW_KEY_ESCAPE) {
                glfwSetWindowShouldClose(w, true);
                return;
            }
            if (benchActive) return;   // saat benchmark, abaikan tombol lain
            switch (key) {
                case GLFW_KEY_B -> blinn = !blinn;
                case GLFW_KEY_F -> smooth = !smooth;
                case GLFW_KEY_Z -> { depthOn = !depthOn; HSRManager.setupDepthTest(depthOn); }
                case GLFW_KEY_C -> { cullOn = !cullOn; HSRManager.setupBackFaceCulling(cullOn); }
                case GLFW_KEY_T -> texOn = !texOn;
                case GLFW_KEY_N -> { mipOn = !mipOn; applyFilterMode(); }
                case GLFW_KEY_H -> shadowOn = !shadowOn;
                case GLFW_KEY_K -> biasMode = (biasMode + 1) % 4;
                case GLFW_KEY_U -> pcfOn = !pcfOn;
                case GLFW_KEY_V -> wire = !wire;
                case GLFW_KEY_L -> lightOrbit = !lightOrbit;
                case GLFW_KEY_I -> showHud = !showHud;
                case GLFW_KEY_P -> screenshotRequested = true;
                case GLFW_KEY_G -> startBenchmark();
                default -> { }
            }
        });
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) dragging = (action == GLFW_PRESS);
        });
        glfwSetCursorPosCallback(window, (w, x, y) -> {
            if (dragging && !benchActive) camera.rotate((float) (x - lastX), (float) (y - lastY));
            lastX = x;
            lastY = y;
        });
        glfwSetScrollCallback(window, (w, dx, dy) -> {
            if (!benchActive) camera.zoom((float) dy);
        });

        System.out.println("=== 3D Visual Realism Suite ===");
        System.out.println("Jumlah segitiga dalam scene: " + scene.getTriangleCount());
        System.out.println("B Phong/Blinn | F Flat/Smooth | Z Z-Buffer | C Culling | T Tekstur | N Filtering");
        System.out.println("H Shadow | K Bias | U PCF | V Wireframe | L Cahaya | I HUD | P Screenshot | G Benchmark");
    }

    private Texture2D loadTexture(String name) {
        Texture2D t = new Texture2D("/textures/" + name + ".png");
        textures.add(t);
        return t;
    }

    private void applyFilterMode() {
        for (Texture2D t : textures) t.setFilterMode(mipOn);
    }

    // =====================================================================
    //  Scene: Teko 3D (Teapot) + Bola (Earth) + Rumah 3D + Lantai
    // =====================================================================
    private void buildScene() {
        Texture2D porcelain = loadTexture("porcelain");
        Texture2D earth = loadTexture("earth");
        Texture2D brick = loadTexture("brick");
        Texture2D roof = loadTexture("roof");
        Texture2D wood = loadTexture("wood");
        Texture2D windowTex = loadTexture("window");
        Texture2D floor = loadTexture("floor");

        // ---------- 1. TEKO (Teapot) : badan, tutup, cerat, gagang ----------
        Matrix4f teapot = new Matrix4f().translate(-0.3f, 0f, 2.0f).rotateY(-0.35f);
        Vector3f white = new Vector3f(0.93f, 0.93f, 0.97f);
        scene.add(MeshFactory.teapotBody(), true, porcelain, teapot, white, 0.9f);
        scene.add(MeshFactory.teapotLid(), true, porcelain, teapot, white, 0.9f);
        scene.add(MeshFactory.teapotSpout(), true, porcelain, teapot, white, 0.9f);
        scene.add(MeshFactory.teapotHandle(), true, porcelain, teapot, white, 0.9f);

        // ---------- 2. BOLA bumi (resolusi rendah agar faset Flat Shading terlihat) ----------
        Matrix4f ball = new Matrix4f().translate(5.2f, 1.4f, -0.6f).scale(1.4f);
        scene.add(MeshFactory.sphere(1f, 16, 24), true, earth, ball, new Vector3f(0.20f, 0.50f, 0.85f), 0.6f);

        // ---------- 3. RUMAH 3D ----------
        final float W = 3.6f, D = 3.0f, H = 2.2f, R = 1.3f;
        Matrix4f house = new Matrix4f().translate(-6.0f, 0f, -3.6f).rotateY((float) Math.toRadians(35.0));
        Vector3f wallCol = new Vector3f(0.72f, 0.33f, 0.24f);
        Vector3f roofCol = new Vector3f(0.36f, 0.40f, 0.48f);
        Vector3f woodCol = new Vector3f(0.45f, 0.30f, 0.17f);
        Vector3f glassCol = new Vector3f(0.55f, 0.75f, 0.92f);

        scene.add(MeshFactory.box(W, H, D, 3.0f), false, brick,
                new Matrix4f(house).translate(0f, H / 2f, 0f), wallCol, 0.08f);
        scene.add(MeshFactory.prism(W, D, R, 3.0f), false, brick,
                new Matrix4f(house).translate(0f, H, 0f), wallCol, 0.08f);

        // dua lereng atap (papan tipis) dengan sedikit overhang
        float len = (float) Math.sqrt(R * R + D * D / 4f);
        float ang = (float) Math.atan2(R, D / 2f);
        float sn = R / len, cs = (D / 2f) / len;
        Vector3f dirP = new Vector3f(0f, -sn, cs), norP = new Vector3f(0f, cs, sn);
        Vector3f cP = new Vector3f(0f, H + R / 2f, D / 4f).fma(0.175f, dirP).fma(0.06f, norP);
        scene.add(MeshFactory.box(W + 0.5f, 0.12f, len + 0.35f, 2.5f), false, roof,
                new Matrix4f(house).translate(cP).rotateX(ang), roofCol, 0.2f);
        Vector3f dirM = new Vector3f(0f, -sn, -cs), norM = new Vector3f(0f, cs, -sn);
        Vector3f cM = new Vector3f(0f, H + R / 2f, -D / 4f).fma(0.175f, dirM).fma(0.06f, norM);
        scene.add(MeshFactory.box(W + 0.5f, 0.12f, len + 0.35f, 2.5f), false, roof,
                new Matrix4f(house).translate(cM).rotateX(-ang), roofCol, 0.2f);
        scene.add(MeshFactory.box(W + 0.5f, 0.16f, 0.28f, 2.5f), false, roof,
                new Matrix4f(house).translate(0f, H + R + 0.07f, 0f), roofCol, 0.2f);

        // cerobong, pintu, dan jendela
        scene.add(MeshFactory.box(0.5f, 1.2f, 0.5f, 1.5f), false, brick,
                new Matrix4f(house).translate(-1.1f, H + 0.9f, -0.6f), wallCol, 0.08f);
        scene.add(MeshFactory.box(0.8f, 1.5f, 0.10f, 0f), false, wood,
                new Matrix4f(house).translate(0.9f, 0.75f, D / 2f + 0.04f), woodCol, 0.10f);
        scene.add(MeshFactory.box(0.8f, 0.7f, 0.08f, 0f), false, windowTex,
                new Matrix4f(house).translate(-0.9f, 1.3f, D / 2f + 0.03f), glassCol, 0.9f);
        scene.add(MeshFactory.box(0.08f, 0.7f, 0.8f, 0f), false, windowTex,
                new Matrix4f(house).translate(-W / 2f - 0.03f, 1.3f, 0f), glassCol, 0.9f);

        // ---------- 4. LANTAI (ground plane): digambar paling akhir ----------
        scene.add(MeshFactory.plane(10f, 10f), true, floor, new Matrix4f(),
                new Vector3f(0.78f, 0.76f, 0.72f), 0.25f);
    }

    // =====================================================================
    //  Game loop
    // =====================================================================
    private void loop() {
        double last = glfwGetTime();
        int[] fw = new int[1];
        int[] fh = new int[1];
        int[] ww = new int[1];
        int[] wh = new int[1];
        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float dt = (float) (now - last);
            last = now;

            glfwPollEvents();
            if (!benchActive) pollKeyboardCamera(dt);

            if (lightOrbit) lightAngle += dt * 0.6f;
            float a = LIGHT_BASE + lightAngle;
            lightPos.set(LIGHT_RADIUS * (float) Math.cos(a), LIGHT_HEIGHT, LIGHT_RADIUS * (float) Math.sin(a));

            glfwGetFramebufferSize(window, fw, fh);
            glfwGetWindowSize(window, ww, wh);
            fbW = Math.max(1, fw[0]);
            fbH = Math.max(1, fh[0]);
            winW = Math.max(1, ww[0]);

            render();
            if (showHud) drawHud();

            if (screenshotRequested) {
                saveScreenshot();
                screenshotRequested = false;
            }
            glfwSwapBuffers(window);

            updateFpsAndTitle(now);
            updateBenchmark();
        }
    }

    private void pollKeyboardCamera(float dt) {
        if (glfwGetKey(window, GLFW_KEY_LEFT) == GLFW_PRESS)  camera.rotate(-200f * dt, 0f);
        if (glfwGetKey(window, GLFW_KEY_RIGHT) == GLFW_PRESS) camera.rotate(200f * dt, 0f);
        if (glfwGetKey(window, GLFW_KEY_UP) == GLFW_PRESS)    camera.rotate(0f, -200f * dt);
        if (glfwGetKey(window, GLFW_KEY_DOWN) == GLFW_PRESS)  camera.rotate(0f, 200f * dt);
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS)     camera.zoom(6f * dt);
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS)     camera.zoom(-6f * dt);
    }

    // =====================================================================
    //  Render dua pass (Shadow Mapping)
    // =====================================================================
    private void render() {
        // Matriks cahaya (directional light -> proyeksi ortografik)
        Matrix4f lightProj = new Matrix4f().ortho(-14f, 14f, -14f, 14f, 1f, 30f);
        Matrix4f lightView = new Matrix4f().lookAt(lightPos, new Vector3f(0f, 0f, 0f), new Vector3f(0f, 1f, 0f));
        Matrix4f lightSpace = new Matrix4f(lightProj).mul(lightView);

        // ---------- PASS 1: depth dari sudut pandang cahaya ----------
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        HSRManager.setupDepthTest(true);          // wajib aktif walau tombol Z mati
        shadowFBO.bindForWriting();
        depthShader.use();
        depthShader.setMat4("lightSpaceMatrix", lightSpace);
        scene.render(depthShader, smooth, false);
        shadowFBO.unbind(fbW, fbH);

        // ---------- PASS 2: render normal dari kamera ----------
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        glClearColor(0.05f, 0.06f, 0.09f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        if (!wire) drawSky();

        HSRManager.setupDepthTest(depthOn);       // kembalikan sesuai tombol Z
        HSRManager.setupBackFaceCulling(cullOn);
        glPolygonMode(GL_FRONT_AND_BACK, wire ? GL_LINE : GL_FILL);

        phong.use();
        phong.setMat4("view", camera.getViewMatrix());
        phong.setMat4("projection", camera.getProjectionMatrix(fbW / (float) fbH));
        phong.setMat4("lightSpaceMatrix", lightSpace);
        phong.setVec3("lightPos", lightPos);
        phong.setVec3("viewPos", camera.getPosition());
        phong.setVec3("lightColor", 1.0f, 0.97f, 0.90f);
        phong.setBool("useBlinnPhong", blinn);
        phong.setBool("useTexture", texOn);
        phong.setBool("useShadow", shadowOn);
        phong.setBool("usePCF", pcfOn);
        phong.setInt("biasMode", biasMode);

        phong.setInt("textureSampler", 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, shadowFBO.getDepthTextureID());
        phong.setInt("shadowMap", 1);
        glActiveTexture(GL_TEXTURE0);

        scene.render(phong, smooth, true);
    }

    /** Latar langit gradasi (segitiga layar penuh, tanpa depth). */
    private void drawSky() {
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        skyShader.use();
        glBindVertexArray(skyVao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
    }

    // =====================================================================
    //  HUD: status fitur + rumus yang sedang aktif
    // =====================================================================
    private void drawHud() {
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        float ui = fbW / (float) winW;     // skala layar HiDPI
        float s = 1.55f * ui;              // skala huruf
        float lh = 12.5f * s;              // tinggi baris
        float pad = 10f * ui;

        List<Object[]> lines = new ArrayList<>();
        lines.add(new Object[]{"3D VISUAL REALISM SUITE  -  Grafika Komputer Minggu 11", C_TITLE});
        lines.add(new Object[]{" ", C_TEXT});
        lines.add(new Object[]{"1. SHADING MODEL (B): ", C_HEAD, blinn ? "Blinn-Phong" : "Phong", C_ON});
        lines.add(new Object[]{blinn ? "   I = ka*Ia + kd*Id*(N.L) + ks*Is*(N.H)^32"
                                     : "   I = ka*Ia + kd*Id*(N.L) + ks*Is*(R.V)^16", C_FORM});
        lines.add(new Object[]{"2. FLAT / SMOOTH (F): ", C_HEAD, smooth ? "Smooth (per-vertex normal)" : "Flat (per-face normal)", C_ON});
        lines.add(new Object[]{smooth ? "   N_vertex = sum(N_face) / |sum(N_face)|"
                                      : "   N_face = (B-A)x(C-A) / |(B-A)x(C-A)|", C_FORM});
        lines.add(new Object[]{"3. HSR  Z-Buffer (Z): ", C_HEAD, depthOn ? "ON" : "OFF", depthOn ? C_ON : C_OFF,
                "   Culling (C): ", C_HEAD, cullOn ? "ON" : "OFF", cullOn ? C_ON : C_OFF});
        lines.add(new Object[]{"   z_new < ZBuf[x,y] -> update;  cull jika N.V >= 0", C_FORM});
        lines.add(new Object[]{"4. TEXTURE (T): ", C_HEAD, texOn ? "ON" : "OFF", texOn ? C_ON : C_OFF,
                "   Filter (N): ", C_HEAD, mipOn ? "Bilinear + Mipmap" : "Nearest", C_ON});
        lines.add(new Object[]{"   UV (u,v) dalam [0,1]  ->  warna = tex(u,v)", C_FORM});
        lines.add(new Object[]{"5. SHADOW (H): ", C_HEAD, shadowOn ? "ON" : "OFF", shadowOn ? C_ON : C_OFF,
                "   PCF (U): ", C_HEAD, pcfOn ? "ON" : "OFF", pcfOn ? C_ON : C_OFF,
                "   Bias (K): ", C_HEAD, BIAS_SHORT[biasMode], C_ON});
        lines.add(new Object[]{"   bayangan jika d_cur > d_shadow + bias (faktor 0.2)", C_FORM});
        String biasFormula = switch (biasMode) {
            case 0 -> "   bias = 0";
            case 1 -> "   bias = 0.005";
            case 3 -> "   bias = 0.05";
            default -> "   bias = max(0.05*(1 - N.L), 0.005)";
        };
        lines.add(new Object[]{biasFormula, C_FORM});

        // lebar panel = baris terpanjang
        float maxW = 0f;
        for (Object[] ln : lines) {
            float w = 0f;
            for (int i = 0; i < ln.length; i += 2) w += hud.width((String) ln[i]) * s;
            maxW = Math.max(maxW, w);
        }
        float panelW = maxW + pad * 2f;
        float panelH = lines.size() * lh + pad * 2f;
        float x0 = 12f * ui, y0 = 12f * ui;
        hud.drawRect(x0, y0, panelW, panelH, C_PANEL, fbW, fbH);

        float y = y0 + pad;
        for (Object[] ln : lines) {
            float x = x0 + pad;
            for (int i = 0; i < ln.length; i += 2) {
                String t = (String) ln[i];
                hud.drawText(t, x, y, s, (float[]) ln[i + 1], fbW, fbH);
                x += hud.width(t) * s;
            }
            y += lh;
        }

        // FPS (kanan atas)
        String fps = String.format(Locale.ROOT, "FPS %.0f", currentFps);
        float fs = 2.4f * ui;
        float fw = hud.width(fps) * fs;
        hud.drawRect(fbW - fw - 34f * ui, 12f * ui, fw + 22f * ui, 12f * 2.4f * ui + 14f * ui, C_PANEL, fbW, fbH);
        hud.drawText(fps, fbW - fw - 23f * ui, 19f * ui, fs, C_HEAD, fbW, fbH);

        // bantuan tombol (kiri bawah)
        float ks = 1.3f * ui;
        String k1 = "B Phong/Blinn  F Flat/Smooth  Z Z-Buffer  C Culling  T Tekstur  N Filter  H Shadow  K Bias  U PCF";
        String k2 = "V Wireframe  L Cahaya berputar  P Screenshot  G Benchmark  I HUD  |  drag mouse = putar, scroll = zoom";
        float kw = Math.max(hud.width(k1), hud.width(k2)) * ks + 20f * ui;
        float kh = 2f * 12f * ks + 16f * ui;
        hud.drawRect(12f * ui, fbH - kh - 12f * ui, kw, kh, C_PANEL, fbW, fbH);
        hud.drawText(k1, 22f * ui, fbH - kh - 12f * ui + 8f * ui, ks, C_TEXT, fbW, fbH);
        hud.drawText(k2, 22f * ui, fbH - kh - 12f * ui + 8f * ui + 12f * ks, ks, C_TEXT, fbW, fbH);

        glDisable(GL_BLEND);
        HSRManager.setupDepthTest(depthOn);
        HSRManager.setupBackFaceCulling(cullOn);
        glPolygonMode(GL_FRONT_AND_BACK, wire ? GL_LINE : GL_FILL);
    }

    // =====================================================================
    //  Screenshot (tombol P)
    // =====================================================================
    private void saveScreenshot() {
        new File("screenshots").mkdirs();
        ByteBuffer buf = BufferUtils.createByteBuffer(fbW * fbH * 3);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, fbW, fbH, GL_RGB, GL_UNSIGNED_BYTE, buf);
        String name = String.format(Locale.ROOT, "screenshots/shot_%02d_%s.png", ++shotCount, stateTag());
        stbi_flip_vertically_on_write(true);
        stbi_write_png(name, fbW, fbH, 3, buf, fbW * 3);
        System.out.println("Screenshot tersimpan: " + new File(name).getAbsolutePath());
    }

    private String stateTag() {
        return (blinn ? "Blinn" : "Phong") + "_" + (smooth ? "Smooth" : "Flat")
                + "_Z" + (depthOn ? 1 : 0) + "_C" + (cullOn ? 1 : 0)
                + "_Tex" + (texOn ? 1 : 0) + "_" + (mipOn ? "Mipmap" : "Nearest")
                + "_Shadow" + (shadowOn ? 1 : 0) + "_Bias" + biasMode + (wire ? "_Wire" : "");
    }

    // =====================================================================
    //  FPS di judul window
    // =====================================================================
    private void updateFpsAndTitle(double now) {
        fpsFrames++;
        if (now - fpsTimer >= 0.5) {
            currentFps = fpsFrames / (now - fpsTimer);
            fpsFrames = 0;
            fpsTimer = now;
            String title;
            if (benchActive) {
                title = String.format(Locale.ROOT, "BENCHMARK %d/%d: %s | jangan sentuh mouse/keyboard | FPS: %.0f",
                        benchIndex + 1, BENCH_NAMES.length, BENCH_NAMES[benchIndex], currentFps);
            } else {
                title = String.format(Locale.ROOT,
                        "FPS: %.0f | %s | %s | Z:%s C:%s | Tekstur:%s | Shadow:%s | Bias:%s%s",
                        currentFps, blinn ? "Blinn-Phong" : "Phong", smooth ? "Smooth" : "Flat",
                        depthOn ? "ON" : "OFF", cullOn ? "ON" : "OFF",
                        !texOn ? "OFF" : (mipOn ? "Mipmap" : "Nearest"),
                        shadowOn ? "ON" : "OFF", BIAS_NAMES[biasMode], wire ? " | Wireframe" : "");
            }
            glfwSetWindowTitle(window, title);
        }
    }

    // =====================================================================
    //  Benchmark FPS otomatis (tombol G): 7 konfigurasi x 10 detik
    // =====================================================================
    private void startBenchmark() {
        if (benchActive) return;
        benchActive = true;
        benchIndex = 0;
        applyBenchConfig(0);
        benchStart = glfwGetTime();
        benchFrames = 0;
        System.out.println("=== Benchmark dimulai (sekitar 70 detik). Jangan sentuh mouse/keyboard. ===");
    }

    private void applyBenchConfig(int i) {
        blinn = true; smooth = true; depthOn = true; cullOn = true;
        texOn = true; mipOn = true; shadowOn = true; pcfOn = true; wire = false; biasMode = 2; lightOrbit = false;
        switch (i) {
            case 1 -> blinn = false;
            case 2 -> smooth = false;
            case 3 -> cullOn = false;
            case 4 -> mipOn = false;
            case 5 -> shadowOn = false;
            case 6 -> depthOn = false;
            default -> { }
        }
        HSRManager.setupDepthTest(depthOn);
        HSRManager.setupBackFaceCulling(cullOn);
        applyFilterMode();
    }

    private void updateBenchmark() {
        if (!benchActive) return;
        benchFrames++;
        double elapsed = glfwGetTime() - benchStart;
        if (elapsed < BENCH_SECONDS) return;

        benchFps[benchIndex] = benchFrames / elapsed;
        System.out.printf(Locale.ROOT, "%d. %-32s %.1f FPS%n", benchIndex + 1, BENCH_NAMES[benchIndex], benchFps[benchIndex]);
        benchIndex++;
        if (benchIndex >= BENCH_NAMES.length) {
            finishBenchmark();
        } else {
            applyBenchConfig(benchIndex);
            benchStart = glfwGetTime();
            benchFrames = 0;
        }
    }

    private void finishBenchmark() {
        applyBenchConfig(0);   // kembali ke konfigurasi awal
        benchActive = false;
        try (PrintWriter out = new PrintWriter("benchmark_fps.txt")) {
            out.println("No | Konfigurasi | FPS rata-rata | Selisih dari baseline");
            for (int i = 0; i < BENCH_NAMES.length; i++) {
                double diff = (benchFps[i] - benchFps[0]) / benchFps[0] * 100.0;
                out.printf(Locale.ROOT, "%d | %s | %.1f | %+.1f%%%n", i + 1, BENCH_NAMES[i], benchFps[i], diff);
            }
            System.out.println("=== Benchmark selesai. Hasil: " + new File("benchmark_fps.txt").getAbsolutePath() + " ===");
        } catch (IOException e) {
            System.err.println("Gagal menulis benchmark_fps.txt: " + e.getMessage());
        }
    }
}
