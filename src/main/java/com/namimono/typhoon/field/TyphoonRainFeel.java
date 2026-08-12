package com.namimono.typhoon.field;

/**
 * 台风雨判定：由局部强度派生雨表现与主风向。纯逻辑接缝。
 * <p>
 * 局部强度 &gt; 0 → 下雨；风眼/带外强度 0 → 停雨。
 * 雨强度 = 局部强度 / 6（峰值刻度上限），夹在 [0, 1]。
 */
public final class TyphoonRainFeel {

	private static final double MAX_GRADE = 6.0;

	private TyphoonRainFeel() {
	}

	public static TyphoonRainEffect resolve(TyphoonSample sample) {
		double intensity = sample.localIntensity();
		if (intensity <= 0.0) {
			return TyphoonRainEffect.NONE;
		}
		double strength = Math.min(1.0, intensity / MAX_GRADE);
		return new TyphoonRainEffect(true, strength, sample.windX(), sample.windZ());
	}
}
