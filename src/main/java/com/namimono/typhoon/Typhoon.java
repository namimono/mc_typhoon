package com.namimono.typhoon;

import com.namimono.typhoon.command.TyphoonCommands;
import com.namimono.typhoon.persist.TyphoonSavedData;
import com.namimono.typhoon.ui.TyphoonBossBars;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Typhoon implements ModInitializer {
	public static final String MOD_ID = "typhoon";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register(TyphoonCommands::register);
		ServerTickEvents.END_WORLD_TICK.register(level -> {
			TyphoonSavedData data = TyphoonSavedData.getIfPresent(level);
			if (data == null) {
				return;
			}
			data.tick();
			TyphoonBossBars.tick(level, data);
		});
		LOGGER.info("Typhoon mod initialized");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
