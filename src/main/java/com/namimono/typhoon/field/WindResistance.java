package com.namimono.typhoon.field;

import java.util.OptionalInt;

/**
 * 抗风等级解析：跳过/免疫 → 例外 tag → hardness 分箱。纯逻辑接缝。
 */
public final class WindResistance {

	private WindResistance() {
	}

	/**
	 * @return 抗风等级 1–6；空表示不参与或免疫（风力破坏跳过）
	 */
	public static OptionalInt resolve(WindBlockFacts facts) {
		if (facts.nonParticipating()) {
			return OptionalInt.empty();
		}
		if (facts.immune()) {
			return OptionalInt.empty();
		}
		if (facts.hardness() < 0.0f) {
			return OptionalInt.empty();
		}
		Integer exception = facts.exceptionGrade();
		if (exception != null) {
			return OptionalInt.of(clampGrade(exception));
		}
		return OptionalInt.of(binHardness(facts.hardness()));
	}

	private static int clampGrade(int grade) {
		if (grade < 1) {
			return 1;
		}
		if (grade > 6) {
			return 6;
		}
		return grade;
	}

	/** ADR / 决议 05：未命中例外时的 hardness 分箱。 */
	static int binHardness(float h) {
		if (h < 0.4f) {
			return 1;
		}
		if (h < 1.0f) {
			return 2;
		}
		if (h < 2.0f) {
			return 3;
		}
		if (h < 3.5f) {
			return 4;
		}
		if (h < 20.0f) {
			return 5;
		}
		return 6;
	}
}
