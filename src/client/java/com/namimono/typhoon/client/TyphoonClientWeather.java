package com.namimono.typhoon.client;

import com.namimono.typhoon.field.TyphoonRainEffect;
import com.namimono.typhoon.field.TyphoonRainFeel;
import com.namimono.typhoon.field.TyphoonSample;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 客户端台风雨与风向粒子：叠加 FX，不写 rainLevel / 不抢 /weather。
 */
public final class TyphoonClientWeather {

	/** 雨粒子基数（再乘 rainStrength；全局已下雨时减半避免叠层过密）。 */
	private static final int BASE_RAIN_PARTICLES = 28;
	/** 风向示意粒子基数。 */
	private static final int BASE_WIND_PARTICLES = 6;
	private static final float RAIN_SOUND_INTERVAL_TICKS = 20.0f;

	private static int soundCooldown;

	private TyphoonClientWeather() {
	}

	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null) {
			soundCooldown = 0;
			return;
		}

		Optional<TyphoonSample> sample = TyphoonClientState.sampleAt(player.getX(), player.getZ());
		if (sample.isEmpty()) {
			soundCooldown = 0;
			return;
		}

		TyphoonRainEffect effect = TyphoonRainFeel.resolve(sample.get());
		if (!effect.raining()) {
			soundCooldown = 0;
			return;
		}

		float globalRain = level.getRainLevel(1.0f);
		double densityScale = globalRain > 0.2f ? 0.45 : 1.0;
		spawnRainParticles(level, player, effect, densityScale);
		spawnWindParticles(level, player, effect, densityScale);
		tickRainSound(level, player, effect);
	}

	private static void spawnRainParticles(
			ClientLevel level, LocalPlayer player, TyphoonRainEffect effect, double densityScale) {
		RandomSource random = level.random;
		int count = Mth.ceil(BASE_RAIN_PARTICLES * effect.rainStrength() * densityScale);
		double px = player.getX();
		double py = player.getY();
		double pz = player.getZ();
		for (int i = 0; i < count; i++) {
			double x = px + (random.nextDouble() - 0.5) * 20.0;
			double z = pz + (random.nextDouble() - 0.5) * 20.0;
			int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
			double y = Math.max(py + 8.0 + random.nextDouble() * 6.0, surfaceY + 1.0);
			// 轻微随风漂移，雨帘仍以竖直落下为主
			double vx = effect.windX() * 0.15 * effect.rainStrength();
			double vz = effect.windZ() * 0.15 * effect.rainStrength();
			level.addParticle(ParticleTypes.RAIN, x, y, z, vx, -0.4, vz);
		}
	}

	private static void spawnWindParticles(
			ClientLevel level, LocalPlayer player, TyphoonRainEffect effect, double densityScale) {
		RandomSource random = level.random;
		int count = Mth.ceil(BASE_WIND_PARTICLES * effect.rainStrength() * densityScale);
		double px = player.getX();
		double py = player.getEyeY();
		double pz = player.getZ();
		double speed = 0.35 + 0.45 * effect.rainStrength();
		for (int i = 0; i < count; i++) {
			double x = px + (random.nextDouble() - 0.5) * 12.0 - effect.windX() * 4.0;
			double y = py + (random.nextDouble() - 0.5) * 3.0;
			double z = pz + (random.nextDouble() - 0.5) * 12.0 - effect.windZ() * 4.0;
			level.addParticle(
					ParticleTypes.CLOUD,
					x,
					y,
					z,
					effect.windX() * speed,
					0.02,
					effect.windZ() * speed);
		}
	}

	private static void tickRainSound(ClientLevel level, LocalPlayer player, TyphoonRainEffect effect) {
		if (soundCooldown > 0) {
			soundCooldown--;
			return;
		}
		soundCooldown = Math.max(5, (int) (RAIN_SOUND_INTERVAL_TICKS / Math.max(0.25, effect.rainStrength())));
		float volume = (float) (0.15 + 0.55 * effect.rainStrength());
		level.playLocalSound(
				player.getX(),
				player.getY(),
				player.getZ(),
				SoundEvents.WEATHER_RAIN,
				SoundSource.WEATHER,
				volume,
				1.0f,
				false);
	}
}
