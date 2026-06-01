package net.arm.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class AreaClient implements ClientModInitializer {
    private static AreaClient instance;

    private boolean isAreaActive = true;
    private KeyBinding toggleAreaKey;
    private KeyBinding openMenuKey;

    @Override
    public void onInitializeClient() {
        instance = this;

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.kotaradius.arm.open_menu.screen",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Y,
                "key.kotaradius.category.arm.keybinds"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                client.setScreen(new AreaConfigScreen());
            }
        });

        AreaCommand.register();

        BlockOutlineRenderer.init();
    }

    public static AreaClient getInstance() {
        return instance;
    }

    public boolean isAreaActive() {
        return isAreaActive;
    }
}