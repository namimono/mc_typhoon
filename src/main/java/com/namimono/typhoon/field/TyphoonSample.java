package com.namimono.typhoon.field;

/**
 * 台风场对某水平坐标的采样结果。
 *
 * @param inInfluenceBand 是否在影响带内（路径半宽内且处于剖面可达区段）
 * @param localIntensity  局部强度（风眼/带外/剖面外为 0）
 * @param windX           主风向单位向量 X（覆盖优先；否则过眼自动反转）
 * @param windZ           主风向单位向量 Z
 * @param pathProgress    路径进度 [0, 1]
 */
public record TyphoonSample(
		boolean inInfluenceBand,
		double localIntensity,
		double windX,
		double windZ,
		double pathProgress) {
}
