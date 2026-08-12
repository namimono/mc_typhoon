package com.namimono.typhoon.persist;

import com.namimono.typhoon.field.TyphoonFields;
import com.namimono.typhoon.field.TyphoonRecord;
import com.namimono.typhoon.field.TyphoonSnapshot;
import com.namimono.typhoon.field.TyphoonSpawnRequest;
import com.namimono.typhoon.field.TyphoonSummary;
import com.namimono.typhoon.field.WindBreakEngine;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 维度台风权威状态（SavedData）。委托 {@link TyphoonFields} 做领域行为。
 */
public final class TyphoonSavedData extends SavedData {

	public static final String ID = "typhoon_storms";

	private TyphoonFields fields = new TyphoonFields();
	/** 会话内裂纹状态；不写入存档。 */
	private final WindBreakEngine windBreakEngine = new WindBreakEngine();

	public static SavedData.Factory<TyphoonSavedData> factory() {
		return new SavedData.Factory<>(TyphoonSavedData::new, TyphoonSavedData::load, null);
	}

	public static TyphoonSavedData get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(factory(), ID);
	}

	/** 未创建过存档数据时返回 null，避免无台风维度每 tick 空建 SavedData。 */
	public static TyphoonSavedData getIfPresent(ServerLevel level) {
		return level.getDataStorage().get(factory(), ID);
	}

	public static TyphoonSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
		TyphoonSavedData data = new TyphoonSavedData();
		data.fields = TyphoonFields.fromSnapshots(TyphoonNbt.readSnapshots(tag));
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		TyphoonNbt.writeSnapshots(tag, fields.snapshots());
		return tag;
	}

	public TyphoonRecord spawn(TyphoonSpawnRequest request) {
		TyphoonRecord record = fields.spawn(request);
		setDirty();
		return record;
	}

	public void clear() {
		fields.clear();
		setDirty();
	}

	public List<TyphoonSummary> list() {
		return fields.list();
	}

	public void tick() {
		if (fields.isEmpty()) {
			return;
		}
		fields.tick(1);
		setDirty();
	}

	public Optional<TyphoonRecord> tracked() {
		return fields.tracked();
	}

	public List<TyphoonSnapshot> snapshots() {
		return fields.snapshots();
	}

	public WindBreakEngine windBreakEngine() {
		return windBreakEngine;
	}
}
