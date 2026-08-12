package com.namimono.typhoon.drop;

import com.namimono.typhoon.field.BlownDropImpact;
import com.namimono.typhoon.field.BlownDropImpactCooldown;
import com.namimono.typhoon.field.BlownDropMotion;
import com.namimono.typhoon.field.BlownDropMotionEffect;
import com.namimono.typhoon.field.TyphoonField;
import com.namimono.typhoon.field.TyphoonRecord;
import com.namimono.typhoon.field.TyphoonSample;
import com.namimono.typhoon.persist.TyphoonSavedData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 适配层：掉落物风力施力与撞击伤害。
 * <p>
 * 不改原版 despawn、保留 merge；撞后不销毁掉落物；不做碎片实体、不打建筑。
 */
public final class BlownDropApplier {

	private BlownDropApplier() {
	}

	public static void tick(ServerLevel level, TyphoonSavedData data) {
		Optional<TyphoonRecord> tracked = data.tracked();
		if (tracked.isEmpty() || level.players().isEmpty()) {
			return;
		}

		TyphoonField field = tracked.orElseThrow().field();
		List<ItemEntity> candidates = collectNearPlayers(level);
		if (candidates.isEmpty()) {
			return;
		}

		BlownDropImpactCooldown cooldown = data.blownDropImpactCooldown();
		int budget = BlownDropMotion.FORCE_BUDGET;
		int size = candidates.size();
		int cursor = data.blownDropCursor();
		int applied = 0;
		for (int i = 0; i < size && applied < budget; i++) {
			ItemEntity item = candidates.get(Math.floorMod(cursor + i, size));
			BlownDropMotionEffect motion = BlownDropMotion.resolve(field.sample(item.getX(), item.getZ()));
			if (!motion.active()) {
				continue;
			}
			applyForce(item, motion);
			applied++;
		}
		data.setBlownDropCursor(cursor + Math.max(applied, 1));

		// 撞击与施力预算解耦：附近够速掉落物均可结算
		for (ItemEntity item : candidates) {
			TyphoonSample sample = field.sample(item.getX(), item.getZ());
			if (sample.localIntensity() <= 0.0) {
				continue;
			}
			tryImpact(level, item, sample, cooldown);
		}
	}

	private static List<ItemEntity> collectNearPlayers(ServerLevel level) {
		List<ItemEntity> out = new ArrayList<>();
		Set<Integer> seen = new HashSet<>();
		double hx = BlownDropMotion.PLAYER_HORIZONTAL_RADIUS;
		double hy = BlownDropMotion.PLAYER_VERTICAL_RADIUS;
		for (ServerPlayer player : level.players()) {
			AABB box = player.getBoundingBox().inflate(hx, hy, hx);
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
				if (seen.add(item.getId())) {
					out.add(item);
				}
			}
		}
		return out;
	}

	static void applyForce(ItemEntity item, BlownDropMotionEffect motion) {
		Vec3 delta = item.getDeltaMovement();
		double vx = delta.x * 20.0;
		double vz = delta.z * 20.0;
		double[] next = BlownDropMotion.stepTowardTerminal(motion, vx, vz);
		double lift = BlownDropMotion.blocksPerSecondToPerTick(
				BlownDropMotion.liftImpulseBlocksPerSecond(motion));
		item.setDeltaMovement(
				BlownDropMotion.blocksPerSecondToPerTick(next[0]),
				delta.y + lift,
				BlownDropMotion.blocksPerSecondToPerTick(next[1]));
		item.hasImpulse = true;
	}

	static void tryImpact(
			ServerLevel level,
			ItemEntity item,
			TyphoonSample sample,
			BlownDropImpactCooldown cooldown) {
		Vec3 delta = item.getDeltaMovement();
		double vx = delta.x * 20.0;
		double vz = delta.z * 20.0;
		if (!BlownDropImpact.isWindDriven(vx, vz)) {
			return;
		}

		AABB box = item.getBoundingBox().inflate(0.15);
		List<LivingEntity> victims = level.getEntitiesOfClass(
				LivingEntity.class,
				box,
				LivingEntity::isAlive);
		long gameTick = level.getGameTime();
		float damage = BlownDropImpact.damageAmount(sample.localIntensity());
		for (LivingEntity living : victims) {
			if (!BlownDropImpact.shouldDealDamage(vx, vz, sample.localIntensity(), isExempt(living))) {
				continue;
			}
			if (!cooldown.tryHit(item.getId(), living.getId(), gameTick)) {
				continue;
			}
			living.hurt(level.damageSources().generic(), damage);
		}
	}

	private static boolean isExempt(LivingEntity living) {
		if (!(living instanceof Player player)) {
			return false;
		}
		return BlownDropImpact.isImpactExempt(
				player.isSpectator(),
				player.isCreative(),
				player.getAbilities().mayfly);
	}
}
