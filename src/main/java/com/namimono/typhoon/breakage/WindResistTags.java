package com.namimono.typhoon.breakage;

import com.namimono.typhoon.Typhoon;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** 抗风例外 / 免疫方块标签。 */
public final class WindResistTags {

	public static final TagKey<Block> RESIST_1 = tag("wind_resist_1");
	public static final TagKey<Block> RESIST_2 = tag("wind_resist_2");
	public static final TagKey<Block> RESIST_3 = tag("wind_resist_3");
	public static final TagKey<Block> RESIST_4 = tag("wind_resist_4");
	public static final TagKey<Block> RESIST_5 = tag("wind_resist_5");
	public static final TagKey<Block> RESIST_6 = tag("wind_resist_6");
	public static final TagKey<Block> IMMUNE = tag("wind_immune");

	private WindResistTags() {
	}

	private static TagKey<Block> tag(String path) {
		return TagKey.create(Registries.BLOCK, Typhoon.id(path));
	}
}
