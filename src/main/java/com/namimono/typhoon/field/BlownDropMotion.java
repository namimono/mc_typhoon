package com.namimono.typhoon.field;

/**
 * 掉落物风力运动：终端速度、轻度抬升与加速。纯逻辑接缝。
 * <p>
 * 水平终端：局部强度 1→3、3→8、6→14 格/秒（分段线性插值）。
 * 约 0.5–1 秒加速到位；离开影响带后不再施力。
 */
public final class BlownDropMotion {

	/** 约 0.75 秒（15 tick）接近终端速度。 */
	public static final int ACCEL_TICKS = 15;
	/** 每 tick 施力数量预算（规格 32–64 中值）。 */
	public static final int FORCE_BUDGET = 48;
	/** 玩家附近水平窗口（与破坏扫描同量级）。 */
	public static final int PLAYER_HORIZONTAL_RADIUS = 32;
	/** 玩家附近垂直窗口。 */
	public static final int PLAYER_VERTICAL_RADIUS = 16;

	private static final double TICKS_PER_SECOND = 20.0;
	/** 指数逼近：约 ACCEL_TICKS 内到达终端的 95%。 */
	private static final double BLEND_PER_TICK = 1.0 - Math.pow(0.05, 1.0 / ACCEL_TICKS);

	private BlownDropMotion() {
	}

	public static BlownDropMotionEffect resolve(TyphoonSample sample) {
		double intensity = sample.localIntensity();
		if (intensity <= 0.0) {
			return BlownDropMotionEffect.NONE;
		}
		return new BlownDropMotionEffect(
				true,
				sample.windX(),
				sample.windZ(),
				terminalSpeedBlocksPerSecond(intensity),
				liftSpeedBlocksPerSecond(intensity));
	}

	/**
	 * 水平终端速度（格/秒）。锚点：1→3、3→8、6→14；中间线性插值；&lt;1 按比例缩。
	 */
	public static double terminalSpeedBlocksPerSecond(double localIntensity) {
		if (localIntensity <= 0.0) {
			return 0.0;
		}
		if (localIntensity <= 1.0) {
			return 3.0 * localIntensity;
		}
		if (localIntensity <= 3.0) {
			return lerp(3.0, 8.0, (localIntensity - 1.0) / 2.0);
		}
		if (localIntensity <= 6.0) {
			return lerp(8.0, 14.0, (localIntensity - 3.0) / 3.0);
		}
		return 14.0;
	}

	/**
	 * 竖直抬升目标速度（格/秒）：强度 1 很弱，3 明显，6 可腾空掠过。
	 * 适配层每 tick 叠加该冲量（÷20），重力仍在。
	 */
	public static double liftSpeedBlocksPerSecond(double localIntensity) {
		if (localIntensity <= 0.0) {
			return 0.0;
		}
		if (localIntensity <= 1.0) {
			return 0.5 * localIntensity;
		}
		if (localIntensity <= 3.0) {
			return lerp(0.5, 1.2, (localIntensity - 1.0) / 2.0);
		}
		if (localIntensity <= 6.0) {
			return lerp(1.2, 2.0, (localIntensity - 3.0) / 3.0);
		}
		return 2.0;
	}

	/**
	 * 一 tick 水平向终端速度逼近（格/秒）。返回 [vx, vz]。
	 * 竖直抬升用 {@link #liftImpulseBlocksPerSecond}，交给适配层叠加，保留重力。
	 */
	public static double[] stepTowardTerminal(BlownDropMotionEffect effect, double vx, double vz) {
		if (!effect.active()) {
			return new double[] {vx, vz};
		}
		double targetVx = effect.windX() * effect.targetHorizontalSpeed();
		double targetVz = effect.windZ() * effect.targetHorizontalSpeed();
		return new double[] {
			vx + (targetVx - vx) * BLEND_PER_TICK,
			vz + (targetVz - vz) * BLEND_PER_TICK
		};
	}

	/** 本 tick 应叠加的竖直抬升冲量（格/秒）；非永久悬浮，重力仍在。 */
	public static double liftImpulseBlocksPerSecond(BlownDropMotionEffect effect) {
		if (!effect.active()) {
			return 0.0;
		}
		return effect.targetLiftSpeed();
	}

	/** 格/秒 → 格/tick（Minecraft 运动单位）。 */
	public static double blocksPerSecondToPerTick(double blocksPerSecond) {
		return blocksPerSecond / TICKS_PER_SECOND;
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}
}
