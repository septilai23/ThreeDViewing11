package com.grafkom.viewing3d;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Kamera orbit: berputar mengelilingi titik target. */
public class Camera {
    private float yaw = 0.55f;       // rotasi horizontal (radian)
    private float pitch = 0.42f;     // rotasi vertikal (radian)
    private float distance = 19f;   // jarak ke target
    private final Vector3f target = new Vector3f(0f, 1.2f, 0f);

    public void rotate(float dx, float dy) {
        yaw -= dx * 0.005f;
        pitch += dy * 0.005f;
        pitch = Math.max(-1.4f, Math.min(1.4f, pitch));
    }

    public void zoom(float amount) {
        distance -= amount * 0.8f;
        distance = Math.max(3f, Math.min(45f, distance));
    }

    public Vector3f getPosition() {
        float cp = (float) Math.cos(pitch);
        return new Vector3f(
                target.x + distance * cp * (float) Math.sin(yaw),
                target.y + distance * (float) Math.sin(pitch),
                target.z + distance * cp * (float) Math.cos(yaw));
    }

    public Matrix4f getViewMatrix() {
        return new Matrix4f().lookAt(getPosition(), target, new Vector3f(0f, 1f, 0f));
    }

    public Matrix4f getProjectionMatrix(float aspect) {
        // zNear tidak terlalu dekat ke 0 agar presisi Z-Buffer terjaga (hindari Z-fighting)
        return new Matrix4f().perspective((float) Math.toRadians(45.0), aspect, 0.1f, 100f);
    }
}
