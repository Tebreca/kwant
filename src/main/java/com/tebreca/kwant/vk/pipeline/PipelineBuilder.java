package com.tebreca.kwant.vk.pipeline;

import com.tebreca.kwant.util.FlagHolder;
import com.tebreca.kwant.vk.VulkanManager;
import com.tebreca.kwant.vk.VulkanUtils;
import com.tebreca.kwant.vk.shader.Shader;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.NativeType;

import static org.lwjgl.vulkan.VK13.*;

import org.lwjgl.vulkan.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class PipelineBuilder extends FlagHolder<PipelineBuilder> {
    private final Mono<MemoryStack> onInit;
    private final Mono<VkDevice> deviceMono;
    private Function<MemoryStack, VkPipelineVertexInputStateCreateInfo> vertexInputState = null;

    private final List<Shader> shaders = new ArrayList<>();
    private Function<MemoryStack, VkPipelineInputAssemblyStateCreateInfo> inputAssemblyState = null;

    private Function<MemoryStack, VkPipelineTessellationStateCreateInfo> tesslationState = null;

    public PipelineBuilder(VulkanManager manager) {
        onInit = manager.onInit();
        deviceMono = manager.virtualDevice();
    }

    /**
     * @return Configurator for the vertex input stage of the pipeline
     */
    public VertexInputStageConfigurator vertexInput() {
        return new VertexInputStageConfigurator();
    }

    public InputAssemblyStateConfigurator vertexAssembly() {
        return new InputAssemblyStateConfigurator();
    }

    /**
     * Configures the tesselation state
     * <br><br>
     * As the <a href="https://registry.khronos.org/vulkan/specs/1.3-extensions/man/html/VkPipelineTessellationStateCreateFlags.html">flags currently are unused</a>, rather than use a configurator a simple single field method should suffice
     *
     * @param patchControlPoints see <a href="https://registry.khronos.org/vulkan/specs/1.3-extensions/man/html/VkPipelineTessellationStateCreateInfo.html">vulkan docs</a>
     * @return this builder
     */
    public PipelineBuilder tesselation(int patchControlPoints) {
        tesslationState = stack -> {
            VkPipelineTessellationStateCreateInfo calloc = VkPipelineTessellationStateCreateInfo.calloc(stack).sType$Default();
            calloc.patchControlPoints(patchControlPoints);
            return calloc;
        };
        return this;
    }

    public Mono<GraphicsPipeline> build() {
        Sinks.One<GraphicsPipeline> one = Sinks.one();
        onInit.subscribeOn(Schedulers.immediate()).subscribe(stack -> {
            VkDevice device = deviceMono.block();
            VkGraphicsPipelineCreateInfo.Buffer createInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack).sType$Default();

            createInfo.flags(flags);
            VkPipelineShaderStageCreateInfo.Buffer shaderBuffer = VkPipelineShaderStageCreateInfo.calloc(this.shaders.size(), stack);
            this.shaders.stream().map(shader -> shader.populateShaderStageCreateInfo(stack)).forEach(shaderBuffer::put);
            createInfo.pStages(shaderBuffer.flip());

            if (vertexInputState != null) {
                createInfo.pVertexInputState(vertexInputState.apply(stack));
            }

            if (inputAssemblyState != null) {
                createInfo.pInputAssemblyState(inputAssemblyState.apply(stack));
            }

            if (tesslationState != null) {
                createInfo.pTessellationState(tesslationState.apply(stack));
            }


            LongBuffer pointer = stack.callocLong(1);

            boolean caching = false; // TODO
            VulkanUtils.assertResult(vkCreateGraphicsPipelines(device, caching ? 1 : VK_NULL_HANDLE, createInfo, null, pointer), "Failed to create Graphics pipeline!");
            one.tryEmitValue(new GraphicsPipeline(pointer.get()));
        });
        return one.asMono();
    }


    public class VertexInputStageConfigurator extends FlagHolder<VertexInputStageConfigurator> {

        private final List<Function<MemoryStack, VkVertexInputAttributeDescription>> inputAttributes = new ArrayList<>();

        private final List<Function<MemoryStack, VkVertexInputBindingDescription>> inputBindings = new ArrayList<>();

        private VertexInputStageConfigurator() {

        }

        /**
         * Constructs a new vertex binding and attribute, with a VkVertexInputRate of VK_VERTEX_INPUT_RATE_VERTEX
         */
        public void vertex(int binding, int stride, int location, int offset, @NativeType("VkFormat") int format) {
            populate(0, binding, stride, location, offset, format);
        }

        /**
         * Constructs a new vertex binding and attribute, with a VkVertexInputRate of VK_VERTEX_INPUT_RATE_INSTANCE
         */
        public void instance(int binding, int stride, int location, int offset, @NativeType("VkFormat") int format) {
            populate(1, binding, stride, location, offset, format);
        }

        private void populate(int i, int binding, int stride, int location, int offset, int format) {
            inputBindings.add(m -> {
                VkVertexInputBindingDescription struct = VkVertexInputBindingDescription.calloc(m);
                struct.binding(binding);
                struct.stride(stride);
                struct.inputRate(i);
                return struct;
            });
            inputAttributes.add(m -> {
                VkVertexInputAttributeDescription struct = VkVertexInputAttributeDescription.calloc(m);
                struct.binding(binding);
                struct.format(format);
                struct.location(location);
                struct.offset(offset);
                return struct;
            });
        }

        public void submit() {
            PipelineBuilder.this.takeVertexInput((MemoryStack stack) -> {
                VkVertexInputAttributeDescription.Buffer attributes = VkVertexInputAttributeDescription.calloc(inputAttributes.size(), stack);
                VkVertexInputBindingDescription.Buffer bindings = VkVertexInputBindingDescription.calloc(inputBindings.size(), stack);

                inputAttributes.stream().map(f -> f.apply(stack)).forEach(attributes::put);
                inputBindings.stream().map(f -> f.apply(stack)).forEach(bindings::put);

                VkPipelineVertexInputStateCreateInfo calloc = VkPipelineVertexInputStateCreateInfo.calloc(stack).sType$Default();

                calloc.flags(flags);
                calloc.pVertexAttributeDescriptions(attributes.flip());
                calloc.pVertexBindingDescriptions(bindings.flip());
                return calloc;
            });
        }
    }

    private void takeVertexInput(Function<MemoryStack, VkPipelineVertexInputStateCreateInfo> function) {
        this.vertexInputState = function;
    }

    private void takeInputAssembly(Function<MemoryStack, VkPipelineInputAssemblyStateCreateInfo> function) {
        this.inputAssemblyState = function;
    }

    public class InputAssemblyStateConfigurator extends FlagHolder<InputAssemblyStateConfigurator> {

        private int topology;

        private boolean primitiveRestart = false;

        public void submit() {
            PipelineBuilder.this.takeInputAssembly((MemoryStack stack) -> {
                VkPipelineInputAssemblyStateCreateInfo calloc = VkPipelineInputAssemblyStateCreateInfo.calloc(stack).sType$Default();
                calloc.flags(flags);
                calloc.primitiveRestartEnable(primitiveRestart);
                calloc.topology(topology);
                return calloc;
            });
        }

        public InputAssemblyStateConfigurator topology(int topology) {
            this.topology = topology;
            return this;
        }

        /**
         * Enables primitive restart, this is OFF by default!
         */
        public InputAssemblyStateConfigurator primitiveRestart() {
            primitiveRestart = true;
            return this;
        }


    }
}
