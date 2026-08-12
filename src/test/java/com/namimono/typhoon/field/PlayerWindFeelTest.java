package com.namimono.typhoon.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlayerWindFeelTest {

	private static final double EPS = 1e-9;

	/** Path (0,0)→(100,0), L=100; peak 6; half-width 10; duration 100. Center at 50. */
	private static TyphoonField centeredField() {
		TyphoonField field = TyphoonField.create(0, 0, 100, 0, 6, 10, 100);
		field.tick(50);
		return field;
	}

	@Test
	void zeroIntensityOrOutsideBandYieldsNoPlayerWind() {
		TyphoonField field = centeredField();

		PlayerWindEffect eye = PlayerWindFeel.resolve(field.sample(50, 0), false);
		assertFalse(eye.active());
		assertEquals(0, eye.grade());

		PlayerWindEffect outside = PlayerWindFeel.resolve(field.sample(50, 20), false);
		assertFalse(outside.active());
		assertEquals(0, outside.grade());
	}

	@Test
	void windExemptModesDisablePlayerWindEvenInEyewall() {
		TyphoonField field = centeredField();
		TyphoonSample eyewall = field.sample(60, 0);
		assertEquals(6.0, eyewall.localIntensity(), EPS);

		assertFalse(PlayerWindFeel.resolve(eyewall, true).active());
		assertTrue(PlayerWindFeel.isWindExempt(true, false, false));
		assertTrue(PlayerWindFeel.isWindExempt(false, true, true));
		assertFalse(PlayerWindFeel.isWindExempt(false, true, false));
		assertFalse(PlayerWindFeel.isWindExempt(false, false, true));
	}

	@Test
	void gradeOneIsSlightDriftAlongPrimaryWind() {
		TyphoonField field = TyphoonField.create(0, 0, 100, 0, 1, 10, 100);
		field.tick(50);
		TyphoonSample wall = field.sample(60, 0);

		PlayerWindEffect effect = PlayerWindFeel.resolve(wall, false);
		assertTrue(effect.active());
		assertEquals(1, effect.grade());
		assertEquals(1.0, effect.windX(), EPS);
		assertEquals(0.0, effect.windZ(), EPS);
		assertTrue(effect.horizontalPushPerTick() > 0);
		assertEquals(0.0, effect.verticalLiftPerTick(), EPS);
		assertFalse(effect.sprintBlocked());
		assertFalse(effect.jumpBiased());
		assertFalse(effect.mechanicalHell());
		assertEquals(1.0, effect.movementSpeedMultiplier(1, 0), EPS);
	}

	@Test
	void gradesTwoAndThreeModulateSpeedWithAndAgainstWind() {
		TyphoonField peak2 = TyphoonField.create(0, 0, 100, 0, 2, 10, 100);
		peak2.tick(50);
		PlayerWindEffect g2 = PlayerWindFeel.resolve(peak2.sample(60, 0), false);
		assertEquals(2, g2.grade());
		assertEquals(1.20, g2.movementSpeedMultiplier(1, 0), EPS);
		assertEquals(0.80, g2.movementSpeedMultiplier(-1, 0), EPS);

		TyphoonField peak3 = TyphoonField.create(0, 0, 100, 0, 3, 10, 100);
		peak3.tick(50);
		PlayerWindEffect g3 = PlayerWindFeel.resolve(peak3.sample(60, 0), false);
		assertEquals(3, g3.grade());
		assertEquals(1.40, g3.movementSpeedMultiplier(1, 0), EPS);
		assertEquals(0.60, g3.movementSpeedMultiplier(-1, 0), EPS);
		assertTrue(g3.horizontalPushPerTick() > g2.horizontalPushPerTick());
	}

	@Test
	void gradeFourBlocksSprintAndKeepsLowerEffects() {
		TyphoonField field = TyphoonField.create(0, 0, 100, 0, 4, 10, 100);
		field.tick(50);
		PlayerWindEffect effect = PlayerWindFeel.resolve(field.sample(60, 0), false);

		assertEquals(4, effect.grade());
		assertTrue(effect.sprintBlocked());
		assertFalse(effect.jumpBiased());
		assertFalse(effect.mechanicalHell());
		assertEquals(1.40, effect.movementSpeedMultiplier(1, 0), EPS);
		assertTrue(effect.horizontalPushPerTick() > 0);
	}

	@Test
	void gradeFiveAddsJumpBias() {
		TyphoonField field = TyphoonField.create(0, 0, 100, 0, 5, 10, 100);
		field.tick(50);
		PlayerWindEffect effect = PlayerWindFeel.resolve(field.sample(60, 0), false);

		assertEquals(5, effect.grade());
		assertTrue(effect.sprintBlocked());
		assertTrue(effect.jumpBiased());
		assertFalse(effect.mechanicalHell());
		assertEquals(0.0, effect.verticalLiftPerTick(), EPS);
	}

	@Test
	void gradeSixIsMechanicalHellWithoutDirectDamage() {
		TyphoonField field = centeredField();
		PlayerWindEffect effect = PlayerWindFeel.resolve(field.sample(60, 0), false);

		assertEquals(6, effect.grade());
		assertTrue(effect.sprintBlocked());
		assertTrue(effect.jumpBiased());
		assertTrue(effect.mechanicalHell());
		assertTrue(effect.verticalLiftPerTick() > 0);
		assertTrue(effect.horizontalPushPerTick() > 0);
		assertFalse(effect.dealsDirectDamage());
	}

	@Test
	void pushDirectionFollowsPrimaryWindIncludingEyeReversal() {
		TyphoonField field = centeredField();

		PlayerWindEffect ahead = PlayerWindFeel.resolve(field.sample(60, 0), false);
		assertEquals(1.0, ahead.windX(), EPS);
		assertEquals(0.0, ahead.windZ(), EPS);

		PlayerWindEffect behind = PlayerWindFeel.resolve(field.sample(40, 0), false);
		assertEquals(-1.0, behind.windX(), EPS);
		assertEquals(0.0, behind.windZ(), EPS);
	}

	@Test
	void flooredLocalIntensitySelectsGrade() {
		// Slope |s|=30 → intensity 3.0 with peak 6; floor stays 3
		TyphoonField field = centeredField();
		assertEquals(3.0, field.sample(80, 0).localIntensity(), EPS);
		assertEquals(3, PlayerWindFeel.resolve(field.sample(80, 0), false).grade());

		// peak 1 field but slope mid → intensity 0.5 → floor 0 → no wind
		TyphoonField weak = TyphoonField.create(0, 0, 100, 0, 1, 10, 100);
		weak.tick(50);
		assertEquals(0.5, weak.sample(80, 0).localIntensity(), EPS);
		assertFalse(PlayerWindFeel.resolve(weak.sample(80, 0), false).active());
	}
}
