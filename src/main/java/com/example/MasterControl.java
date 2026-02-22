package com.example;

import com.example.gui.MasterControlScreen;
import com.example.util.AutoTaskManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvents;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MasterControl implements ModInitializer {
	public static final String MOD_ID = "master-control";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Master Control mod initialized!");
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
			initializeClient();
		}
	}

	@net.fabricmc.api.Environment(EnvType.CLIENT)
	private void initializeClient() {
		// 注册按键
		KeyBinding openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.master-control.open",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_F6,
				"category.master-control"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openGuiKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new MasterControlScreen());
				}
			}
		});
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			String text = message.getString();
			AutoTaskManager.getInstance().onChatMessageReceived(text);
			return true; // 允许消息正常显示
		});


		LOGGER.info("Client-side initialization complete. Press F6 to open GUI.");
	}
}