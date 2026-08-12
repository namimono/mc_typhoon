package com.namimono.typhoon.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.namimono.typhoon.field.TyphoonRecord;
import com.namimono.typhoon.field.TyphoonSpawnRequest;
import com.namimono.typhoon.field.TyphoonSummary;
import com.namimono.typhoon.network.TyphoonSync;
import com.namimono.typhoon.persist.TyphoonSavedData;
import com.namimono.typhoon.ui.TyphoonBossBars;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /typhoon spawn|clear|list} — 权限约作弊 2 级。
 */
public final class TyphoonCommands {

	private TyphoonCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext,
			Commands.CommandSelection selection) {
		LiteralArgumentBuilder<CommandSourceStack> root = literal("typhoon")
				.requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS));

		root.then(buildSpawn());
		root.then(literal("clear").executes(TyphoonCommands::clear));
		root.then(literal("list").executes(TyphoonCommands::list));

		dispatcher.register(root);
	}

	/**
	 * spawn &lt;from&gt; &lt;to&gt; [peak] [widthChunks] [durationTicks] [name] [windX] [windZ]
	 */
	private static ArgumentBuilder<CommandSourceStack, ?> buildSpawn() {
		return literal("spawn")
				.then(argument("from", BlockPosArgument.blockPos())
						.then(argument("to", BlockPosArgument.blockPos())
								.executes(ctx -> spawn(ctx, null, null, null, null, null, null))
								.then(argument("peak", IntegerArgumentType.integer(1, 6))
										.executes(ctx -> spawn(ctx, IntegerArgumentType.getInteger(ctx, "peak"), null, null, null, null, null))
										.then(argument("widthChunks", IntegerArgumentType.integer(0))
												.executes(ctx -> spawn(
														ctx,
														IntegerArgumentType.getInteger(ctx, "peak"),
														IntegerArgumentType.getInteger(ctx, "widthChunks"),
														null,
														null,
														null,
														null))
												.then(argument("durationTicks", IntegerArgumentType.integer(1))
														.executes(ctx -> spawn(
																ctx,
																IntegerArgumentType.getInteger(ctx, "peak"),
																IntegerArgumentType.getInteger(ctx, "widthChunks"),
																IntegerArgumentType.getInteger(ctx, "durationTicks"),
																null,
																null,
																null))
														.then(argument("name", StringArgumentType.string())
																.executes(ctx -> spawn(
																		ctx,
																		IntegerArgumentType.getInteger(ctx, "peak"),
																		IntegerArgumentType.getInteger(ctx, "widthChunks"),
																		IntegerArgumentType.getInteger(ctx, "durationTicks"),
																		StringArgumentType.getString(ctx, "name"),
																		null,
																		null))
																.then(argument("windX", DoubleArgumentType.doubleArg())
																		.then(argument("windZ", DoubleArgumentType.doubleArg())
																				.executes(ctx -> spawn(
																						ctx,
																						IntegerArgumentType.getInteger(ctx, "peak"),
																						IntegerArgumentType.getInteger(ctx, "widthChunks"),
																						IntegerArgumentType.getInteger(ctx, "durationTicks"),
																						StringArgumentType.getString(ctx, "name"),
																						DoubleArgumentType.getDouble(ctx, "windX"),
																						DoubleArgumentType.getDouble(ctx, "windZ")))))))))));
	}

	private static int spawn(
			CommandContext<CommandSourceStack> ctx,
			Integer peak,
			Integer widthChunks,
			Integer durationTicks,
			String name,
			Double windX,
			Double windZ) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();
		BlockPos from = BlockPosArgument.getBlockPos(ctx, "from");
		BlockPos to = BlockPosArgument.getBlockPos(ctx, "to");

		TyphoonSpawnRequest request = new TyphoonSpawnRequest(
				from.getX() + 0.5,
				from.getZ() + 0.5,
				to.getX() + 0.5,
				to.getZ() + 0.5,
				peak,
				widthChunks,
				durationTicks,
				name,
				windX,
				windZ);

		TyphoonRecord record = TyphoonSavedData.get(level).spawn(request);
		TyphoonSync.tick(level, TyphoonSavedData.get(level));
		source.sendSuccess(
				() -> Component.literal(formatSpawnFeedback(record, from, to)),
				true);
		return Command.SINGLE_SUCCESS;
	}

	static String formatSpawnFeedback(TyphoonRecord record, BlockPos from, BlockPos to) {
		return String.format(
				Locale.ROOT,
				"已生成台风 %s（id=%s）路径 (%d,%d)→(%d,%d) 峰值=%d 半宽=%.0f 格 时长=%d tick",
				record.name(),
				record.id(),
				from.getX(),
				from.getZ(),
				to.getX(),
				to.getZ(),
				record.peakGrade(),
				record.influenceHalfWidth(),
				record.durationTicks());
	}

	private static int clear(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getLevel();
		TyphoonSavedData data = TyphoonSavedData.get(level);
		data.clear();
		TyphoonBossBars.clear(level);
		TyphoonSync.tick(level, data);
		ctx.getSource().sendSuccess(() -> Component.literal("已清除当前维度全部台风"), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getLevel();
		List<TyphoonSummary> listed = TyphoonSavedData.get(level).list();
		if (listed.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.literal("当前维度没有进行中的台风"), false);
			return Command.SINGLE_SUCCESS;
		}
		StringBuilder body = new StringBuilder("进行中的台风：");
		for (TyphoonSummary summary : listed) {
			body.append('\n')
					.append("- id=")
					.append(summary.id())
					.append(" 名称=")
					.append(summary.name())
					.append(" 进度=")
					.append(String.format(Locale.ROOT, "%.1f%%", summary.pathProgress() * 100.0));
		}
		String message = body.toString();
		ctx.getSource().sendSuccess(() -> Component.literal(message), false);
		return listed.size();
	}
}
