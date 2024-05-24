package com.tebreca.kwant.vk.shader;

import com.tebreca.kwant.vk.VulkanManager;
import com.tebreca.kwant.vk.VulkanUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSpecializationInfo;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK13.vkCreateShaderModule;

@SuppressWarnings({"unused"})
public class ShaderBuilder {

    private final Mono<VkDevice> device;
    private final File resource;
    private final Sinks.One<Shader> one = Sinks.one();
    private int stage;

    private String name = "main";

    VkSpecializationInfo specializationInfo = null;

    public ShaderBuilder stage(int stage) {
        this.stage = stage;
        return this;
    }

    public ShaderBuilder name(String name) {
        this.name = name;
        return this;
    }

    public ShaderBuilder specializationInfo(VkSpecializationInfo specializationInfo) {
        this.specializationInfo = specializationInfo;
        return this;
    }

    @SuppressWarnings("deprecation")
    public ShaderBuilder(File resource, VulkanManager manager) throws RuntimeException {
        this.device = manager.virtualDevice();
        one.asMono().subscribe(manager::withShader);
        this.resource = resource;
    }

    public Mono<Shader> build() {
        device.subscribeOn(Schedulers.immediate()).subscribe(device -> {
            try (FileInputStream fileInputStream = new FileInputStream(resource); var stack = MemoryStack.stackPush()) {
                byte[] raw = fileInputStream.readAllBytes();
                int size = raw.length;
                VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack);
                ByteBuffer data = stack.calloc(size);
                data.put(raw).flip();
                info.pCode(data);
                info.sType$Default();
                LongBuffer module = stack.callocLong(1);
                VulkanUtils.assertResult(vkCreateShaderModule(device, info, null, module), "Failed to load in shader %s".formatted(resource.getAbsolutePath()));
                one.tryEmitValue(new Shader(module.get(), stage, name, specializationInfo));
            } catch (IOException e) {
                throw new RuntimeException("Failed to read shader data from file!", e);
            }
        });
        return one.asMono();
    }


}
