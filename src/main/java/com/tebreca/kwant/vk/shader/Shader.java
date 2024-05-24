package com.tebreca.kwant.vk.shader;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkSpecializationInfo;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

public record Shader(long module, int stage, String name, @Nullable VkSpecializationInfo specializationInfo,
                     int shaderFlags) {

    public VkPipelineShaderStageCreateInfo populateShaderStageCreateInfo(MemoryStack stack) {
        VkPipelineShaderStageCreateInfo createInfo = VkPipelineShaderStageCreateInfo.calloc(stack);
        createInfo.sType$Default();
        createInfo.module(module);
        createInfo.flags(shaderFlags);
        ByteBuffer nameBuffer = stack.ASCII(name);
        createInfo.pName(nameBuffer);
        createInfo.pSpecializationInfo(specializationInfo);
        return createInfo;
    }

    boolean hasSpecializationInfo() {
        return specializationInfo != null;
    }
}