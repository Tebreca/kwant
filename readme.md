# Kwant - Minimalistic stateless 2d Vulkan engine for the JVM


## Scope

### Design mentality
Kwanta is written according to the following mentality;
- Mono and Flux are used exclusively for futures, replays and/or specifically streamable values. So *NOT* for getter values!
- Kwanta calls are stateless, they can always be called in whatever order preferred. Kwanta takes the values at its own pace and order.
- Kwanta is minimal, no more code is written than bare minimally required, without undermining features required to run a general game.
- Javadocs are one of the nicer features of Java, however, I write them after the fact as to not interfere with productivity.
- Any major release needs full documentation in the form of JDoc and normal explanation.

### Native ready


## Getting started

Making a simple fullscreen window with Kwant, all ready for rendering with vulkan is as easy as 5 lines! (Boilerplate excluded)
```java
public static class Game {
    public static void main(String[] args) {
        Engine engine = new Engine().withWindowSettings(Game::getWindowSettings)//
                .withGameInfo(() -> new GameInfo("Game", 1));
        engine.vulkan().subscribe(Game::setupRender);
        engine.start();
    }

    private static void setupRender(VulkanManager vulkanManager) {
        // Your rendering code goes here!
    }

    private static WindowSettings getWindowSettings(long defaultMonitor) {
        return WindowSettings.fullscreen(defaultMonitor, "My Kwanta Game!");
    }
} 
```