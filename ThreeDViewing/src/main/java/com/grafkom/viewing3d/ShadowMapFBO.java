package com.grafkom.viewing3d;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33.*;

/** Framebuffer Object berisi depth texture (shadow map) 2048 x 2048. */
public class ShadowMapFBO {
    private final int fboID;
    private final int depthTextureID;
    private static final int SHADOW_WIDTH = 2048;
    private static final int SHADOW_HEIGHT = 2048;

    public ShadowMapFBO() {
        fboID = glGenFramebuffers();
        depthTextureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, depthTextureID);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, SHADOW_WIDTH, SHADOW_HEIGHT, 0,
                GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        // area di luar shadow map dianggap terang (kedalaman 1.0 = terjauh)
        float[] borderColor = {1.0f, 1.0f, 1.0f, 1.0f};
        glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, borderColor);

        glBindFramebuffer(GL_FRAMEBUFFER, fboID);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTextureID, 0);
        glDrawBuffer(GL_NONE);   // tanpa color buffer, hanya depth
        glReadBuffer(GL_NONE);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Shadow map FBO tidak lengkap!");
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void bindForWriting() {
        glViewport(0, 0, SHADOW_WIDTH, SHADOW_HEIGHT);
        glBindFramebuffer(GL_FRAMEBUFFER, fboID);
        glClear(GL_DEPTH_BUFFER_BIT);
    }

    public void unbind(int screenWidth, int screenHeight) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, screenWidth, screenHeight);
    }

    public int getDepthTextureID() {
        return depthTextureID;
    }
}
