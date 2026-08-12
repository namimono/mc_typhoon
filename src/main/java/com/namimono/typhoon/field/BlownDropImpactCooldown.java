package com.namimono.typhoon.field;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 掉落物→生物撞击冷却：同一掉落物实体对同一生物 20 tick。
 */
public final class BlownDropImpactCooldown {

	private final Map<Long, Long> lastHitTickByPair = new HashMap<>();

	/**
	 * @param dropEntityId 掉落物实体 id
	 * @param livingEntityId 目标生物实体 id
	 * @param gameTick 当前世界 tick（单调）
	 * @return 若允许本次命中则记录并返回 true
	 */
	public boolean tryHit(long dropEntityId, int livingEntityId, long gameTick) {
		long key = pairKey(dropEntityId, livingEntityId);
		Long last = lastHitTickByPair.get(key);
		if (last != null && gameTick - last < BlownDropImpact.COOLDOWN_TICKS) {
			return false;
		}
		lastHitTickByPair.put(key, gameTick);
		prune(gameTick);
		return true;
	}

	private void prune(long gameTick) {
		if (lastHitTickByPair.size() < 256) {
			return;
		}
		Iterator<Map.Entry<Long, Long>> it = lastHitTickByPair.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Long, Long> e = it.next();
			if (gameTick - e.getValue() >= BlownDropImpact.COOLDOWN_TICKS * 4L) {
				it.remove();
			}
		}
	}

	private static long pairKey(long dropEntityId, int livingEntityId) {
		return (dropEntityId << 32) ^ (livingEntityId & 0xffffffffL);
	}
}
