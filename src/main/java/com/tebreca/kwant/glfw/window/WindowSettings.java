package com.tebreca.kwant.glfw.window;

import org.joml.Vector2i;

public record WindowSettings(long monitorId, Vector2i size, WindowType type, boolean resizable, String name) {

    public static enum WindowType{
        NORMAL, TRUE_FULLSCREEN, BORDERLESS_FULLSCREEN;
    }


    public static WindowSettings fullscreen(long monitorId, String name){
        return new WindowSettings(monitorId, new Vector2i(1,1),WindowType.TRUE_FULLSCREEN, false, name);
    }

    public static WindowSettings borderless(long monitorId, String name){
        return new WindowSettings(monitorId, new Vector2i(1,1), WindowType.BORDERLESS_FULLSCREEN, false, name);
    }

    public static WindowSettings normal(long monitorId, String name, Vector2i size, boolean resizable){
        return new WindowSettings(monitorId, size, WindowType.NORMAL, resizable, name);
    }
}
