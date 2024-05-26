package com.tebreca.kwant.vk.queue;

import com.tebreca.kwant.vk.VulkanManager;
import org.lwjgl.vulkan.VkQueue;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Optional;

@SuppressWarnings("unused")
public class QueueBuilder {

    final QueueType queueType;
    private final VulkanManager vulkanManager;
    float priority = 1.0f;

    boolean present = false;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    Optional<Integer> familyOverride = Optional.empty();

    public QueueBuilder(QueueType queueType, VulkanManager manager) {
        this.queueType = queueType;
        vulkanManager = manager;
    }

    public QueueBuilder family(int familyOverride) {
        this.familyOverride = Optional.of(familyOverride);
        return this;
    }

    public QueueBuilder priority(float priority) {
        this.priority = priority;
        return this;
    }

    public Mono<VkQueue> submit() {
        Sinks.One<VkQueue> one = Sinks.one();
        vulkanManager.submitQueue(new QueueInfo(one, familyOverride.orElse(vulkanManager.getIdealFamilyIndex(queueType)), priority));
        return one.asMono();
    }

    public record QueueInfo(Sinks.One<VkQueue> sink, int familyIndex, float priority) {

    }
}
