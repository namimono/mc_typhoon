package com.namimono.typhoon.field;

/**
 * 玩家风力效果：由局部强度档派生，越高叠加越低档。风本身不直接扣血。
 */
public record PlayerWindEffect(
		int grade,
		double windX,
		double windZ,
		double horizontalPushPerTick,
		double verticalLiftPerTick,
		boolean sprintBlocked,
		boolean jumpBiased,
		boolean mechanicalHell) {

	public static final PlayerWindEffect NONE =
			new PlayerWindEffect(0, 0.0, 0.0, 0.0, 0.0, false, false, false);

	public boolean active() {
		return grade > 0;
	}

	/** 风本身不直接扣血；死亡率来自失控与环境。 */
	public boolean dealsDirectDamage() {
		return false;
	}

	/**
	 * 顺/逆风水平移速倍率。档 &lt; 2 为 1；档 2 约 ±20%；档 ≥ 3 约 ±40%。
	 * {@code moveDir} 与主风向点积为正为顺风。
	 */
	public double movementSpeedMultiplier(double moveDirX, double moveDirZ) {
		if (grade < 2) {
			return 1.0;
		}
		double mag = Math.hypot(moveDirX, moveDirZ);
		if (mag < 1e-9) {
			return 1.0;
		}
		double alignment = (moveDirX * windX + moveDirZ * windZ) / mag;
		double amplitude = grade >= 3 ? 0.40 : 0.20;
		return 1.0 + amplitude * alignment;
	}
}
