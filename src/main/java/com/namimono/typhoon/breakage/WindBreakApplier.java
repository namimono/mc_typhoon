package com.namimono.typhoon.breakage;

import com.namimono.typhoon.field.BreakScanPlayer;
import com.namimono.typhoon.field.TyphoonField;
import com.namimono.typhoon.field.TyphoonRecord;
import com.namimono.typhoon.field.WindBreakAction;
import com.namimono.typhoon.field.WindBreakEngine;
import com.namimono.typhoon.persist.TyphoonSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * 适配层：台风场风力破坏引擎 → destroyBlockProgress / destroyBlock。
 */
public final class WindBreakApplier {

	private WindBreakApplier() {
	}

	public static void tick(ServerLevel level, TyphoonSavedData data) {
		WindBreakEngine engine = data.windBreakEngine();
		Optional<TyphoonRecord> tracked = data.tracked();
		if (tracked.isEmpty()) {
			apply(level, engine.clearAll());
			return;
		}

		List<BreakScanPlayer> scanners = survivalAdventureAnchors(level);
		ServerWindBreakWorld world = new ServerWindBreakWorld(level);
		TyphoonField field = tracked.orElseThrow().field();
		List<WindBreakAction> actions = engine.tick(field, scanners, world);
		apply(level, actions);
	}

	private static List<BreakScanPlayer> survivalAdventureAnchors(ServerLevel level) {
		List<BreakScanPlayer> out = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (isSurvivalOrAdventure(player)) {
				out.add(new BreakScanPlayer(player.getBlockX(), player.getBlockY(), player.getBlockZ()));
			}
		}
		return out;
	}

	private static boolean isSurvivalOrAdventure(ServerPlayer player) {
		GameType type = player.gameMode.getGameModeForPlayer();
		return type == GameType.SURVIVAL || type == GameType.ADVENTURE;
	}

	private static void apply(ServerLevel level, List<WindBreakAction> actions) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (WindBreakAction action : actions) {
			pos.set(action.x(), action.y(), action.z());
			if (action.destroy()) {
				level.destroyBlock(pos, true);
				level.destroyBlockProgress(action.breakerId(), pos, -1);
			} else {
				level.destroyBlockProgress(action.breakerId(), pos, action.stage());
			}
		}
	}
}
