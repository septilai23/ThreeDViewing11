3D VISUAL REALISM SUITE - Grafika Komputer Minggu 11
=====================================================
Java 17+, LWJGL 3.3.4, JOML 1.10.5, GLSL 330 core (Maven project)

ISI SCENE
- Teko 3D (Teapot) prosedural: badan, tutup, cerat, gagang (tekstur porselen biru)
- Bola bumi (16 x 24, tekstur peta bumi)
- Rumah 3D: dinding bata, atap, cerobong, pintu kayu, jendela kaca
- Lantai marmer (ground plane) tempat bayangan jatuh
- Langit gradasi, anti-aliasing MSAA 4x, HUD berisi status fitur dan rumus yang sedang aktif

CARA MENJALANKAN (NetBeans)
1. File > Open Project > pilih folder ThreeDViewing > Open.
2. Tunggu Maven mengunduh library (butuh internet saat pertama kali).
3. Klik kanan project > Run (atau tombol hijau Run Project).
   * Pengguna macOS: klik kanan project > Properties > Run > VM Options, isi:  -XstartOnFirstThread

KONTROL
B = Phong / Blinn-Phong        F = Flat / Smooth shading
Z = Z-Buffer ON/OFF            C = Back-Face Culling ON/OFF
T = Tekstur ON/OFF             N = Nearest / Bilinear+Mipmap
H = Shadow Mapping ON/OFF      K = ganti nilai Shadow Bias (0 / 0.005 / dinamis / 0.05)
U = PCF (bayangan halus) ON/OFF
V = Wireframe                  L = cahaya berputar        I = HUD ON/OFF
P = simpan screenshot ke folder screenshots/
G = benchmark FPS otomatis (hasil di benchmark_fps.txt, sekitar 70 detik)
Mouse drag kiri / tombol panah = putar kamera, scroll / W,S = zoom, ESC = keluar

Screenshot dan benchmark_fps.txt tersimpan di folder kerja project (folder ThreeDViewing).
