package com.namimono.typhoon.field;

/**
 * 迎风面判定：主风向投到水平主轴，同列更迎风实心格挡住则为背风。顶面不算迎风。
 */
public final class WindwardFace {

	private WindwardFace() {
	}

	/**
	 * @param windX 主风向 X（吹向）
	 * @param windZ 主风向 Z（吹向）
	 * @return true 若迎风侧未被同列更迎风实心挡住
	 */
	public static boolean isWindward(int x, int y, int z, double windX, double windZ, SolidProbe solids) {
		int dx;
		int dz;
		if (Math.abs(windX) >= Math.abs(windZ)) {
			if (windX == 0.0 && windZ == 0.0) {
				return false;
			}
			dx = windX >= 0.0 ? 1 : -1;
			dz = 0;
		} else {
			dx = 0;
			dz = windZ >= 0.0 ? 1 : -1;
		}
		// 更迎风 = 风的来向（吹向的反方向）
		int upwindX = x - dx;
		int upwindZ = z - dz;
		return !solids.isSolid(upwindX, y, upwindZ);
	}
}
