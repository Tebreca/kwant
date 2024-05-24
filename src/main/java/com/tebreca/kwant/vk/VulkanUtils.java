package com.tebreca.kwant.vk;

import static org.lwjgl.vulkan.VK13.*;
public class VulkanUtils {

    public static void assertResult(int result, String error){
        if (result != VK_SUCCESS){
            AssertionError sub = new AssertionError("Native vulkan call did not return VK_SUCCES (=0), but rather: " + result);
            throw new RuntimeException(error, sub);
        }
    }

}