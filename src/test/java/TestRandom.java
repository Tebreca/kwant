import org.lwjgl.glfw.GLFW;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;


public class TestRandom {
    public static void main(String[] args) {
        glfwInit();

        glfwDefaultWindowHints();


        long window = glfwCreateWindow(400, 400, "BALLS", NULL, NULL);

        while (!glfwWindowShouldClose(window)){
            glfwPollEvents();
        }

        glfwDestroyWindow(window);
    }
}
