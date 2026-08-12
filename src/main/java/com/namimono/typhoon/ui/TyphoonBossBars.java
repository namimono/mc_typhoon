package com.namimono.typhoon.ui;

import com.namimono.typhoon.field.TyphoonDisplay;
import com.namimono.typhoon.field.TyphoonRecord;
import com.namimono.typhoon.field.TyphoonSample;
import com.namimono.typhoon.persist.TyphoonSavedData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * 同维度 Boss 栏：只跟踪一场；结束后清除。单人优先：标题局部强度取维度内首位玩家脚下采样。
 */
public final class TyphoonBossBars {

	private static final Map<UUID, ServerBossEvent> BARS_BY_DIMENSION = new HashMap<>();

	private TyphoonBossBars() {
	}

	public static void tick(ServerLevel level, TyphoonSavedData data) {
		Optional<TyphoonRecord> tracked = data.tracked();
		UUID key = dimensionKey(level);
		if (tracked.isEmpty()) {
			removeBar(key);
			return;
		}

		TyphoonRecord record = tracked.get();
		ServerBossEvent bar = BARS_BY_DIMENSION.computeIfAbsent(
				key,
				ignored -> new ServerBossEvent(
						Component.literal(record.name()),
						BossEvent.BossBarColor.BLUE,
						BossEvent.BossBarOverlay.PROGRESS));
		bar.setVisible(true);
		bar.setProgress((float) Math.max(0.0, Math.min(1.0, record.pathProgress())));

		double sampleX;
		double sampleZ;
		if (level.players().isEmpty()) {
			sampleX = record.field().startX();
			sampleZ = record.field().startZ();
		} else {
			ServerPlayer first = level.players().getFirst();
			sampleX = first.getX();
			sampleZ = first.getZ();
		}
		TyphoonSample sample = record.field().sample(sampleX, sampleZ);
		bar.setName(Component.literal(
				TyphoonDisplay.bossTitle(record.name(), sample.localIntensity(), record.peakGrade())));

		Set<ServerPlayer> present = new HashSet<>(level.players());
		for (ServerPlayer player : present) {
			if (!bar.getPlayers().contains(player)) {
				bar.addPlayer(player);
			}
		}
		for (ServerPlayer viewer : Set.copyOf(bar.getPlayers())) {
			if (!present.contains(viewer)) {
				bar.removePlayer(viewer);
			}
		}
	}

	public static void clear(ServerLevel level) {
		removeBar(dimensionKey(level));
	}

	private static void removeBar(UUID key) {
		ServerBossEvent existing = BARS_BY_DIMENSION.remove(key);
		if (existing != null) {
			existing.removeAllPlayers();
			existing.setVisible(false);
		}
	}

	private static UUID dimensionKey(ServerLevel level) {
		return UUID.nameUUIDFromBytes(level.dimension().location().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}
}
