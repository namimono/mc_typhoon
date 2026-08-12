package com.namimono.typhoon.field;

/**
 * 台风雨表现量：是否下雨、雨强度（0..1）、主风向单位向量。
 * 客户端粒子与音效按此驱动；不触及原版全局天气。
 */
public record TyphoonRainEffect(
		boolean raining,
		double rainStrength,
		double windX,
		double windZ) {

	public static final TyphoonRainEffect NONE = new TyphoonRainEffect(false, 0.0, 0.0, 0.0);
}
