import com.tebreca.kwant.Engine;
import com.tebreca.kwant.general.GameInfo;
import com.tebreca.kwant.vk.VulkanManager;
import com.tebreca.kwant.vk.queue.QueueType;
import com.tebreca.kwant.glfw.window.WindowSettings;
import org.joml.Vector2i;
import org.lwjgl.vulkan.VkQueue;

public class TestEngine {

    private VkQueue graphicsQueue;
    private VkQueue transferQueue;

    public static void main(String[] args) {
        Engine engine = new Engine().withWindowSettings(TestEngine::getWindowSettings)
                .withGameInfo(new GameInfo("Game", 1));
        engine.withValidationLayer("VK_LAYER_KHRONOS_validation").start();
    }

    private void setupRender(VulkanManager vulkanManager) {
        vulkanManager.queue(QueueType.TRANSFER).submit().subscribe(vkQueue -> transferQueue = vkQueue);
        vulkanManager.queue(QueueType.GRAPHICS).submit().subscribe(vkQueue -> graphicsQueue = vkQueue);

    }

    private static WindowSettings getWindowSettings(long defaultMonitor) {
        return WindowSettings.normal(defaultMonitor, "Hello World!", new Vector2i(1600, 900), false);
    }

}
