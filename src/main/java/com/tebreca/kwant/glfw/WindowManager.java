package com.tebreca.kwant.glfw;

import com.tebreca.kwant.window.WindowSettings;
import org.joml.Vector2i;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class WindowManager {

    private long windowId;

    public void start(WindowSettings windowSettings) {
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        var videomode = glfwGetVideoMode(windowSettings.monitorId());
        if (videomode == null)
            throw new IllegalStateException("videomode couldn't be found");

        switch (windowSettings.type()) {
            case NORMAL -> {
                Vector2i size = windowSettings.size();
                glfwWindowHint(GLFW_RESIZABLE, windowSettings.resizable() ? GLFW_TRUE : GLFW_FALSE);
                windowId = glfwCreateWindow(size.x, size.y, windowSettings.name(), NULL, NULL);
            }
            case TRUE_FULLSCREEN -> {
                glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
                windowId = glfwCreateWindow(videomode.width(), videomode.height(), windowSettings.name(), windowSettings.monitorId(), NULL);
            }
            case BORDERLESS_FULLSCREEN -> {
                glfwWindowHint(GLFW_RED_BITS, videomode.redBits());
                glfwWindowHint(GLFW_BLUE_BITS, videomode.blueBits());
                glfwWindowHint(GLFW_GREEN_BITS, videomode.greenBits());
                glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
                windowId = glfwCreateWindow(videomode.width(), videomode.height(), windowSettings.name(), windowSettings.monitorId(), NULL);
            }
        }
    }

    public void subscribe() {
        while (!glfwWindowShouldClose(windowId)) {
            glfwPollEvents();
        }
    }

    public void cleanup() {
        glfwDestroyWindow(windowId);
    }
}
