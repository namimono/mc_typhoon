package com.namimono.typhoon.client;

import com.namimono.typhoon.network.TyphoonSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class TyphoonClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(TyphoonSyncPayload.TYPE, (payload, context) -> {
			TyphoonClientState.apply(payload.storms());
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TyphoonClientState.clear());
		ClientTickEvents.END_CLIENT_TICK.register(TyphoonClientWeather::tick);
	}
}
