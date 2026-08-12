package com.namimono.typhoon;

import com.namimono.typhoon.breakage.WindBreakApplier;
import com.namimono.typhoon.command.TyphoonCommands;
import com.namimono.typhoon.drop.BlownDropApplier;
import com.namimono.typhoon.network.TyphoonSync;
import com.namimono.typhoon.persist.TyphoonSavedData;
import com.namimono.typhoon.player.PlayerWindApplier;
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
		TyphoonSync.register();
		CommandRegistrationCallback.EVENT.register(TyphoonCommands::register);
		ServerTickEvents.END_WORLD_TICK.register(level -> {
			TyphoonSavedData data = TyphoonSavedData.getIfPresent(level);
			if (data == null) {
				return;
			}
			data.tick();
			PlayerWindApplier.tick(level, data);
			WindBreakApplier.tick(level, data);
			BlownDropApplier.tick(level, data);
			TyphoonBossBars.tick(level, data);
			TyphoonSync.tick(level, data);
		});
		LOGGER.info("Typhoon mod initialized");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
