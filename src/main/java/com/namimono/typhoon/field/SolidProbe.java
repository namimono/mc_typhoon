package com.namimono.typhoon.field;

/**
 * 实心探测：迎风同列遮挡用。由适配层或测试替身注入。
 */
@FunctionalInterface
public interface SolidProbe {
	boolean isSolid(int x, int y, int z);
}
