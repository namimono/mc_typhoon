package com.namimono.typhoon.field;

/**
 * 掉落物撞击伤害：速度门槛、底伤×强度倍率、免疫。纯逻辑接缝。
 * <p>
 * 水平速率 ≥ 5 格/秒才致伤；伤害 = 2 × clamp(局部强度/3, 0.5, 2)；
 * 同实体对同生物冷却见 {@link BlownDropImpactCooldown}。
 */
public final class BlownDropImpact {

	/** 「被风驱动中」水平速率门槛（格/秒）。 */
	public static final double MIN_HORIZONTAL_SPEED = 5.0;
	public static final float BASE_DAMAGE = 2.0f;
	public static final int COOLDOWN_TICKS = 20;

	private static final double MULT_MIN = 0.5;
	private static final double MULT_MAX = 2.0;

	private BlownDropImpact() {
	}

	public static boolean isWindDriven(double velocityX, double velocityZ) {
		return Math.hypot(velocityX, velocityZ) >= MIN_HORIZONTAL_SPEED;
	}

	/**
	 * 撞击伤害量。忽略物品种类与堆叠；强度倍率夹在约 0.5～2。
	 */
	public static float damageAmount(double localIntensity) {
		double mult = localIntensity / 3.0;
		if (mult < MULT_MIN) {
			mult = MULT_MIN;
		} else if (mult > MULT_MAX) {
			mult = MULT_MAX;
		}
		return (float) (BASE_DAMAGE * mult);
	}

	/** 创造且可飞行，或旁观 → 免疫撞击伤害。 */
	public static boolean isImpactExempt(boolean spectator, boolean creative, boolean mayfly) {
		return PlayerWindFeel.isWindExempt(spectator, creative, mayfly);
	}

	public static boolean shouldDealDamage(
			double velocityX, double velocityZ, double localIntensity, boolean impactExempt) {
		if (impactExempt || localIntensity <= 0.0) {
			return false;
		}
		return isWindDriven(velocityX, velocityZ);
	}
}
