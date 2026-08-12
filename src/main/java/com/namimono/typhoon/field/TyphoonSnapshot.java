package com.namimono.typhoon.field;

/**
 * 可持久化的台风快照（SavedData 适配层读写此形状）。
 */
public record TyphoonSnapshot(
		String id,
		String name,
		double startX,
		double startZ,
		double endX,
		double endZ,
		int peakGrade,
		double influenceHalfWidth,
		int durationTicks,
		Double windOverrideX,
		Double windOverrideZ,
		int elapsedTicks) {
}
