package com.namimono.typhoon.network;

import com.namimono.typhoon.persist.TyphoonSavedData;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 适配层：把维度台风快照同步到客户端；不触碰原版天气 API。
 */
public final class TyphoonSync {

	private TyphoonSync() {
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(TyphoonSyncPayload.TYPE, TyphoonSyncPayload.STREAM_CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			sendTo(player, TyphoonSavedData.getIfPresent(player.serverLevel()));
		});
	}

	public static void tick(ServerLevel level, TyphoonSavedData data) {
		TyphoonSyncPayload payload = payloadFor(data);
		for (ServerPlayer player : level.players()) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	public static void sendTo(ServerPlayer player, TyphoonSavedData data) {
		ServerPlayNetworking.send(player, payloadFor(data));
	}

	private static TyphoonSyncPayload payloadFor(TyphoonSavedData data) {
		return data == null ? TyphoonSyncPayload.empty() : TyphoonSyncPayload.of(data.snapshots());
	}
}
