package com.tebreca.kwant.vk.device;

import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;

public record DeviceSettings(VkPhysicalDeviceFeatures deviceFeatures, int flags) {


}
