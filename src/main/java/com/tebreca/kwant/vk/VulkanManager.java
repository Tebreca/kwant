package com.tebreca.kwant.vk;

import com.tebreca.kwant.vk.device.DeviceScorer;
import com.tebreca.kwant.vk.device.DeviceSettings;
import com.tebreca.kwant.vk.queue.QueueBuilder;
import com.tebreca.kwant.vk.queue.QueueFamilyFinder;
import com.tebreca.kwant.vk.queue.QueueType;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.lwjgl.vulkan.VK13.*;

@SuppressWarnings("unused")
public final class VulkanManager {

    private final VkInstance instance;
    private final DeviceScorer deviceScorer;
    private final VkPhysicalDevice physicalDevice;

    private final Map<QueueType, Integer> idealQueueFamilies;

    private final int transferFamily;

    private final List<QueueBuilder.QueueInfo> submittedQueueInfos = new ArrayList<>();
    private final Sinks.One<VkDevice> virtualDeviceSink = Sinks.one();
    private VkDevice device;

    public VulkanManager(VkInstance vulkan, DeviceScorer scorer, QueueFamilyFinder queueFamilyFinder) {
        instance = vulkan;
        deviceScorer = scorer;
        physicalDevice = pickPhysicalDevice();
        idealQueueFamilies = queueFamilyFinder.getFamilies(physicalDevice);
        transferFamily = findTransferFamily();
    }

    private int findTransferFamily() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer size = stack.callocInt(1);
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, size, null);
            var families = VkQueueFamilyProperties.calloc(size.get(), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, size, families);
            while (families.hasRemaining()){
                VkQueueFamilyProperties familyProperties = families.get();

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

    public VkInstance raw(VkInstance instance) {
        return instance;
    }

    public VkPhysicalDevice physicalDevice() {
        return physicalDevice;
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
                for (String name : extensions) {
                    System.out.printf("Enabling extension %s %n", name);
                    names.put(i++, stack.ASCII(name.stripIndent()));
                }
                names.flip();
                deviceCreateInfo.ppEnabledExtensionNames(names);
            }

            VulkanUtils.assertResult(vkCreateDevice(physicalDevice, deviceCreateInfo, null, device), "Failed to create Logical Device!");
            this.device = new VkDevice(device.get(), physicalDevice, deviceCreateInfo);


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
            var instance = VkDeviceQueueCreateInfo.calloc(stack);
            instance.sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
            priorities.flip();
            instance.pQueuePriorities(priorities);
            instance.queueFamilyIndex(family);
            buffer.put(instance);
        }

        return buffer;
    }

    public Mono<VkDevice> virtualDevice() {
        return virtualDeviceSink.asMono();
    }

    public QueueBuilder queue(QueueType type) {
        return new QueueBuilder(type, this);
    }

    public void cleanup() {
        // It may be better not to throw here and just continue. TODO: logger.error to inform user about this problem
        onCleanup.tryEmitValue(instance);
        vkDestroyDevice(device, null);
        vkDestroyInstance(instance, null);
    }

    public void submitQueue(QueueBuilder.QueueInfo queueInfo) {
        submittedQueueInfos.add(queueInfo);
    }
}
