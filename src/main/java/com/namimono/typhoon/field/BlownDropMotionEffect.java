package com.namimono.typhoon.field;

/**
 * 掉落物被风吹走的目标速度效果。
 *
 * @param active                  是否在影响带内且局部强度 &gt; 0
 * @param windX                   主风向单位向量 X
 * @param windZ                   主风向单位向量 Z
 * @param targetHorizontalSpeed   水平终端速度（格/秒）
 * @param targetLiftSpeed         竖直抬升目标速度（格/秒）
 */
public record BlownDropMotionEffect(
		boolean active,
		double windX,
		double windZ,
		double targetHorizontalSpeed,
		double targetLiftSpeed) {

	public static final BlownDropMotionEffect NONE =
			new BlownDropMotionEffect(false, 0.0, 0.0, 0.0, 0.0);
}
