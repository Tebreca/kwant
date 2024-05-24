package com.tebreca.kwant.vk.pipeline;

import com.tebreca.kwant.vk.shader.Shader;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;

import static org.lwjgl.vulkan.VK13.*;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

public class PipelineBuilder {

    private int flags = 0x0b;


    public PipelineBuilder withFlags(int flags) {
        this.flags |= flags;
        return this;
    }


    private final List<Shader> shaders = new ArrayList<>();

    public GraphicsPipeline build() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkGraphicsPipelineCreateInfo createInfo = VkGraphicsPipelineCreateInfo.calloc(stack).sType$Default();

            createInfo.flags(flags);
            VkPipelineShaderStageCreateInfo.Buffer shaders = VkPipelineShaderStageCreateInfo.calloc(this.shaders.size(), stack);
            this.shaders.stream().map(shader -> shader.populateShaderStageCreateInfo(stack)).forEach(shaders::put);
            createInfo.pStages(shaders.flip());


            LongBuffer pointer = stack.callocLong(1);

            return new GraphicsPipeline(pointer.get());
        }
    }
}
