package com.tebreca.kwant.vk.queue;

import org.lwjgl.vulkan.VK13;

@SuppressWarnings("unused")
public enum QueueType {

    GRAPHICS(VK13.VK_QUEUE_GRAPHICS_BIT),
    COMPUTE(VK13.VK_QUEUE_COMPUTE_BIT),
    TRANSFER(VK13.VK_QUEUE_TRANSFER_BIT);

    private final int bit;

    public int getBit() {
        return bit;
    }

    QueueType(int queueBit) {
        bit = queueBit;
    }
}


