package com.tebreca.kwant.vk.swapchain;

import com.tebreca.kwant.vk.VulkanManager;
import com.tebreca.kwant.vk.VulkanUtils;
import org.joml.Vector2i;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.VK_FORMAT_B8G8R8A8_SRGB;

public class SwapChainManager {

    private final long surface;
    private final VkQueue presentQueue;
    private final VulkanManager vulkanManager;
    private VkDevice device;
    private long swapchain;

    private long[] imageHandles;
    private long[] imageViewHandles;


    public SwapChainManager(VulkanManager vulkanManager, long surface, VkQueue presentQueue) {
        this.vulkanManager = vulkanManager;
        vulkanManager.virtualDevice().subscribe(vkDevice -> device = vkDevice);
        this.surface = surface;
        this.presentQueue = presentQueue;
    }

    public void createSwapChain(GraphicsSettings settings) {
        //TODO: more configurable
        try (var stack = MemoryStack.stackPush()) {
            VkSurfaceFormatKHR surfaceFormatKHR = selectSurfaceFormat(stack);
            VkSurfaceCapabilitiesKHR surfaceCapabilitiesKHR = VkSurfaceCapabilitiesKHR.calloc(stack);
            vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device.getPhysicalDevice(), surface, surfaceCapabilitiesKHR);
            Vector2i res = settings.resolution();
            VkExtent2D min = surfaceCapabilitiesKHR.minImageExtent();
            VkExtent2D max = surfaceCapabilitiesKHR.maxImageExtent();
            int x = Math.clamp(res.x, min.width(), max.width());
            int y = Math.clamp(res.y, min.height(), max.height());
            VkExtent2D extent2D = VkExtent2D.calloc(stack);
            extent2D.set(x, y);
            int maxImageCount = surfaceCapabilitiesKHR.maxImageCount();
            int imageCount = maxImageCount == 0 ? Math.max(settings.buffering(), surfaceCapabilitiesKHR.minImageCount() + 1) : Math.clamp(settings.buffering(), surfaceCapabilitiesKHR.minImageCount(), maxImageCount);

            VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack);
            createInfo.sType$Default();
            createInfo.surface(surface);
            createInfo.imageFormat(surfaceFormatKHR.format());
            createInfo.imageColorSpace(surfaceFormatKHR.colorSpace());
            createInfo.imageExtent(extent2D);
            createInfo.imageArrayLayers(1);
            createInfo.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
            createInfo.minImageCount(imageCount);
            createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
            createInfo.preTransform(surfaceCapabilitiesKHR.currentTransform());
            createInfo.compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR);
            createInfo.presentMode(settings.presentmode());
            createInfo.clipped(true);
            createInfo.oldSwapchain(VK_NULL_HANDLE);

            LongBuffer handle = stack.callocLong(1);
            VulkanUtils.assertResult(vkCreateSwapchainKHR(device, createInfo, null, handle), "Failed to create swapchain!");
            this.swapchain = handle.get();

            IntBuffer size = stack.callocInt(1);
            vkGetSwapchainImagesKHR(device, swapchain, size, null);
            LongBuffer images = stack.callocLong(size.get());
            vkGetSwapchainImagesKHR(device, swapchain, size.clear(), images);
            imageHandles = new long[size.get()];
            images.get(imageHandles);

            images.clear();
            for (long image : imageHandles) {
                LongBuffer view = stack.callocLong(1);
                //TODO: more configurable
                VkImageViewCreateInfo info = VkImageViewCreateInfo.calloc(stack);
                info.sType$Default();
                info.image(image);
                info.viewType(VK_IMAGE_VIEW_TYPE_2D);
                info.format(surfaceFormatKHR.format());
                info.components().set(VK_COMPONENT_SWIZZLE_IDENTITY, VK_COMPONENT_SWIZZLE_IDENTITY, VK_COMPONENT_SWIZZLE_IDENTITY, VK_COMPONENT_SWIZZLE_IDENTITY);
                info.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
                VulkanUtils.assertResult(vkCreateImageView(device, info, null, view), String.format("Failed to create imageView %d of %d", images.position() + 1, images.capacity()));
                images.put(view);
            }
            imageViewHandles = new long[imageHandles.length];
            images.flip().get(imageViewHandles);

        }
    }

    private VkSurfaceFormatKHR selectSurfaceFormat(MemoryStack stack) {
        IntBuffer size = stack.callocInt(1);
        vkGetPhysicalDeviceSurfaceFormatsKHR(device.getPhysicalDevice(), surface, size, null);
        VkSurfaceFormatKHR.Buffer buffer = VkSurfaceFormatKHR.calloc(size.get());
        vkGetPhysicalDeviceSurfaceFormatsKHR(device.getPhysicalDevice(), surface, size.clear(), buffer);

        while (buffer.hasRemaining()) {
            VkSurfaceFormatKHR format = buffer.get();
            if (format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR && format.format() == VK_FORMAT_B8G8R8A8_SRGB)
                return format;
        }

        return buffer.get(0);
    }

    public long getSurface() {
        return surface;
    }

    public VkQueue getPresentQueue() {
        return presentQueue;
    }

    public void destroyChain() {
        for (long handle : imageViewHandles) {
            vkDestroyImageView(device, handle, null);
        }
        vkDestroySwapchainKHR(device, swapchain, null);
        vkDestroySurfaceKHR(vulkanManager.raw(), surface, null);
    }

}