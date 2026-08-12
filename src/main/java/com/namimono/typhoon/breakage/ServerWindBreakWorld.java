package com.namimono.typhoon.breakage;

import com.namimono.typhoon.field.WindBlockFacts;
import com.namimono.typhoon.field.WindBreakWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * 将 ServerLevel 方块探测适配为台风场 {@link WindBreakWorld} 接缝。
 */
public final class ServerWindBreakWorld implements WindBreakWorld {

	private final ServerLevel level;
	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

	public ServerWindBreakWorld(ServerLevel level) {
		this.level = level;
	}

	@Override
	public WindBlockFacts factsAt(int x, int y, int z) {
		BlockState state = level.getBlockState(cursor.set(x, y, z));
		FluidState fluid = state.getFluidState();
		boolean nonParticipating = state.isAir()
				|| !fluid.isEmpty()
				|| state.canBeReplaced();
		boolean immune = state.is(WindResistTags.IMMUNE);
		Integer exception = exceptionGrade(state);
		float hardness = state.getDestroySpeed(level, cursor);
		return new WindBlockFacts(nonParticipating, immune, exception, hardness);
	}

	@Override
	public boolean isSolid(int x, int y, int z) {
		BlockState state = level.getBlockState(cursor.set(x, y, z));
		return state.blocksMotion();
	}

	@Override
	public boolean isChunkLoaded(int blockX, int blockZ) {
		return level.hasChunk(blockX >> 4, blockZ >> 4);
	}

	/** 高档优先：深板岩等可能同时落在较低档嵌套 tag 中。 */
	private static Integer exceptionGrade(BlockState state) {
		if (state.is(WindResistTags.RESIST_6)) {
			return 6;
		}
		if (state.is(WindResistTags.RESIST_5)) {
			return 5;
		}
		if (state.is(WindResistTags.RESIST_4)) {
			return 4;
		}
		if (state.is(WindResistTags.RESIST_3)) {
			return 3;
		}
		if (state.is(WindResistTags.RESIST_2)) {
			return 2;
		}
		if (state.is(WindResistTags.RESIST_1)) {
			return 1;
		}
		return null;
	}
}
