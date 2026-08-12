package com.namimono.typhoon.field;

/**
 * 玩家风力档判定：从台风采样与资格派生效果。纯逻辑接缝。
 */
public final class PlayerWindFeel {

	/** 档 1–6 水平推力（格/tick），越高越强。 */
	private static final double[] HORIZONTAL_PUSH = {
		0.0, 0.008, 0.015, 0.025, 0.04, 0.06, 0.18
	};

	/** 仅档 6：竖直抬升（格/tick）。 */
	private static final double HELL_LIFT = 0.12;

	private PlayerWindFeel() {
	}

	/**
	 * @param windExempt 创造且可飞行，或旁观 → 关闭玩家风力
	 */
	public static PlayerWindEffect resolve(TyphoonSample sample, boolean windExempt) {
		if (windExempt) {
			return PlayerWindEffect.NONE;
		}
		int grade = (int) Math.floor(sample.localIntensity());
		if (grade < 1) {
			return PlayerWindEffect.NONE;
		}
		if (grade > 6) {
			grade = 6;
		}
		double push = HORIZONTAL_PUSH[grade];
		double lift = grade >= 6 ? HELL_LIFT : 0.0;
		return new PlayerWindEffect(
				grade,
				sample.windX(),
				sample.windZ(),
				push,
				lift,
				grade >= 4,
				grade >= 5,
				grade >= 6);
	}

	/** 创造且可飞行，或旁观。 */
	public static boolean isWindExempt(boolean spectator, boolean creative, boolean mayfly) {
		return spectator || (creative && mayfly);
	}
}
