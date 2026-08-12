package com.namimono.typhoon.field;

/**
 * 生成台风时的命令面参数；可选字段为 null 时使用规格缺省值。
 *
 * @param startX           起点 X（方块）
 * @param startZ           起点 Z
 * @param endX             终点 X
 * @param endZ             终点 Z
 * @param peakGrade        峰值等级；null → 6
 * @param widthChunks      影响半宽（区块）；null → 16
 * @param durationTicks    过境时长；null → 12000
 * @param name             显示名；null → Typhoon-&lt;短id&gt;
 * @param windOverrideX    主风向覆盖 X；与 Z 同为 null 则自动
 * @param windOverrideZ    主风向覆盖 Z
 */
public record TyphoonSpawnRequest(
		double startX,
		double startZ,
		double endX,
		double endZ,
		Integer peakGrade,
		Integer widthChunks,
		Integer durationTicks,
		String name,
		Double windOverrideX,
		Double windOverrideZ) {

	public static final int DEFAULT_PEAK_GRADE = 6;
	public static final int DEFAULT_WIDTH_CHUNKS = 16;
	public static final int DEFAULT_DURATION_TICKS = 12000;
	public static final int BLOCKS_PER_CHUNK = 16;

	public static TyphoonSpawnRequest of(double startX, double startZ, double endX, double endZ) {
		return new TyphoonSpawnRequest(startX, startZ, endX, endZ, null, null, null, null, null, null);
	}

	public TyphoonSpawnRequest withDurationTicks(int durationTicks) {
		return new TyphoonSpawnRequest(
				startX, startZ, endX, endZ, peakGrade, widthChunks, durationTicks, name, windOverrideX, windOverrideZ);
	}

	public TyphoonSpawnRequest withName(String name) {
		return new TyphoonSpawnRequest(
				startX, startZ, endX, endZ, peakGrade, widthChunks, durationTicks, name, windOverrideX, windOverrideZ);
	}

	public int resolvedPeakGrade() {
		return peakGrade != null ? peakGrade : DEFAULT_PEAK_GRADE;
	}

	public double resolvedInfluenceHalfWidthBlocks() {
		int chunks = widthChunks != null ? widthChunks : DEFAULT_WIDTH_CHUNKS;
		return chunks * (double) BLOCKS_PER_CHUNK;
	}

	public int resolvedDurationTicks() {
		return durationTicks != null ? durationTicks : DEFAULT_DURATION_TICKS;
	}
}
