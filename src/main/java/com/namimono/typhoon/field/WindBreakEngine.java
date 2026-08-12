package com.namimono.typhoon.field;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * 风力破坏引擎：玩家窗扫描、迎风/抗风判定、超额渐裂、预算与并发上限。
 * 纯逻辑；Minecraft 发包由适配层消费 {@link WindBreakAction}。
 */
public final class WindBreakEngine {

	/** 每 tick 读块 + 判定预算（规格 64–128 中值）。 */
	public static final int SCAN_BUDGET = 96;
	/** 同时裂纹上限（规格 16–32 中值）。 */
	public static final int MAX_CONCURRENT_CRACKS = 24;
	public static final int PLAYER_HORIZONTAL_RADIUS = 32;
	public static final int PLAYER_VERTICAL_RADIUS = 16;

	private static final int WINDOW_VOLUME =
			(PLAYER_HORIZONTAL_RADIUS * 2 + 1)
					* (PLAYER_VERTICAL_RADIUS * 2 + 1)
					* (PLAYER_HORIZONTAL_RADIUS * 2 + 1);

	/** 相对玩家的偏移，按曼哈顿距离由近及远（滑动光标轮转）。 */
	private static final int[][] OFFSETS = buildOffsetsByManhattan();

	private static final int TICKS_EXCESS_1 = 100; // ~5s
	private static final int TICKS_EXCESS_2 = 50; // ~2.5s
	private static final int TICKS_EXCESS_3_PLUS = 20; // ~1s

	private final Map<Long, Crack> cracks = new HashMap<>();
	private final boolean[] idUsed = new boolean[MAX_CONCURRENT_CRACKS];
	private long cursor;

	public List<WindBreakAction> tick(
			TyphoonField field,
			List<BreakScanPlayer> survivalAdventurePlayers,
			WindBreakWorld world) {
		List<WindBreakAction> actions = new ArrayList<>();
		if (survivalAdventurePlayers.isEmpty()) {
			return actions;
		}

		maintainExisting(field, world, actions);
		discover(field, survivalAdventurePlayers, world, actions);
		return actions;
	}

	public int activeCrackCount() {
		return cracks.size();
	}

	/** 释放全部裂纹并返回清进度动作（台风结束/清除时用）。 */
	public List<WindBreakAction> clearAll() {
		List<WindBreakAction> actions = new ArrayList<>(cracks.size());
		for (Crack crack : cracks.values()) {
			actions.add(clearAction(crack));
			releaseId(crack.breakerId);
		}
		cracks.clear();
		return actions;
	}

	/** 测试用：预置裂纹进度。 */
	void debugSeedCrack(int x, int y, int z, float progress, int preferredId) {
		int id = preferredId;
		if (id >= 0) {
			id = allocateId();
		} else {
			int idx = -id - 1;
			if (idx >= 0 && idx < idUsed.length) {
				idUsed[idx] = true;
			}
		}
		if (id == 0) {
			return;
		}
		cracks.put(key(x, y, z), new Crack(x, y, z, id, progress, -1));
	}

	private void maintainExisting(TyphoonField field, WindBreakWorld world, List<WindBreakAction> actions) {
		Iterator<Map.Entry<Long, Crack>> it = cracks.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Long, Crack> entry = it.next();
			Crack crack = entry.getValue();
			TyphoonSample sample = field.sample(crack.x + 0.5, crack.z + 0.5);
			WindBlockFacts facts = world.factsAt(crack.x, crack.y, crack.z);
			OptionalInt resist = WindResistance.resolve(facts);
			double excess = resist.isEmpty()
					? 0.0
					: sample.localIntensity() - resist.getAsInt();

			boolean stillWindward = resist.isPresent()
					&& WindwardFace.isWindward(
							crack.x, crack.y, crack.z, sample.windX(), sample.windZ(), world);

			if (excess <= 0.0 || !stillWindward || !sample.inInfluenceBand()) {
				actions.add(clearAction(crack));
				releaseId(crack.breakerId);
				it.remove();
				continue;
			}

			crack.progress += progressPerTick(excess);
			int stage = stageOf(crack.progress);
			if (crack.progress >= 1.0f) {
				actions.add(new WindBreakAction(crack.x, crack.y, crack.z, crack.breakerId, -1, true));
				releaseId(crack.breakerId);
				it.remove();
				continue;
			}
			if (stage != crack.lastSentStage) {
				crack.lastSentStage = stage;
				actions.add(new WindBreakAction(crack.x, crack.y, crack.z, crack.breakerId, stage, false));
			}
		}
	}

	private void discover(
			TyphoonField field,
			List<BreakScanPlayer> players,
			WindBreakWorld world,
			List<WindBreakAction> actions) {
		if (cracks.size() >= MAX_CONCURRENT_CRACKS) {
			return;
		}

		long total = (long) players.size() * WINDOW_VOLUME;
		if (total <= 0) {
			return;
		}

		int budget = SCAN_BUDGET;
		while (budget > 0 && cracks.size() < MAX_CONCURRENT_CRACKS) {
			long index = Math.floorMod(cursor, total);
			cursor++;
			budget--;

			int playerIndex = (int) (index / WINDOW_VOLUME);
			int local = (int) (index % WINDOW_VOLUME);
			BreakScanPlayer player = players.get(playerIndex);
			int[] offset = OFFSETS[local];
			int x = player.blockX() + offset[0];
			int y = player.blockY() + offset[1];
			int z = player.blockZ() + offset[2];

			if (!world.isChunkLoaded(x, z)) {
				continue;
			}

			long k = key(x, y, z);
			if (cracks.containsKey(k)) {
				continue;
			}

			TyphoonSample sample = field.sample(x + 0.5, z + 0.5);
			if (!sample.inInfluenceBand() || sample.localIntensity() <= 0.0) {
				continue;
			}

			WindBlockFacts facts = world.factsAt(x, y, z);
			OptionalInt resist = WindResistance.resolve(facts);
			if (resist.isEmpty()) {
				continue;
			}
			double excess = sample.localIntensity() - resist.getAsInt();
			if (excess <= 0.0) {
				continue;
			}
			if (!WindwardFace.isWindward(x, y, z, sample.windX(), sample.windZ(), world)) {
				continue;
			}

			int id = allocateId();
			if (id == 0) {
				break;
			}
			Crack crack = new Crack(x, y, z, id, 0.0f, -1);
			cracks.put(k, crack);
			crack.progress += progressPerTick(excess);
			int stage = stageOf(crack.progress);
			crack.lastSentStage = stage;
			actions.add(new WindBreakAction(x, y, z, id, stage, false));
			if (crack.progress >= 1.0f) {
				actions.add(new WindBreakAction(x, y, z, id, -1, true));
				releaseId(id);
				cracks.remove(k);
			}
		}
	}

	static float progressPerTick(double excess) {
		int ticks;
		if (excess < 2.0) {
			ticks = TICKS_EXCESS_1;
		} else if (excess < 3.0) {
			ticks = TICKS_EXCESS_2;
		} else {
			ticks = TICKS_EXCESS_3_PLUS;
		}
		return 1.0f / ticks;
	}

	private static int stageOf(float progress) {
		if (progress <= 0.0f) {
			return 0;
		}
		return Math.min(9, (int) (progress * 10.0f));
	}

	private WindBreakAction clearAction(Crack crack) {
		return new WindBreakAction(crack.x, crack.y, crack.z, crack.breakerId, -1, false);
	}

	/** 负 id：-1 .. -MAX；0 表示池满。 */
	private int allocateId() {
		for (int i = 0; i < idUsed.length; i++) {
			if (!idUsed[i]) {
				idUsed[i] = true;
				return -(i + 1);
			}
		}
		return 0;
	}

	private void releaseId(int breakerId) {
		int idx = -breakerId - 1;
		if (idx >= 0 && idx < idUsed.length) {
			idUsed[idx] = false;
		}
	}

	private static long key(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}

	private static int[][] buildOffsetsByManhattan() {
		List<int[]> list = new ArrayList<>(WINDOW_VOLUME);
		for (int dy = -PLAYER_VERTICAL_RADIUS; dy <= PLAYER_VERTICAL_RADIUS; dy++) {
			for (int dx = -PLAYER_HORIZONTAL_RADIUS; dx <= PLAYER_HORIZONTAL_RADIUS; dx++) {
				for (int dz = -PLAYER_HORIZONTAL_RADIUS; dz <= PLAYER_HORIZONTAL_RADIUS; dz++) {
					list.add(new int[] {dx, dy, dz});
				}
			}
		}
		list.sort(Comparator.comparingInt(o -> Math.abs(o[0]) + Math.abs(o[1]) + Math.abs(o[2])));
		return list.toArray(new int[0][]);
	}

	private static final class Crack {
		final int x;
		final int y;
		final int z;
		final int breakerId;
		float progress;
		int lastSentStage;

		Crack(int x, int y, int z, int breakerId, float progress, int lastSentStage) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.breakerId = breakerId;
			this.progress = progress;
			this.lastSentStage = lastSentStage;
		}
	}
}
