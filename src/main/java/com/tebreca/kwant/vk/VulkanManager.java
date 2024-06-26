package com.tebreca.kwant.vk;

import com.tebreca.kwant.vk.device.DeviceScorer;
import com.tebreca.kwant.vk.device.DeviceSettings;
import com.tebreca.kwant.vk.pipeline.PipelineBuilder;
import com.tebreca.kwant.vk.queue.QueueBuilder;
import com.tebreca.kwant.vk.queue.QueueFamilyFinder;
import com.tebreca.kwant.vk.queue.QueueType;
import com.tebreca.kwant.vk.shader.Shader;
import com.tebreca.kwant.vk.shader.ShaderBuilder;
import com.tebreca.kwant.vk.swapchain.SwapChainManager;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.VK13.*;

@SuppressWarnings("unused")
public final class VulkanManager {

    private final VkInstance instance;
    private final DeviceScorer deviceScorer;
    private final VkPhysicalDevice physicalDevice;

    private final Map<QueueType, Integer> idealQueueFamilies;

    private final int presentFamily;

    private final List<QueueBuilder.QueueInfo> submittedQueueInfos = new ArrayList<>();
    private final Sinks.One<VkDevice> virtualDeviceSink = Sinks.one();

    private VkDevice device;
    private SwapChainManager swapChainManager;

    private final Sinks.One<SwapChainManager> chainManagerSink = Sinks.one();

    private final Sinks.One<MemoryStack> onInitSink = Sinks.one();
    private List<Shader> shaders = new ArrayList<>();

    public VulkanManager(VkInstance vulkan, DeviceScorer scorer, QueueFamilyFinder queueFamilyFinder, long window) {
        instance = vulkan;
        deviceScorer = scorer;
        physicalDevice = pickPhysicalDevice();
        long surface = createSurface(window);
        idealQueueFamilies = queueFamilyFinder.getFamilies(physicalDevice);
        presentFamily = findPresentFamily(surface);
        queue(QueueType.GRAPHICS).family(presentFamily).submit().subscribe(vkQueue -> chainManagerSink.tryEmitValue(new SwapChainManager(this, surface, vkQueue)).orThrow());
        chainManagerSink.asMono().subscribe(s -> swapChainManager = s);

    }

    private long createSurface(long window) {
        try (var stack = MemoryStack.stackPush()) {
            LongBuffer longBuffer = stack.callocLong(1);
            VulkanUtils.assertResult(glfwCreateWindowSurface(instance, window, null, longBuffer), "Failed to create window surface!");
            return longBuffer.get();
        }
    }

    private int findPresentFamily(long surface) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer size = stack.callocInt(1);
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, size, null);
            var families = VkQueueFamilyProperties.calloc(size.get(), stack);
            size.clear();
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, size, families);
            families.flip();
            int i = 0;
            while (families.hasRemaining()) {
                VkQueueFamilyProperties familyProperties = families.get();
                var flag = stack.callocInt(1);
                vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, i++, surface, flag);
                if (flag.get() == VK_TRUE) {
                    return i;
                }
            }
        }
        return 0;
    }

    public int getIdealFamilyIndex(QueueType type) {
        return idealQueueFamilies.get(type);
    }

    private VkPhysicalDevice pickPhysicalDevice() {
        int scoreBest = -1;
        VkPhysicalDevice device = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var count = stack.callocInt(1);
            vkEnumeratePhysicalDevices(instance, count, null);
            var devices = stack.callocPointer(count.get());
            vkEnumeratePhysicalDevices(instance, count.clear(), devices);

            while (devices.hasRemaining()) {
                VkPhysicalDevice physicalDevice = new VkPhysicalDevice(devices.get(), instance);
                int score = deviceScorer.getScore(physicalDevice);
                if (score > scoreBest) {
                    device = physicalDevice;
                    scoreBest = score;
                }
            }
        }
        assert device != null;
        return device;
    }

    private final Sinks.One<VkInstance> onCleanup = Sinks.one();

    public Mono<VkInstance> onCleanup() {
        return onCleanup.asMono();
    }

    public VkInstance raw() {
        return instance;
    }


    public QueueBuilder queue(QueueType type) {
        return new QueueBuilder(type, this);
    }

    private VkDeviceQueueCreateInfo.Buffer buildQueueCreateInfoBuffer(MemoryStack stack) {
        HashMap<Integer, Integer> indexAmounts = new HashMap<>();
        for (QueueBuilder.QueueInfo info : submittedQueueInfos) {
            indexAmounts.put(info.familyIndex(), indexAmounts.getOrDefault(info.familyIndex(), 0) + 1);
        }

        VkDeviceQueueCreateInfo.Buffer buffer = VkDeviceQueueCreateInfo.calloc(indexAmounts.size(), stack);
        for (Map.Entry<Integer, Integer> entry : indexAmounts.entrySet()) {
            Integer family = entry.getKey();
            Integer amount = entry.getValue();

            var priorities = stack.callocFloat(amount);
            for (float f : submittedQueueInfos.stream().filter(queueInfo -> queueInfo.familyIndex() == family).map(QueueBuilder.QueueInfo::priority).toList()) {
                priorities.put(f);
            }
            var deviceQueueCreateInfo = VkDeviceQueueCreateInfo.calloc(stack);
            deviceQueueCreateInfo.sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
            priorities.flip();
            deviceQueueCreateInfo.pQueuePriorities(priorities);
            deviceQueueCreateInfo.queueFamilyIndex(family);
            buffer.put(deviceQueueCreateInfo);
        }

        return buffer;
    }

    public void createDevice(DeviceSettings deviceSettings, List<String> extensions) {
        try (var stack = MemoryStack.stackPush()) {
            VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.calloc(stack);
            deviceCreateInfo.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
            PointerBuffer device = stack.mallocPointer(1);
            VkDeviceQueueCreateInfo.Buffer queueCreateInfos = buildQueueCreateInfoBuffer(stack);
            queueCreateInfos.flip();
            deviceCreateInfo.pQueueCreateInfos(queueCreateInfos);
            deviceCreateInfo.flags(deviceSettings.flags());
            deviceCreateInfo.pEnabledFeatures(deviceSettings.deviceFeatures());
            int i = 0;
            if (!extensions.isEmpty()) {
                var names = stack.mallocPointer(extensions.size());
                extensions.stream().map(stack::ASCII).forEach(names::put);
                deviceCreateInfo.ppEnabledExtensionNames(names.flip());
            }

            VulkanUtils.assertResult(vkCreateDevice(physicalDevice, deviceCreateInfo, null, device), "Failed to create Logical Device!");
            this.device = new VkDevice(device.get(), physicalDevice, deviceCreateInfo);

            virtualDeviceSink.tryEmitValue(this.device).orThrow();

            HashMap<Integer, Integer> indexAmounts = new HashMap<>();
            for (QueueBuilder.QueueInfo info : submittedQueueInfos) {
                indexAmounts.put(info.familyIndex(), indexAmounts.getOrDefault(info.familyIndex(), 0) + 1);
            }

            for (Map.Entry<Integer, Integer> entry : indexAmounts.entrySet()) {
                Integer family = entry.getKey();
                i = 0;
                Stream<QueueBuilder.QueueInfo> queueInfoStream = submittedQueueInfos.stream().filter(queueInfo -> queueInfo.familyIndex() == family);
                for (QueueBuilder.QueueInfo queueInfo : queueInfoStream.toList()) {
                    PointerBuffer queue = stack.callocPointer(1);
                    vkGetDeviceQueue(this.device, family, i++, queue);
                    long handle = queue.get(0);
                    queueInfo.sink().tryEmitValue(new VkQueue(handle, this.device)).orThrow();
                }
            }

            deviceSettings.deviceFeatures().close();
        }
    }

    public void cleanup() {
        // It may be better not to throw here and just continue. TODO: logger.error to inform user about this problem
        onCleanup.tryEmitValue(instance);
        swapChainManager.destroyChain();
        vkDestroyDevice(device, null);
        vkDestroyInstance(instance, null);
    }

    public Mono<VkDevice> virtualDevice() {
        return virtualDeviceSink.asMono();
    }

    public VkPhysicalDevice physicalDevice() {
        return physicalDevice;
    }

    public void submitQueue(QueueBuilder.QueueInfo queueInfo) {
        submittedQueueInfos.add(queueInfo);
    }

    public Mono<SwapChainManager> swapChainManager() {
        return chainManagerSink.asMono();
    }

    /**
     * @param location location of the .spv file
     * @return a ShaderBuilder instance for creating the shader
     */
    public ShaderBuilder shader(@Nonnull String location) {
        try {
            File file = new File(location);
            if (!file.exists()) throw new FileNotFoundException("Shader file doesn't exist!");
            return new ShaderBuilder(file, this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to open shader file %s !".formatted(location), e);
        }
    }


    /**
     * This method is for internal use. Do not use unless you know what you're doing!
     * Use VulkanManager::shader to import shader files
     *
     * @param shader
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    public void withShader(Shader shader) {
        this.shaders.add(shader);
    }

    public PipelineBuilder pipeline() {
        return new PipelineBuilder(this);
    }

    public Mono<MemoryStack> onInit() {
        return onInitSink.asMono();
    }
}
