package com.namimono.typhoon.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BlownDropMotionTest {

	private static final double EPS = 1e-6;

	/** Path (0,0)→(100,0), L=100; peak 6; half-width 10; duration 100. Center at 50. */
	private static TyphoonField centeredField() {
		TyphoonField field = TyphoonField.create(0, 0, 100, 0, 6, 10, 100);
		field.tick(50);
		return field;
	}

	@Test
	void eyeAndOutsideBandYieldNoBlowForce() {
		TyphoonField field = centeredField();

		assertFalse(BlownDropMotion.resolve(field.sample(50, 0)).active());
		assertFalse(BlownDropMotion.resolve(field.sample(50, 20)).active());
	}

	@Test
	void terminalHorizontalSpeedScalesWithLocalIntensityAnchors() {
		assertEquals(3.0, BlownDropMotion.terminalSpeedBlocksPerSecond(1.0), EPS);
		assertEquals(8.0, BlownDropMotion.terminalSpeedBlocksPerSecond(3.0), EPS);
		assertEquals(14.0, BlownDropMotion.terminalSpeedBlocksPerSecond(6.0), EPS);
	}

	@Test
	void liftIncreasesWithLocalIntensityButStaysMild() {
		double lift1 = BlownDropMotion.liftSpeedBlocksPerSecond(1.0);
		double lift3 = BlownDropMotion.liftSpeedBlocksPerSecond(3.0);
		double lift6 = BlownDropMotion.liftSpeedBlocksPerSecond(6.0);
		assertTrue(lift1 > 0 && lift1 < 1.0);
		assertTrue(lift3 > lift1);
		assertTrue(lift6 > lift3);
		assertTrue(lift6 < 5.0);
	}

	@Test
	void resolveFollowsPrimaryWindIncludingEyeReversal() {
		TyphoonField field = centeredField();

		BlownDropMotionEffect ahead = BlownDropMotion.resolve(field.sample(60, 0));
		assertTrue(ahead.active());
		assertEquals(1.0, ahead.windX(), EPS);
		assertEquals(0.0, ahead.windZ(), EPS);
		assertEquals(14.0, ahead.targetHorizontalSpeed(), EPS);

		BlownDropMotionEffect behind = BlownDropMotion.resolve(field.sample(40, 0));
		assertTrue(behind.active());
		assertEquals(-1.0, behind.windX(), EPS);
		assertEquals(0.0, behind.windZ(), EPS);
	}

	@Test
	void stepTowardTerminalApproachesWithinAboutOneSecond() {
		BlownDropMotionEffect effect = new BlownDropMotionEffect(true, 1.0, 0.0, 8.0, 1.5);
		double vx = 0.0;
		double vz = 0.0;
		// 20 ticks = 1s; should be near terminal (within 5%)
		for (int i = 0; i < 20; i++) {
			double[] next = BlownDropMotion.stepTowardTerminal(effect, vx, vz);
			vx = next[0];
			vz = next[1];
		}
		assertEquals(8.0, vx, 0.4);
		assertEquals(0.0, vz, EPS);
		assertTrue(BlownDropMotion.liftImpulseBlocksPerSecond(effect) > 0);
	}

	@Test
	void forceBudgetAndPlayerWindowMatchSpecMagnitude() {
		assertTrue(BlownDropMotion.FORCE_BUDGET >= 32 && BlownDropMotion.FORCE_BUDGET <= 64);
		assertEquals(32, BlownDropMotion.PLAYER_HORIZONTAL_RADIUS);
		assertTrue(BlownDropMotion.ACCEL_TICKS >= 10 && BlownDropMotion.ACCEL_TICKS <= 20);
	}
}
