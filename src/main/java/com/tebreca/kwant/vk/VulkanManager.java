package com.tebreca.kwant.vk;

import org.lwjgl.vulkan.VkInstance;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import static org.lwjgl.vulkan.VK13.vkDestroyInstance;

public class VulkanManager {

    private final VkInstance instance;

    public VulkanManager(VkInstance vulkan) {
        instance = vulkan;
    }


    private final Sinks.One<VkInstance> onCleanup = Sinks.one();

    public Mono<VkInstance> onCleanup() {
        return onCleanup.asMono();
    }


    public VkInstance raw(VkInstance instance) {
        return instance;
    }


    public void setup() {
    }

    public void cleanup() {
        // It may be better not to throw here and just continue. TODO: logger.error to inform user about this problem
        onCleanup.tryEmitValue(instance);
        vkDestroyInstance(instance, null);
    }
}
