package com.namimono.typhoon.field;

/**
 * 台风场接缝：按路径与时长推进台风中心，并对任意水平坐标采样局部强度与主风向。
 * 纯逻辑，不依赖 Minecraft 运行时。
 */
public final class TyphoonField {

	private static final double EYE_END = 0.05;
	private static final double WALL_END = 0.15;
	private static final double SLOPE_END = 0.45;

	private final double startX;
	private final double startZ;
	private final double pathLength;
	private final double dirX;
	private final double dirZ;
	private final int peakGrade;
	private final double influenceHalfWidth;
	private final int durationTicks;
	private final Double overrideWindX;
	private final Double overrideWindZ;

	private int elapsedTicks;

	private TyphoonField(
			double startX,
			double startZ,
			double endX,
			double endZ,
			int peakGrade,
			double influenceHalfWidth,
			int durationTicks,
			Double overrideWindX,
			Double overrideWindZ) {
		if (durationTicks <= 0) {
			throw new IllegalArgumentException("durationTicks must be positive");
		}
		if (peakGrade < 0) {
			throw new IllegalArgumentException("peakGrade must be non-negative");
		}
		if (influenceHalfWidth < 0) {
			throw new IllegalArgumentException("influenceHalfWidth must be non-negative");
		}
		this.startX = startX;
		this.startZ = startZ;
		double dx = endX - startX;
		double dz = endZ - startZ;
		this.pathLength = Math.hypot(dx, dz);
		if (this.pathLength == 0.0) {
			throw new IllegalArgumentException("path length must be positive");
		}
		this.dirX = dx / this.pathLength;
		this.dirZ = dz / this.pathLength;
		this.peakGrade = peakGrade;
		this.influenceHalfWidth = influenceHalfWidth;
		this.durationTicks = durationTicks;
		this.overrideWindX = overrideWindX;
		this.overrideWindZ = overrideWindZ;
		this.elapsedTicks = 0;
	}

	/** 自动主风向（路径水平方向，过眼反转）。 */
	public static TyphoonField create(
			double startX,
			double startZ,
			double endX,
			double endZ,
			int peakGrade,
			double influenceHalfWidth,
			int durationTicks) {
		return create(startX, startZ, endX, endZ, peakGrade, influenceHalfWidth, durationTicks, null, null);
	}

	/**
	 * @param windOverrideX 手动覆盖主风向 X；与 Z 同时为 null 则自动（含过眼反转）
	 * @param windOverrideZ 手动覆盖主风向 Z
	 */
	public static TyphoonField create(
			double startX,
			double startZ,
			double endX,
			double endZ,
			int peakGrade,
			double influenceHalfWidth,
			int durationTicks,
			Double windOverrideX,
			Double windOverrideZ) {
		if ((windOverrideX == null) != (windOverrideZ == null)) {
			throw new IllegalArgumentException("wind override components must both be null or both set");
		}
		return new TyphoonField(
				startX, startZ, endX, endZ, peakGrade, influenceHalfWidth, durationTicks,
				windOverrideX, windOverrideZ);
	}

	public void tick(int deltaTicks) {
		if (deltaTicks < 0) {
			throw new IllegalArgumentException("deltaTicks must be non-negative");
		}
		long next = (long) elapsedTicks + deltaTicks;
		elapsedTicks = (int) Math.min(next, durationTicks);
	}

	/** 存档加载后恢复已推进时长（夹在 [0, durationTicks]）。 */
	public void restoreElapsedTicks(int elapsedTicks) {
		if (elapsedTicks < 0) {
			throw new IllegalArgumentException("elapsedTicks must be non-negative");
		}
		this.elapsedTicks = Math.min(elapsedTicks, durationTicks);
	}

	public double pathProgress() {
		return (double) elapsedTicks / (double) durationTicks;
	}

	public boolean finished() {
		return elapsedTicks >= durationTicks;
	}

	public int peakGrade() {
		return peakGrade;
	}

	public double influenceHalfWidth() {
		return influenceHalfWidth;
	}

	public int durationTicks() {
		return durationTicks;
	}

	public int elapsedTicks() {
		return elapsedTicks;
	}

	public Double windOverrideX() {
		return overrideWindX;
	}

	public Double windOverrideZ() {
		return overrideWindZ;
	}

	public double startX() {
		return startX;
	}

	public double startZ() {
		return startZ;
	}

	public TyphoonSample sample(double x, double z) {
		double progress = pathProgress();
		double centerAlong = progress * pathLength;

		double fromStartX = x - startX;
		double fromStartZ = z - startZ;
		double along = fromStartX * dirX + fromStartZ * dirZ;
		double cross = fromStartX * dirZ - fromStartZ * dirX;
		double distanceFromPath = Math.abs(cross);
		double s = along - centerAlong;

		boolean withinWidth = distanceFromPath <= influenceHalfWidth;
		double intensity = withinWidth ? intensityAt(s) : 0.0;
		boolean inInfluenceBand = withinWidth && Math.abs(s) <= SLOPE_END * pathLength;

		double windX;
		double windZ;
		if (overrideWindX != null) {
			windX = overrideWindX;
			windZ = overrideWindZ;
		} else if (s < 0.0) {
			windX = -dirX;
			windZ = -dirZ;
		} else {
			windX = dirX;
			windZ = dirZ;
		}

		return new TyphoonSample(inInfluenceBand, intensity, windX, windZ, progress);
	}

	private double intensityAt(double s) {
		double abs = Math.abs(s);
		double eyeEnd = EYE_END * pathLength;
		double wallEnd = WALL_END * pathLength;
		double slopeEnd = SLOPE_END * pathLength;
		if (abs <= eyeEnd) {
			return 0.0;
		}
		if (abs <= wallEnd) {
			return peakGrade;
		}
		if (abs <= slopeEnd) {
			return peakGrade * (slopeEnd - abs) / (slopeEnd - wallEnd);
		}
		return 0.0;
	}
}
