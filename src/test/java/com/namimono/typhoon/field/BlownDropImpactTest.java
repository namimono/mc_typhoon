package com.namimono.typhoon.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BlownDropImpactTest {

	private static final double EPS = 1e-6;

	@Test
	void horizontalSpeedBelowFiveIsNotWindDriven() {
		assertFalse(BlownDropImpact.isWindDriven(4.9, 0.0));
		assertFalse(BlownDropImpact.isWindDriven(3.0, 3.0)); // hypot ~4.24
		assertTrue(BlownDropImpact.isWindDriven(5.0, 0.0));
		assertTrue(BlownDropImpact.isWindDriven(4.0, 3.0)); // hypot = 5
		assertTrue(BlownDropImpact.isWindDriven(14.0, 0.0));
	}

	@Test
	void damageScalesWithLocalIntensityClampedMultiplier() {
		// 底伤 2 × clamp(intensity/3, 0.5, 2)
		assertEquals(1.0, BlownDropImpact.damageAmount(1.0), EPS); // 2 * 0.5
		assertEquals(2.0, BlownDropImpact.damageAmount(3.0), EPS); // 2 * 1.0
		assertEquals(4.0, BlownDropImpact.damageAmount(6.0), EPS); // 2 * 2.0
		assertEquals(4.0, BlownDropImpact.damageAmount(9.0), EPS); // still clamp 2
		assertEquals(1.0, BlownDropImpact.damageAmount(0.1), EPS); // floor mult 0.5
	}

	@Test
	void sameDropToSameEntityHasTwentyTickCooldown() {
		BlownDropImpactCooldown cooldown = new BlownDropImpactCooldown();
		assertTrue(cooldown.tryHit(10L, 1, 100L));
		assertFalse(cooldown.tryHit(10L, 1, 119L));
		assertTrue(cooldown.tryHit(10L, 1, 120L));
		assertTrue(cooldown.tryHit(10L, 2, 121L)); // other entity ok
		assertTrue(cooldown.tryHit(11L, 1, 121L)); // other drop ok
	}

	@Test
	void creativeFlyingOrSpectatorAreImpactExempt() {
		assertTrue(BlownDropImpact.isImpactExempt(true, false, false));
		assertTrue(BlownDropImpact.isImpactExempt(false, true, true));
		assertFalse(BlownDropImpact.isImpactExempt(false, true, false));
		assertFalse(BlownDropImpact.isImpactExempt(false, false, false));
	}

	@Test
	void shouldDealDamageRequiresWindDrivenAndNonExempt() {
		assertFalse(BlownDropImpact.shouldDealDamage(4.0, 0.0, 3.0, false));
		assertFalse(BlownDropImpact.shouldDealDamage(8.0, 0.0, 3.0, true));
		assertTrue(BlownDropImpact.shouldDealDamage(8.0, 0.0, 3.0, false));
		assertFalse(BlownDropImpact.shouldDealDamage(8.0, 0.0, 0.0, false));
	}
}
