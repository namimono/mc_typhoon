package com.namimono.typhoon.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TyphoonRainFeelTest {

	private static final double EPS = 1e-9;

	/** Path (0,0)→(100,0), L=100; peak 6; half-width 10; duration 100. Center at 50. */
	private static TyphoonField centeredField() {
		TyphoonField field = TyphoonField.create(0, 0, 100, 0, 6, 10, 100);
		field.tick(50);
		return field;
	}

	@Test
	void eyeAndOutsideBandStopTyphoonRain() {
		TyphoonField field = centeredField();

		TyphoonRainEffect eye = TyphoonRainFeel.resolve(field.sample(50, 0));
		assertFalse(eye.raining());
		assertEquals(0.0, eye.rainStrength(), EPS);

		TyphoonRainEffect outside = TyphoonRainFeel.resolve(field.sample(50, 20));
		assertFalse(outside.raining());
		assertEquals(0.0, outside.rainStrength(), EPS);
	}

	@Test
	void bandWithPositiveIntensityRainsScaledByLocalIntensity() {
		TyphoonField field = centeredField();
		TyphoonSample wall = field.sample(60, 0);
		assertEquals(6.0, wall.localIntensity(), EPS);

		TyphoonRainEffect rain = TyphoonRainFeel.resolve(wall);
		assertTrue(rain.raining());
		assertEquals(1.0, rain.rainStrength(), EPS);

		TyphoonField peak3 = TyphoonField.create(0, 0, 100, 0, 3, 10, 100);
		peak3.tick(50);
		TyphoonRainEffect mid = TyphoonRainFeel.resolve(peak3.sample(60, 0));
		assertTrue(mid.raining());
		assertEquals(0.5, mid.rainStrength(), EPS);
	}

	@Test
	void windVectorFollowsPrimaryDirectionIncludingEyeCrossingReversal() {
		TyphoonField field = centeredField();

		TyphoonRainEffect ahead = TyphoonRainFeel.resolve(field.sample(60, 0));
		assertTrue(ahead.raining());
		assertEquals(1.0, ahead.windX(), EPS);
		assertEquals(0.0, ahead.windZ(), EPS);

		TyphoonRainEffect behind = TyphoonRainFeel.resolve(field.sample(40, 0));
		assertTrue(behind.raining());
		assertEquals(-1.0, behind.windX(), EPS);
		assertEquals(0.0, behind.windZ(), EPS);
	}

	@Test
	void manualWindOverrideKeepsFixedDirectionForRainParticles() {
		TyphoonField field = TyphoonField.create(0, 0, 100, 0, 6, 10, 100, 0.0, 1.0);
		field.tick(50);

		TyphoonRainEffect ahead = TyphoonRainFeel.resolve(field.sample(60, 0));
		assertEquals(0.0, ahead.windX(), EPS);
		assertEquals(1.0, ahead.windZ(), EPS);

		TyphoonRainEffect behind = TyphoonRainFeel.resolve(field.sample(40, 0));
		assertEquals(0.0, behind.windX(), EPS);
		assertEquals(1.0, behind.windZ(), EPS);
	}
}
