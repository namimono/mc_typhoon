package com.namimono.typhoon.field;

/**
 * 方块抗风解析输入：由适配层从 tag / hardness / 流体等探测后注入。
 *
 * @param nonParticipating 流体、空气、可替换等 → 不参与风力破坏
 * @param immune           基岩、黑曜石族等 → 免疫
 * @param exceptionGrade   例外 tag 档 1–6；{@code null} 表示未命中例外
 * @param hardness         {@code BlockState.getDestroySpeed}；{@code < 0} 视为免疫
 */
public record WindBlockFacts(
		boolean nonParticipating,
		boolean immune,
		Integer exceptionGrade,
		float hardness) {

	public static WindBlockFacts skip() {
		return new WindBlockFacts(true, false, null, 0.0f);
	}

	public static WindBlockFacts immuneBlock() {
		return new WindBlockFacts(false, true, null, -1.0f);
	}

	public static WindBlockFacts immuneWithHardness(float hardness) {
		return new WindBlockFacts(false, true, null, hardness);
	}

	public static WindBlockFacts exception(int grade) {
		return new WindBlockFacts(false, false, grade, 0.0f);
	}

	public static WindBlockFacts exceptionWithHardness(int grade, float hardness) {
		return new WindBlockFacts(false, false, grade, hardness);
	}

	public static WindBlockFacts hardness(float hardness) {
		return new WindBlockFacts(false, false, null, hardness);
	}
}
