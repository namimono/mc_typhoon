package com.namimono.typhoon.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TyphoonFieldTest {

	private static final double EPS = 1e-9;

	/** Path (0,0)→(100,0), L=100; peak 6; half-width 10; duration 100 ticks. */
	private static TyphoonField field() {
		return TyphoonField.create(0, 0, 100, 0, 6, 10, 100);
	}

	@Test
	void pathProgressAdvancesWithTicksAndReportsCenterMotion() {
		TyphoonField typhoon = field();
		assertEquals(0.0, typhoon.pathProgress(), EPS);

		typhoon.tick(25);
		assertEquals(0.25, typhoon.pathProgress(), EPS);

		TyphoonSample atStart = typhoon.sample(0, 0);
		assertEquals(0.25, atStart.pathProgress(), EPS);
		// Center is at x=25; point at start is behind center (|s|=25 → slope).
		assertTrue(atStart.localIntensity() > 0);

		typhoon.tick(75);
		assertEquals(1.0, typhoon.pathProgress(), EPS);

		typhoon.tick(10);
		assertEquals(1.0, typhoon.pathProgress(), EPS);
	}

	@Test
	void intensityProfileIsSymmetricAroundCenter() {
		TyphoonField typhoon = field();
		typhoon.tick(50); // center at x=50

		// Eye: |s| <= 0.05L = 5 → 0
		assertEquals(0.0, typhoon.sample(50, 0).localIntensity(), EPS);
		assertEquals(0.0, typhoon.sample(54, 0).localIntensity(), EPS);
		assertEquals(0.0, typhoon.sample(46, 0).localIntensity(), EPS);

		// Eyewall: 0.05L < |s| <= 0.15L → peak 6
		assertEquals(6.0, typhoon.sample(60, 0).localIntensity(), EPS);
		assertEquals(6.0, typhoon.sample(40, 0).localIntensity(), EPS);
		assertEquals(6.0, typhoon.sample(55.1, 0).localIntensity(), EPS);

		// Slope: |s|=30 → 6 * (45-30)/(45-15) = 3
		assertEquals(3.0, typhoon.sample(80, 0).localIntensity(), EPS);
		assertEquals(3.0, typhoon.sample(20, 0).localIntensity(), EPS);

		// Beyond 0.45L → 0
		assertEquals(0.0, typhoon.sample(96, 0).localIntensity(), EPS);
		assertEquals(0.0, typhoon.sample(4, 0).localIntensity(), EPS);
	}

	@Test
	void outsideInfluenceWidthIsUnaffectedWithZeroIntensity() {
		TyphoonField typhoon = field();
		typhoon.tick(50);

		TyphoonSample outside = typhoon.sample(50, 10.1);
		assertFalse(outside.inInfluenceBand());
		assertEquals(0.0, outside.localIntensity(), EPS);

		TyphoonSample onEdge = typhoon.sample(60, 10);
		assertTrue(onEdge.inInfluenceBand());
		assertEquals(6.0, onEdge.localIntensity(), EPS);
	}

	@Test
	void primaryWindReversesAfterPassingTheEye() {
		TyphoonField typhoon = field();
		typhoon.tick(50);

		TyphoonSample ahead = typhoon.sample(60, 0); // s > 0, front
		assertEquals(1.0, ahead.windX(), EPS);
		assertEquals(0.0, ahead.windZ(), EPS);

		TyphoonSample behind = typhoon.sample(40, 0); // s < 0, past eye
		assertEquals(-1.0, behind.windX(), EPS);
		assertEquals(0.0, behind.windZ(), EPS);
	}

	@Test
	void manualWindOverrideIsFixedForWholeField() {
		TyphoonField typhoon = TyphoonField.create(0, 0, 100, 0, 6, 10, 100, 0.0, 1.0);
		typhoon.tick(50);

		TyphoonSample ahead = typhoon.sample(60, 0);
		assertEquals(0.0, ahead.windX(), EPS);
		assertEquals(1.0, ahead.windZ(), EPS);

		TyphoonSample behind = typhoon.sample(40, 0);
		assertEquals(0.0, behind.windX(), EPS);
		assertEquals(1.0, behind.windZ(), EPS);
	}

	@Test
	void farAlongPathButWithinWidthHasZeroIntensityAndLeavesBand() {
		TyphoonField typhoon = field();
		typhoon.tick(50);
		// |s|=50 > 0.45L, still on path line
		TyphoonSample far = typhoon.sample(100, 0);
		assertFalse(far.inInfluenceBand());
		assertEquals(0.0, far.localIntensity(), EPS);
	}

	@Test
	void diagonalPathSamplesRelativeToSignedDistanceAlongPath() {
		// Path (0,0)→(0,100), L=100; center at progress 0.5 → (0,50)
		TyphoonField typhoon = TyphoonField.create(0, 0, 0, 100, 6, 10, 100);
		typhoon.tick(50);

		assertEquals(0.0, typhoon.sample(0, 50).localIntensity(), EPS);
		assertEquals(6.0, typhoon.sample(0, 60).localIntensity(), EPS);
		assertEquals(6.0, typhoon.sample(0, 40).localIntensity(), EPS);

		// Front (s>0): wind = path dir (0,1); behind: reverse (0,-1)
		assertEquals(0.0, typhoon.sample(0, 60).windX(), EPS);
		assertEquals(1.0, typhoon.sample(0, 60).windZ(), EPS);
		assertEquals(0.0, typhoon.sample(0, 40).windX(), EPS);
		assertEquals(-1.0, typhoon.sample(0, 40).windZ(), EPS);

		assertFalse(typhoon.sample(10.1, 50).inInfluenceBand());
	}
}
