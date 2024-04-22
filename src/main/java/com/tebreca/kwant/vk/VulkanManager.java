package com.tebreca.kwant.vk;

import org.lwjgl.vulkan.VkInstance;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import static org.lwjgl.vulkan.VK13.*;
public class VulkanManager {

    private final VkInstance instance;

    public VulkanManager(VkInstance vulkan) {
        instance = vulkan;
    }

    private final Sinks.One<VkInstance> onCleanup = Sinks.one();

    public Mono<VkInstance> onCleanup(){
        return onCleanup.asMono();
    }

    public void cleanup() {
        // Just pray for the best I guess? If we error here worst case scenario JNI has some fun memory to clear >:)
        onCleanup.tryEmitValue(instance);
        vkDestroyInstance(instance, null);
    }



    public VkInstance raw (VkInstance instance){
        return instance;
    }
}
