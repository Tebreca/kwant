package com.tebreca.kwant.vk.queue;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceQueueFamilyProperties;

public interface QueueFamilyFinder {

    HashMap<QueueType, Integer> getFamilies(VkPhysicalDevice device);

     static HashMap<QueueType, Integer> simple(VkPhysicalDevice physicalDevice) {
        HashMap<QueueType, Integer> idealFamilies = new HashMap<>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (QueueType queueType : QueueType.values()) {
                IntBuffer count = stack.callocInt(1);
                vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, count, null);
                var families = VkQueueFamilyProperties.calloc(count.get(), stack);
                vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, count.clear(), families);
                Map<Integer, Integer> possibleFamilies = new HashMap<>();
                int i = -1;
                while (families.hasRemaining()) {
                    i++;
                    VkQueueFamilyProperties familyProperties = families.get();
                    if ((familyProperties.queueFlags() & queueType.getBit()) == 0) {
                        continue;
                    }
                    var supportedCount = Arrays.stream(QueueType.values()).map(type -> (familyProperties.queueFlags() & type.getBit()) == 0 ? 0 : 1).reduce(Integer::sum).orElseThrow();
                    possibleFamilies.put(supportedCount, i);
                }
                if (possibleFamilies.containsKey(1)) {
                    idealFamilies.put(queueType, possibleFamilies.get(1));
                } else {
                    idealFamilies.put(queueType, possibleFamilies.keySet().stream().sorted().findFirst().orElse(-1));
                }
            }
        }
        return idealFamilies;
    }


}
