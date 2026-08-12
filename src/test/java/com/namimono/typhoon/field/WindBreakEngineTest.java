package com.namimono.typhoon.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WindBreakEngineTest {

	private static final double EPS = 1e-9;

	/** Path (0,0)→(100,0); center at 50; peak 6 → eyewall at x≈60. */
	private static TyphoonField eyewallField() {
		TyphoonField field = TyphoonField.create(0, 0, 100, 0, 6, 40, 100);
		field.tick(50);
		return field;
	}

	@Test
	void noSurvivalAdventurePlayersMeansNoScanOrActions() {
		TyphoonField field = eyewallField();
		FakeWorld world = FakeWorld.openColumn(60, 64, 0, WindBlockFacts.exception(1));
		WindBreakEngine engine = new WindBreakEngine();

		List<WindBreakAction> actions = engine.tick(field, List.of(), world);
		assertTrue(actions.isEmpty());
		assertEquals(0, world.probeCount());
	}

	@Test
	void windwardFragileBlockAccumulatesCrackThenDestroys() {
		TyphoonField field = eyewallField();
		assertEquals(6.0, field.sample(60, 0).localIntensity(), EPS);

		FakeWorld world = FakeWorld.openColumn(60, 64, 0, WindBlockFacts.exception(1));
		WindBreakEngine engine = new WindBreakEngine();
		List<BreakScanPlayer> players = List.of(new BreakScanPlayer(60, 64, 0));

		boolean sawCrack = false;
		boolean destroyed = false;
		int lastStage = -2;
		for (int i = 0; i < 200; i++) {
			List<WindBreakAction> actions = engine.tick(field, players, world);
			for (WindBreakAction a : actions) {
				assertTrue(a.breakerId() < 0, "negative breaker id pool");
				if (a.destroy()) {
					destroyed = true;
					assertEquals(-1, a.stage());
				} else if (a.stage() >= 0) {
					sawCrack = true;
					assertTrue(a.stage() >= lastStage || lastStage < 0);
					lastStage = a.stage();
				}
			}
			if (destroyed) {
				break;
			}
		}
		assertTrue(sawCrack);
		assertTrue(destroyed);
	}

	@Test
	void leewardBlockIsNotPrioritizedForBreak() {
		TyphoonField field = eyewallField();
		FakeWorld world = new FakeWorld();
		// Wall: (59) blocks (60) from +X wind → 60 is leeward
		world.put(59, 64, 0, WindBlockFacts.exception(1), true);
		world.put(60, 64, 0, WindBlockFacts.exception(1), true);

		WindBreakEngine engine = new WindBreakEngine();
		List<BreakScanPlayer> players = List.of(new BreakScanPlayer(60, 64, 0));

		for (int i = 0; i < 30; i++) {
			List<WindBreakAction> actions = engine.tick(field, players, world);
			for (WindBreakAction a : actions) {
				assertFalse(a.x() == 60 && a.y() == 64 && a.z() == 0,
						"leeward block should not start cracking");
			}
		}
	}

	@Test
	void excessAtMostZeroClearsProgress() {
		TyphoonField field = eyewallField();
		FakeWorld world = FakeWorld.openColumn(60, 64, 0, WindBlockFacts.exception(1));
		WindBreakEngine engine = new WindBreakEngine();
		List<BreakScanPlayer> players = List.of(new BreakScanPlayer(60, 64, 0));

		// Build some progress (excess 5 → ~20 ticks to break; stop earlier)
		for (int i = 0; i < 8; i++) {
			engine.tick(field, players, world);
		}
		assertTrue(engine.activeCrackCount() > 0);

		// Swap to grade 6 under peak 6 → excess 0
		world.put(60, 64, 0, WindBlockFacts.exception(6), true);
		List<WindBreakAction> actions = engine.tick(field, players, world);
		boolean cleared = actions.stream().anyMatch(a -> a.stage() < 0 && !a.destroy());
		assertTrue(cleared);
		assertEquals(0, engine.activeCrackCount());
	}

	@Test
	void eyeIntensityClearsProgress() {
		TyphoonField field = TyphoonField.create(0, 0, 100, 0, 6, 40, 100);
		field.tick(50); // eye at 50
		FakeWorld world = FakeWorld.openColumn(50, 64, 0, WindBlockFacts.exception(1));
		WindBreakEngine engine = new WindBreakEngine();
		// Seed a crack as if leftover from before eye arrived
		engine.debugSeedCrack(50, 64, 0, 0.5f, -1);

		List<WindBreakAction> actions = engine.tick(
				field, List.of(new BreakScanPlayer(50, 64, 0)), world);
		assertTrue(actions.stream().anyMatch(a -> a.stage() < 0 && !a.destroy()));
		assertEquals(0, engine.activeCrackCount());
	}

	@Test
	void scanRespectsPerTickBudgetAndConcurrentCap() {
		TyphoonField field = eyewallField();
		FakeWorld world = new FakeWorld();
		// Many open fragile blocks around player
		for (int x = 40; x <= 80; x++) {
			for (int z = -5; z <= 5; z++) {
				world.put(x, 64, z, WindBlockFacts.exception(1), true);
			}
		}
		WindBreakEngine engine = new WindBreakEngine();
		List<BreakScanPlayer> players = List.of(new BreakScanPlayer(60, 64, 0));

		int probesBefore = world.probeCount();
		engine.tick(field, players, world);
		int probesThisTick = world.probeCount() - probesBefore;
		assertTrue(probesThisTick <= WindBreakEngine.SCAN_BUDGET);
		assertTrue(probesThisTick >= 1);
		assertTrue(engine.activeCrackCount() <= WindBreakEngine.MAX_CONCURRENT_CRACKS);
	}

	@Test
	void higherExcessBreaksFasterThanLowerExcess() {
		TyphoonField peak6 = eyewallField();
		TyphoonField peak2 = TyphoonField.create(0, 0, 100, 0, 2, 40, 100);
		peak2.tick(50);

		int ticksHigh = ticksToDestroy(peak6, WindBlockFacts.exception(1)); // excess ~5
		int ticksLow = ticksToDestroy(peak2, WindBlockFacts.exception(1)); // excess ~1
		assertTrue(ticksHigh < ticksLow);
		assertTrue(ticksLow >= 80 && ticksLow <= 120, "excess 1 ≈ 4–6s: " + ticksLow);
		assertTrue(ticksHigh <= 30, "excess 3+ ≈ 1s: " + ticksHigh);
	}

	@Test
	void immuneAndNonParticipatingNeverCrack() {
		TyphoonField field = eyewallField();
		FakeWorld world = new FakeWorld();
		world.put(60, 64, 0, WindBlockFacts.immuneBlock(), true);
		world.put(61, 64, 0, WindBlockFacts.skip(), false);
		WindBreakEngine engine = new WindBreakEngine();
		List<BreakScanPlayer> players = List.of(new BreakScanPlayer(60, 64, 0));

		for (int i = 0; i < 50; i++) {
			assertTrue(engine.tick(field, players, world).isEmpty());
		}
	}

	private static int ticksToDestroy(TyphoonField field, WindBlockFacts facts) {
		FakeWorld world = FakeWorld.openColumn(60, 64, 0, facts);
		WindBreakEngine engine = new WindBreakEngine();
		List<BreakScanPlayer> players = List.of(new BreakScanPlayer(60, 64, 0));
		for (int i = 1; i <= 300; i++) {
			List<WindBreakAction> actions = engine.tick(field, players, world);
			if (actions.stream().anyMatch(WindBreakAction::destroy)) {
				return i;
			}
		}
		throw new AssertionError("did not destroy");
	}

	/** In-memory probe world for typhoon-field seam tests. */
	static final class FakeWorld implements WindBreakWorld {
		private final Map<Long, WindBlockFacts> facts = new HashMap<>();
		private final Set<Long> solids = new HashSet<>();
		private int probeCount;

		static FakeWorld openColumn(int x, int y, int z, WindBlockFacts block) {
			FakeWorld w = new FakeWorld();
			w.put(x, y, z, block, true);
			return w;
		}

		void put(int x, int y, int z, WindBlockFacts f, boolean solid) {
			long k = key(x, y, z);
			facts.put(k, f);
			if (solid) {
				solids.add(k);
			} else {
				solids.remove(k);
			}
		}

		int probeCount() {
			return probeCount;
		}

		@Override
		public WindBlockFacts factsAt(int x, int y, int z) {
			probeCount++;
			return facts.getOrDefault(key(x, y, z), WindBlockFacts.skip());
		}

		@Override
		public boolean isSolid(int x, int y, int z) {
			return solids.contains(key(x, y, z));
		}

		@Override
		public boolean isChunkLoaded(int blockX, int blockZ) {
			return true;
		}

		private static long key(int x, int y, int z) {
			return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
		}
	}
}
