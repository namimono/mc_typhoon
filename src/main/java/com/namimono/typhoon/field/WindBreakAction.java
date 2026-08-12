package com.namimono.typhoon.field;

/**
 * 适配层应执行的裂纹/破坏动作。
 *
 * @param breakerId 负 id 池槽位
 * @param stage     0–9 裂纹；&lt;0 清除
 * @param destroy   true 时调用 destroyBlock 掉落，并清进度
 */
public record WindBreakAction(
		int x,
		int y,
		int z,
		int breakerId,
		int stage,
		boolean destroy) {
}
