import com.tebreca.kwant.Engine;
import com.tebreca.kwant.general.GameInfo;
import com.tebreca.kwant.vk.VulkanManager;
import com.tebreca.kwant.window.WindowSettings;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;

public class TestEngine {

    public static void main(String[] args) {
        Engine engine = new Engine().withWindowSettings(TestEngine::getWindowSettings)
                .withGameInfo(() -> new GameInfo("Game", 1));
        engine.vulkan().subscribe(TestEngine::setupRender);
        engine.enableValidationLayers().start();
    }

    private static void setupRender(VulkanManager vulkanManager) {

    }

    private static WindowSettings getWindowSettings(long defaultMonitor) {
        return WindowSettings.normal(defaultMonitor, "Hello World!", new Vector2i(1600, 900), false);
    }

}
