package com.grafkom.viewing3d;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33.*;

/** Membungkus proses compile, link, dan pengaturan uniform GLSL. */
public class ShaderProgram {
    private final int programId;
    private final Map<String, Integer> locations = new HashMap<>();

    public ShaderProgram(String vertexPath, String fragmentPath) {
        int vs = compile(GL_VERTEX_SHADER, ResourceUtil.readText(vertexPath), vertexPath);
        int fs = compile(GL_FRAGMENT_SHADER, ResourceUtil.readText(fragmentPath), fragmentPath);
        programId = glCreateProgram();
        glAttachShader(programId, vs);
        glAttachShader(programId, fs);
        glLinkProgram(programId);
        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Link shader gagal: " + glGetProgramInfoLog(programId));
        }
        glDeleteShader(vs);
        glDeleteShader(fs);
    }

    private static int compile(int type, String source, String name) {
        int id = glCreateShader(type);
        glShaderSource(id, source);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Compile shader gagal (" + name + "): " + glGetShaderInfoLog(id));
        }
        return id;
    }

    public void use() {
        glUseProgram(programId);
    }

    private int loc(String name) {
        return locations.computeIfAbsent(name, n -> glGetUniformLocation(programId, n));
    }

    public void setInt(String name, int value) {
        glUniform1i(loc(name), value);
    }

    public void setBool(String name, boolean value) {
        glUniform1i(loc(name), value ? 1 : 0);
    }

    public void setFloat(String name, float value) {
        glUniform1f(loc(name), value);
    }

    public void setVec2(String name, float x, float y) {
        glUniform2f(loc(name), x, y);
    }

    public void setVec4(String name, float x, float y, float z, float w) {
        glUniform4f(loc(name), x, y, z, w);
    }

    public void setVec3(String name, float x, float y, float z) {
        glUniform3f(loc(name), x, y, z);
    }

    public void setVec3(String name, Vector3f v) {
        glUniform3f(loc(name), v.x, v.y, v.z);
    }

    public void setMat4(String name, Matrix4f m) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            m.get(fb);
            glUniformMatrix4fv(loc(name), false, fb);
        }
    }
}
