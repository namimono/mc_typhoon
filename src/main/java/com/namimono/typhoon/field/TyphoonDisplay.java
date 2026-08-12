package com.namimono.typhoon.field;

/**
 * Boss 栏等展示文案（纯逻辑，无 Minecraft 依赖）。
 */
public final class TyphoonDisplay {

	private TyphoonDisplay() {
	}

	/** 标题：台风「名称」 · 局部强度 n/峰值 */
	public static String bossTitle(String name, double localIntensity, int peakGrade) {
		int local = (int) Math.floor(localIntensity);
		return "台风「" + name + "」 · 局部强度 " + local + "/" + peakGrade;
	}
}
