package com.tebreca.kwant;

import com.tebreca.kwant.general.GameInfo;
import com.tebreca.kwant.glfw.WindowManager;
import com.tebreca.kwant.vk.VulkanManager;
import com.tebreca.kwant.vk.VulkanUtils;
import com.tebreca.kwant.vk.device.DeviceScorer;
import com.tebreca.kwant.vk.device.DeviceSettings;
import com.tebreca.kwant.vk.queue.QueueFamilyFinder;
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
    private final List<String> deviceExtensions = new ArrayList<>();

    // Instance
    private static final Sinks.One<Engine> instanceSink = Sinks.one();
    public static final Mono<Engine> instance = instanceSink.asMono();

    // Continuous construction helpers
    private final Map<Class<?>, Supplier<?>> suppliers = new HashMap<>();

    private final Map<Class<?>, Object> implementations = new HashMap<>();


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
        return this.with(WindowSettings.class, (Supplier<WindowSettings>) () -> windowSettings.apply(glfwGetPrimaryMonitor()));
    }

    public Engine withValidationLayers(String... layers) {
        requestedValidationLayers.addAll(Arrays.stream(layers).toList());
        return this;
    }

    public Engine withValidationLayer(String layer) {
        requestedValidationLayers.add(layer);
        return this;
    }

    public Engine withExtensions(String... extensions) {
        requiredExtensions.addAll(List.of(extensions));
        return this;
    }

    public Engine withExtension(String extension) {
        requiredExtensions.add(extension);
        return this;
    }

    public Engine withDeviceExtension(String extension) {
        deviceExtensions.add(extension);
        return this;
    }

    public Engine withDeviceExtensions(String... extensions) {
        deviceExtensions.addAll(List.of(extensions));
        return this;
    }

    public Engine withScorer(DeviceScorer scorer) {
        return this.with(DeviceScorer.class, scorer);
    }

    public Engine withGameInfo(GameInfo gameInfo) {
        return this.with(GameInfo.class, gameInfo);
    }

    public Engine withDeviceSettings(DeviceSettings settings) {
        return this.with(DeviceSettings.class, settings);
    }

    /**
     * Less controlled but 'cleaner' method, use only if you know what you're doing
     *
     * @param type Type T which is produced by the supplier attached
     * @param t    supplier of type t
     * @param <T>  General Type
     * @return this
     */
    public <T> Engine with(Class<T> type, Supplier<T> t) {
        suppliers.put(type, t);
        return this;
    }

    /**
     * Less controlled but 'cleaner' method, use only if you know what you're doing
     *
     * @param type     Type of implementation T
     * @param instance implementation of t
     * @param <T>      An interface class that is implemented to be used with the engine
     * @return this
     */
    public <T> Engine with(Class<T> type, T instance) {
        implementations.put(type, instance);
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
            instanceCreateInfo.sType(VK13.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
            PointerBuffer glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
            assert glfwExtensions != null;

            if (enableValidation || !requestedValidationLayers.isEmpty()) {
                List<VkLayerProperties> layers = findLayers(memoryStack);
                var names = layers.stream().map(VkLayerProperties::layerNameString).toList();
                PointerBuffer pointers = memoryStack.callocPointer(names.size());
                for (String name : names) {
                    System.out.printf("Using validation layer %s %n", name);
                    pointers.put(memoryStack.ASCII(name));
                }
                pointers.flip();
                instanceCreateInfo.ppEnabledLayerNames(pointers);
                System.out.printf("Total enabled layers; %d%n", instanceCreateInfo.enabledLayerCount());
            }

            var amount = memoryStack.callocInt(1);
            vkEnumerateInstanceExtensionProperties((CharSequence) null, amount, null);
            VkExtensionProperties.Buffer extensionProperties = VkExtensionProperties.calloc(amount.get(), memoryStack);

            HashSet<String> extensionNames = new HashSet<>(extensionProperties.stream().map(VkExtensionProperties::extensionNameString).filter(requiredExtensions::contains).toList());

            if (!extensionNames.containsAll(requiredExtensions)) {
                throw new RuntimeException("Not all required extensions were found!");
            }

            PointerBuffer extensions = memoryStack.callocPointer(glfwExtensions.remaining() + amount.get(0));
            extensions.put(glfwExtensions);
            extensionNames.stream().map(memoryStack::UTF8).forEach(extensions::put);
            extensions.flip();

            instanceCreateInfo.pApplicationInfo(applicationInfo);
            instanceCreateInfo.ppEnabledExtensionNames(extensions);
            var vulkan = memoryStack.callocPointer(VkInstance.POINTER_SIZE);

            VulkanUtils.assertResult(vkCreateInstance(instanceCreateInfo, null, vulkan), "Failed to create Vulkan instance!");

            DeviceScorer scorer = getDirty(DeviceScorer.class, DeviceScorer::simple);
            QueueFamilyFinder familyFinder = getDirty(QueueFamilyFinder.class, QueueFamilyFinder::simple);

            vulkanManager = new VulkanManager(new VkInstance(vulkan.get(), instanceCreateInfo), scorer, familyFinder);
            vulkanSink.tryEmitValue(vulkanManager).orThrow();
        }

        DeviceSettings deviceSettings = getDirty(DeviceSettings.class, new DeviceSettings(VkPhysicalDeviceFeatures.calloc(), 0));

        vulkanManager.createDevice(deviceSettings, deviceExtensions);

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

    @SuppressWarnings("unchecked")
    // We aren't unchecked since the only way to get any value into the map is via Engine.with
    private <T> T getDirty(Class<T> key, T other) {
        return implementations.containsKey(key) ? (T) implementations.get(key) : other;
    }

    @SuppressWarnings("unchecked")
    // We aren't unchecked since the only way to get any value into the map is via Engine.with
    private <T> Supplier<T> getDirty(Class<T> key, Supplier<T> other) {
        return suppliers.containsKey(key) ? (Supplier<T>) suppliers.get(key) : other;
    }

}
