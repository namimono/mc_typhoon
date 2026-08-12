package com.namimono.typhoon.player;

import com.namimono.typhoon.Typhoon;
import com.namimono.typhoon.field.PlayerWindEffect;
import com.namimono.typhoon.field.PlayerWindFeel;
import com.namimono.typhoon.field.TyphoonField;
import com.namimono.typhoon.field.TyphoonRecord;
import com.namimono.typhoon.field.TyphoonSample;
import com.namimono.typhoon.persist.TyphoonSavedData;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * 适配层：把玩家风力效果落到 Minecraft 运动（推力、移速、禁冲刺、跳偏、抬升）。
 */
public final class PlayerWindApplier {

	private static final ResourceLocation SPEED_MODIFIER_ID = Typhoon.id("player_wind_speed");
	/** 跳偏：离地时额外水平推力倍率。 */
	private static final double JUMP_AIR_PUSH_SCALE = 2.5;

	private PlayerWindApplier() {
	}

	public static void tick(ServerLevel level, TyphoonSavedData data) {
		Optional<TyphoonRecord> tracked = data.tracked();
		if (tracked.isEmpty()) {
			for (ServerPlayer player : level.players()) {
				clearSpeedModifier(player);
			}
			return;
		}
		TyphoonField field = tracked.orElseThrow().field();
		for (ServerPlayer player : level.players()) {
			apply(player, field.sample(player.getX(), player.getZ()));
		}
	}

	static void apply(ServerPlayer player, TyphoonSample sample) {
		boolean exempt = PlayerWindFeel.isWindExempt(
				player.isSpectator(),
				player.isCreative(),
				player.getAbilities().mayfly);
		PlayerWindEffect effect = PlayerWindFeel.resolve(sample, exempt);
		if (!effect.active()) {
			clearSpeedModifier(player);
			return;
		}

		double push = effect.horizontalPushPerTick();
		if (effect.jumpBiased() && !player.onGround()) {
			push *= JUMP_AIR_PUSH_SCALE;
		}
		double lift = effect.verticalLiftPerTick();
		player.addDeltaMovement(new Vec3(
				effect.windX() * push,
				lift,
				effect.windZ() * push));
		player.hurtMarked = true;

		if (effect.sprintBlocked() && player.isSprinting()) {
			player.setSprinting(false);
		}

		updateSpeedModifier(player, effect);
	}

	private static void updateSpeedModifier(ServerPlayer player, PlayerWindEffect effect) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		Vec3 look = player.getLookAngle();
		double mult = effect.movementSpeedMultiplier(look.x, look.z);
		if (Math.abs(mult - 1.0) < 1e-6) {
			speed.removeModifier(SPEED_MODIFIER_ID);
			return;
		}
		speed.addOrUpdateTransientModifier(new AttributeModifier(
				SPEED_MODIFIER_ID,
				mult - 1.0,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	private static void clearSpeedModifier(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SPEED_MODIFIER_ID);
		}
	}
}
