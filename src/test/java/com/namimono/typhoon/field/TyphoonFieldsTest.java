package com.namimono.typhoon.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TyphoonFieldsTest {

	private static final double EPS = 1e-9;

	@Test
	void spawnAppliesSpecDefaultsAndGeneratesNameFromShortId() {
		AtomicInteger seq = new AtomicInteger();
		TyphoonFields fields = new TyphoonFields(() -> "ab" + seq.incrementAndGet());

		TyphoonRecord spawned = fields.spawn(TyphoonSpawnRequest.of(0, 0, 100, 0));

		assertEquals("ab1", spawned.id());
		assertEquals("Typhoon-ab1", spawned.name());
		assertEquals(6, spawned.peakGrade());
		assertEquals(16 * 16.0, spawned.influenceHalfWidth(), EPS);
		assertEquals(12000, spawned.durationTicks());
		assertEquals(0.0, spawned.pathProgress(), EPS);
	}

	@Test
	void spawnAcceptsOverridesIncludingWindAndCustomName() {
		TyphoonFields fields = new TyphoonFields(() -> "xy9");

		TyphoonRecord spawned = fields.spawn(new TyphoonSpawnRequest(
				0, 0, 100, 0,
				4,
				8,
				200,
				"海燕",
				0.0,
				1.0));

		assertEquals("xy9", spawned.id());
		assertEquals("海燕", spawned.name());
		assertEquals(4, spawned.peakGrade());
		assertEquals(8 * 16.0, spawned.influenceHalfWidth(), EPS);
		assertEquals(200, spawned.durationTicks());

		spawned.field().tick(100);
		TyphoonSample ahead = spawned.field().sample(60, 0);
		assertEquals(0.0, ahead.windX(), EPS);
		assertEquals(1.0, ahead.windZ(), EPS);
	}

	@Test
	void clearRemovesAllAndListReportsIdNameProgress() {
		TyphoonFields fields = new TyphoonFields(() -> "a");
		fields.spawn(TyphoonSpawnRequest.of(0, 0, 100, 0).withDurationTicks(100));
		fields.tick(25);

		List<TyphoonSummary> listed = fields.list();
		assertEquals(1, listed.size());
		assertEquals("a", listed.getFirst().id());
		assertEquals("Typhoon-a", listed.getFirst().name());
		assertEquals(0.25, listed.getFirst().pathProgress(), EPS);

		fields.clear();
		assertTrue(fields.list().isEmpty());
	}

	@Test
	void tickAdvancesAllAndRemovesFinishedTyphoons() {
		AtomicInteger seq = new AtomicInteger();
		TyphoonFields fields = new TyphoonFields(() -> "t" + seq.incrementAndGet());
		fields.spawn(TyphoonSpawnRequest.of(0, 0, 100, 0).withDurationTicks(100));
		fields.spawn(TyphoonSpawnRequest.of(0, 0, 50, 0).withDurationTicks(40));

		fields.tick(40);
		assertEquals(1, fields.list().size());
		assertEquals("t1", fields.list().getFirst().id());
		assertEquals(0.4, fields.list().getFirst().pathProgress(), EPS);

		fields.tick(60);
		assertTrue(fields.list().isEmpty());
	}

	@Test
	void trackedTyphoonIsMostRecentlySpawnedActiveOne() {
		AtomicInteger seq = new AtomicInteger();
		TyphoonFields fields = new TyphoonFields(() -> "p" + seq.incrementAndGet());
		fields.spawn(TyphoonSpawnRequest.of(0, 0, 100, 0).withDurationTicks(100).withName("first"));
		fields.spawn(TyphoonSpawnRequest.of(0, 0, 100, 0).withDurationTicks(100).withName("second"));

		assertEquals("second", fields.tracked().orElseThrow().name());

		fields.tick(100);
		assertTrue(fields.tracked().isEmpty());
	}

	@Test
	void snapshotRoundTripPreservesProgressAndSampling() {
		TyphoonFields original = new TyphoonFields(() -> "snap");
		original.spawn(new TyphoonSpawnRequest(0, 0, 100, 0, 6, 10, 100, "海燕", null, null));
		original.tick(50);

		List<TyphoonSnapshot> snapshots = original.snapshots();
		TyphoonFields restored = TyphoonFields.fromSnapshots(snapshots);

		assertEquals(1, restored.list().size());
		assertEquals(0.5, restored.list().getFirst().pathProgress(), EPS);
		assertEquals("海燕", restored.list().getFirst().name());
		assertEquals(6.0, restored.tracked().orElseThrow().field().sample(60, 0).localIntensity(), EPS);
	}

	@Test
	void bossTitleFormatsNameAndLocalOverPeak() {
		assertEquals(
				"台风「海燕」 · 局部强度 3/6",
				TyphoonDisplay.bossTitle("海燕", 3.0, 6));
		assertEquals(
				"台风「Typhoon-ab」 · 局部强度 0/6",
				TyphoonDisplay.bossTitle("Typhoon-ab", 0.2, 6));
	}
}
