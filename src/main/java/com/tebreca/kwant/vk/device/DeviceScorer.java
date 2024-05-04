package com.tebreca.kwant.vk.device;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import static org.lwjgl.vulkan.VK13.*;

public interface DeviceScorer {

    int getScore(VkPhysicalDevice physicalDevice);

    static int simple(VkPhysicalDevice device) {
        int score = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var deviceProperties = VkPhysicalDeviceProperties.calloc(stack);
            var deviceFeatures = VkPhysicalDeviceFeatures.calloc(stack);
            vkGetPhysicalDeviceFeatures(device, deviceFeatures);
            vkGetPhysicalDeviceProperties(device, deviceProperties);

            score += switch (deviceProperties.deviceType()) {
                case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU -> 1000;
                case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU -> 500;
                case VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU -> 300;
                case VK_PHYSICAL_DEVICE_TYPE_CPU -> 100;
                default -> 0;
            };

            score += deviceProperties.limits().maxImageDimension2D();

            var size = stack.callocInt(1);
            vkGetPhysicalDeviceQueueFamilyProperties(device, size, null);
            VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.calloc(size.get(), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(device, size.clear(), queueFamilies);

            boolean supportsGraphics = false;

            while (queueFamilies.hasRemaining()) {
                supportsGraphics |= (queueFamilies.get().queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0;
            }

            if (!deviceFeatures.geometryShader() || !supportsGraphics) {
                score = Integer.MIN_VALUE;
            }
        }
        return score;
    }

}