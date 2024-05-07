package com.tebreca.kwant.vk.swapchain;

import org.joml.Vector2i;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;

public record GraphicsSettings(int buffering, Vector2i resolution, int presentmode) {

    public static GraphicsSettings tripleBuffering(long window) {
        try (var stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.callocInt(1);
            IntBuffer height = stack.callocInt(1);
            glfwGetFramebufferSize(window, width, height);
            return new GraphicsSettings(3, new Vector2i(width.get(), height.get()), VK_PRESENT_MODE_MAILBOX_KHR);
        }
    }

    public static GraphicsSettings simple(long window) {
        try (var stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.callocInt(1);
            IntBuffer height = stack.callocInt(1);
            glfwGetFramebufferSize(window, width, height);
            return new GraphicsSettings(1, new Vector2i(width.get(), height.get()), VK_PRESENT_MODE_IMMEDIATE_KHR);
        }
    }
}
