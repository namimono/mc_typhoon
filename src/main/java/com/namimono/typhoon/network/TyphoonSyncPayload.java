package com.namimono.typhoon.network;

import com.namimono.typhoon.Typhoon;
import com.namimono.typhoon.field.TyphoonSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端→客户端：当前维度进行中台风的几何/强度快照（不写全局天气）。
 */
public record TyphoonSyncPayload(List<TyphoonSnapshot> storms) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<TyphoonSyncPayload> TYPE =
			new CustomPacketPayload.Type<>(Typhoon.id("sync_storms"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TyphoonSyncPayload> STREAM_CODEC =
			StreamCodec.of(TyphoonSyncPayload::encode, TyphoonSyncPayload::decode);

	public TyphoonSyncPayload {
		storms = List.copyOf(storms);
	}

	public static TyphoonSyncPayload empty() {
		return new TyphoonSyncPayload(List.of());
	}

	public static TyphoonSyncPayload of(List<TyphoonSnapshot> storms) {
		return new TyphoonSyncPayload(storms);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void encode(RegistryFriendlyByteBuf buf, TyphoonSyncPayload payload) {
		List<TyphoonSnapshot> storms = payload.storms();
		buf.writeVarInt(storms.size());
		for (TyphoonSnapshot storm : storms) {
			buf.writeUtf(storm.id());
			buf.writeUtf(storm.name());
			buf.writeDouble(storm.startX());
			buf.writeDouble(storm.startZ());
			buf.writeDouble(storm.endX());
			buf.writeDouble(storm.endZ());
			buf.writeVarInt(storm.peakGrade());
			buf.writeDouble(storm.influenceHalfWidth());
			buf.writeVarInt(storm.durationTicks());
			buf.writeVarInt(storm.elapsedTicks());
			boolean hasOverride = storm.windOverrideX() != null;
			buf.writeBoolean(hasOverride);
			if (hasOverride) {
				buf.writeDouble(storm.windOverrideX());
				buf.writeDouble(storm.windOverrideZ());
			}
		}
	}

	private static TyphoonSyncPayload decode(RegistryFriendlyByteBuf buf) {
		int size = buf.readVarInt();
		if (size < 0 || size > 64) {
			throw new IllegalArgumentException("invalid typhoon sync count: " + size);
		}
		List<TyphoonSnapshot> storms = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			String id = buf.readUtf();
			String name = buf.readUtf();
			double startX = buf.readDouble();
			double startZ = buf.readDouble();
			double endX = buf.readDouble();
			double endZ = buf.readDouble();
			int peakGrade = buf.readVarInt();
			double halfWidth = buf.readDouble();
			int duration = buf.readVarInt();
			int elapsed = buf.readVarInt();
			Double windX = null;
			Double windZ = null;
			if (buf.readBoolean()) {
				windX = buf.readDouble();
				windZ = buf.readDouble();
			}
			storms.add(new TyphoonSnapshot(
					id, name, startX, startZ, endX, endZ,
					peakGrade, halfWidth, duration, windX, windZ, elapsed));
		}
		return new TyphoonSyncPayload(Collections.unmodifiableList(storms));
	}
}
