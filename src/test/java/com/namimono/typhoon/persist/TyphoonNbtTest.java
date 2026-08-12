package com.namimono.typhoon.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.namimono.typhoon.field.TyphoonFields;
import com.namimono.typhoon.field.TyphoonSnapshot;
import com.namimono.typhoon.field.TyphoonSpawnRequest;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class TyphoonNbtTest {

	private static final double EPS = 1e-9;

	@Test
	void nbtRoundTripPreservesSnapshots() {
		TyphoonFields fields = new TyphoonFields(() -> "n1");
		fields.spawn(new TyphoonSpawnRequest(10, 20, 110, 40, 5, 8, 500, "海燕", 0.0, 1.0));
		fields.tick(125);

		CompoundTag tag = new CompoundTag();
		TyphoonNbt.writeSnapshots(tag, fields.snapshots());
		List<TyphoonSnapshot> loaded = TyphoonNbt.readSnapshots(tag);

		assertEquals(1, loaded.size());
		TyphoonSnapshot s = loaded.getFirst();
		assertEquals("n1", s.id());
		assertEquals("海燕", s.name());
		assertEquals(10.0, s.startX(), EPS);
		assertEquals(20.0, s.startZ(), EPS);
		assertEquals(110.0, s.endX(), EPS);
		assertEquals(40.0, s.endZ(), EPS);
		assertEquals(5, s.peakGrade());
		assertEquals(8 * 16.0, s.influenceHalfWidth(), EPS);
		assertEquals(500, s.durationTicks());
		assertEquals(0.0, s.windOverrideX(), EPS);
		assertEquals(1.0, s.windOverrideZ(), EPS);
		assertEquals(125, s.elapsedTicks());
	}
}
