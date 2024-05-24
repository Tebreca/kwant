package com.tebreca.kwant.vk.shader;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkSpecializationInfo;

import javax.annotation.Nullable;
public record Shader(long module, int stage, String name, @Nullable VkSpecializationInfo specializationInfo) {

    VkPipelineShaderStageCreateInfo generate(MemoryStack stack) {
        //TODO implement ? Maybe move out of record
        return VkPipelineShaderStageCreateInfo.calloc(stack);
    }

    boolean hasSpecializationInfo() {
        return specializationInfo != null;
    }
}