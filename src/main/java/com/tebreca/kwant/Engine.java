package com.tebreca.kwant;

import com.tebreca.kwant.general.GameInfo;
import com.tebreca.kwant.glfw.WindowManager;
import com.tebreca.kwant.vk.VulkanManager;
import com.tebreca.kwant.window.WindowSettings;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import static org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Kwant simple Vulkan engine in Java by Ewan Arends (tebreca)
 * <p>
 * Subfolders;
 * <p>
 * - vk: all the vulkan POJOs for easy interaction
 * - util: all the helper classes to ensure as much DRY as possible
 * - window: all the glfw window abstractions
 * - data: simple data POJOs for the engine
 */
public class Engine {

    private static final int VERSION = 1;
    private static final String NAME = "Kwant Engine";

    private final Map<Class<?>, Supplier<?>> suppliers = new HashMap<>();
    private final WindowManager windowManager = new WindowManager();
    private VulkanManager vulkanManager;

    private final Sinks.One<VulkanManager> vulkanSink = Sinks.one();

    public Engine withWindowSettings(LongFunction<WindowSettings> windowSettings) {
        suppliers.put(WindowSettings.class, () -> windowSettings.apply(glfwGetPrimaryMonitor()));
        return this;
    }

    public Engine withGameInfo(Supplier<GameInfo> gameInfo){
        return this.with(GameInfo.class, gameInfo);
    }

    /**
     * Less controlled but 'cleaner' method, use only if you know what you're doing
     *
     * @param type Type T which is produced by the supplier attached
     * @param t    supplier of type t
     * @param <T>  General Type
     * @return this entity
     */
    public <T> Engine with(Class<T> type, Supplier<T> t) {
        suppliers.put(type, t);
        return this;
    }

    public void start() {
        glfwInit();

        if (suppliers.containsKey(WindowSettings.class)) {
            windowManager.start((WindowSettings) suppliers.get(WindowSettings.class).get());
        }
        try ( var memoryStack = stackPush()){
            var applicationInfo = VkApplicationInfo.calloc(memoryStack);
            applicationInfo.apiVersion(VK_API_VERSION_1_3);
            applicationInfo.engineVersion(Engine.VERSION);
            ByteBuffer engine_name = memoryStack.ASCII(Engine.NAME);
            applicationInfo.pEngineName(engine_name);

            if (suppliers.containsKey(GameInfo.class)){
                GameInfo gameInfo = (GameInfo) suppliers.get(GameInfo.class).get();
                ByteBuffer game_name = memoryStack.ASCII(gameInfo.getName());
                applicationInfo.pApplicationName(game_name);
                applicationInfo.applicationVersion(gameInfo.getVersion());
            } else {
                applicationInfo.pApplicationName(memoryStack.ASCII("Kwanta game"));
                applicationInfo.applicationVersion(1);
            }

            VkInstanceCreateInfo instanceCreateInfo = VkInstanceCreateInfo.calloc(memoryStack);
            instanceCreateInfo.pApplicationInfo(applicationInfo);

            var vulkan = memoryStack.callocPointer(VkInstance.POINTER_SIZE);

            if(vkCreateInstance(instanceCreateInfo, null, vulkan) != VK_SUCCESS){
                throw new RuntimeException("Failed to create Vulkan instance!");
            }
            vulkanManager = new VulkanManager(new VkInstance(vulkan.get(), instanceCreateInfo));
            vulkanSink.tryEmitValue(vulkanManager).orThrow();
        }

        // RUN PHASE
        windowManager.subscribe();

        // CLEANUP PHASE
        vulkanManager.cleanup();
        windowManager.cleanup();
    }

    public Mono<VulkanManager> vulkan(){
        return vulkanSink.asMono();
    }


}
