package com.namimono.typhoon.field;

/**
 * 风力破坏对世界的探测接缝（适配层或测试替身）。
 */
public interface WindBreakWorld extends SolidProbe {
	WindBlockFacts factsAt(int x, int y, int z);

	boolean isChunkLoaded(int blockX, int blockZ);
}
