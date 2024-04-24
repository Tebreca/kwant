package com.tebreca.kwant;

import com.tebreca.kwant.general.GameInfo;
import com.tebreca.kwant.glfw.WindowManager;
import com.tebreca.kwant.vk.VulkanManager;
import com.tebreca.kwant.window.WindowSettings;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import static org.lwjgl.glfw.GLFW.*;
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
@SuppressWarnings("unused")
public class Engine {

    //Constants
    private static final int VERSION = 1;
    private static final String NAME = "Kwant Engine";

    // Validation layers
    private final List<String> requestedValidationLayers = new ArrayList<>();
    private boolean enableValidation = false;

    //Extensions
    private final List<String> requiredExtensions = new ArrayList<>();

    // Instance
    private static final Sinks.One<Engine> instanceSink = Sinks.one();
    public static final Mono<Engine> instance = instanceSink.asMono();

    // Continuous construction helpers
    private final Map<Class<?>, Supplier<?>> suppliers = new HashMap<>();


    // Managers
    private final WindowManager windowManager = new WindowManager();
    private VulkanManager vulkanManager;

    public Engine enableValidationLayers() {
        this.enableValidation = true;
        return this;
    }

    public Engine() {
        instanceSink.tryEmitValue(this).orThrow();
    }

    private final Sinks.One<VulkanManager> vulkanSink = Sinks.one();

    public Engine withWindowSettings(LongFunction<WindowSettings> windowSettings) {
        suppliers.put(WindowSettings.class, () -> windowSettings.apply(glfwGetPrimaryMonitor()));
        return this;
    }

    public Engine withValidationLayers(String... layers) {
        requestedValidationLayers.addAll(Arrays.stream(layers).toList());
        return this;
    }

    public Engine withValidationLayer(String layer) {
        requestedValidationLayers.add(layer);
        return this;
    }

    public Engine withExtensions(String... extensions){
        requiredExtensions.addAll(Arrays.stream(extensions).toList());
        return this;
    }

    public Engine withExtension(String extension){
        requiredExtensions.add(extension);
        return this;
    }

    public Engine withGameInfo(Supplier<GameInfo> gameInfo) {
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
        try (var memoryStack = stackPush()) {
            var applicationInfo = VkApplicationInfo.calloc(memoryStack);
            applicationInfo.apiVersion(VK_API_VERSION_1_3);
            applicationInfo.engineVersion(Engine.VERSION);
            ByteBuffer engine_name = memoryStack.ASCII(Engine.NAME);
            applicationInfo.pEngineName(engine_name);

            if (suppliers.containsKey(GameInfo.class)) {
                GameInfo gameInfo = (GameInfo) suppliers.get(GameInfo.class).get();
                ByteBuffer game_name = memoryStack.ASCII(gameInfo.getName());
                applicationInfo.pApplicationName(game_name);
                applicationInfo.applicationVersion(gameInfo.getVersion());
            } else {
                applicationInfo.pApplicationName(memoryStack.ASCII("Kwanta game"));
                applicationInfo.applicationVersion(1);
            }

            VkInstanceCreateInfo instanceCreateInfo = VkInstanceCreateInfo.calloc(memoryStack);
            PointerBuffer glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
            assert glfwExtensions != null;

            if (enableValidation || !requestedValidationLayers.isEmpty()) {
                List<VkLayerProperties> layers = findLayers(memoryStack);
                var names = layers.stream().map(VkLayerProperties::layerNameString).toList();
                PointerBuffer pointers = memoryStack.mallocPointer(names.size());
                names.stream().map(memoryStack::UTF8).forEach(pointers::put);
                instanceCreateInfo.ppEnabledLayerNames(pointers);
            }

            var amount = memoryStack.callocInt(1);
            vkEnumerateInstanceExtensionProperties((CharSequence) null, amount, null);
            VkExtensionProperties.Buffer extensionProperties = VkExtensionProperties.calloc(amount.get(), memoryStack);

            HashSet<String> extensionNames = new HashSet<>(extensionProperties.stream().map(VkExtensionProperties::extensionNameString).filter(requiredExtensions::contains).toList());

            if (!extensionNames.containsAll(requiredExtensions)){
                throw new RuntimeException("Not all required extensions were found!");
            }

            PointerBuffer extensions = memoryStack.callocPointer(glfwExtensions.remaining() + amount.get(0));
            extensions.put(glfwExtensions);
            extensionNames.stream().map(memoryStack::UTF8).forEach(extensions::put);
            extensions.flip();

            instanceCreateInfo.pApplicationInfo(applicationInfo);
            instanceCreateInfo.ppEnabledExtensionNames(extensions);
            var vulkan = memoryStack.callocPointer(VkInstance.POINTER_SIZE);

            if (vkCreateInstance(instanceCreateInfo, null, vulkan) != VK_SUCCESS) {
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


    private List<VkLayerProperties> findLayers(MemoryStack stack) {
        var size = stack.callocInt(1);
        vkEnumerateInstanceLayerProperties(size, null);
        var layers = size.get(0);
        VkLayerProperties.Buffer properties = VkLayerProperties.calloc(layers);
        vkEnumerateInstanceLayerProperties(size, properties);
        return (requestedValidationLayers.isEmpty() ? properties.stream() : properties.stream().filter(l -> requestedValidationLayers.contains(l.layerNameString()))).toList();
    }

    public Mono<VulkanManager> vulkan() {
        return vulkanSink.asMono();
    }


}
