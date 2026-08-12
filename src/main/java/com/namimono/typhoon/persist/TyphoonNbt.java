package com.namimono.typhoon.persist;

import com.namimono.typhoon.field.TyphoonSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 台风快照 ↔ NBT。SavedData 适配层只做读写，不承载领域规则。
 */
public final class TyphoonNbt {

	public static final String KEY_STORMS = "Storms";

	private TyphoonNbt() {
	}

	public static void writeSnapshots(CompoundTag tag, List<TyphoonSnapshot> snapshots) {
		ListTag list = new ListTag();
		for (TyphoonSnapshot snapshot : snapshots) {
			list.add(writeOne(snapshot));
		}
		tag.put(KEY_STORMS, list);
	}

	public static List<TyphoonSnapshot> readSnapshots(CompoundTag tag) {
		ListTag list = tag.getList(KEY_STORMS, Tag.TAG_COMPOUND);
		List<TyphoonSnapshot> out = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			out.add(readOne(list.getCompound(i)));
		}
		return out;
	}

	private static CompoundTag writeOne(TyphoonSnapshot s) {
		CompoundTag tag = new CompoundTag();
		tag.putString("Id", s.id());
		tag.putString("Name", s.name());
		tag.putDouble("StartX", s.startX());
		tag.putDouble("StartZ", s.startZ());
		tag.putDouble("EndX", s.endX());
		tag.putDouble("EndZ", s.endZ());
		tag.putInt("PeakGrade", s.peakGrade());
		tag.putDouble("HalfWidth", s.influenceHalfWidth());
		tag.putInt("Duration", s.durationTicks());
		tag.putInt("Elapsed", s.elapsedTicks());
		if (s.windOverrideX() != null) {
			tag.putDouble("WindX", s.windOverrideX());
			tag.putDouble("WindZ", s.windOverrideZ());
			tag.putBoolean("HasWindOverride", true);
		} else {
			tag.putBoolean("HasWindOverride", false);
		}
		return tag;
	}

	private static TyphoonSnapshot readOne(CompoundTag tag) {
		Double windX = null;
		Double windZ = null;
		if (tag.getBoolean("HasWindOverride")) {
			windX = tag.getDouble("WindX");
			windZ = tag.getDouble("WindZ");
		}
		return new TyphoonSnapshot(
				tag.getString("Id"),
				tag.getString("Name"),
				tag.getDouble("StartX"),
				tag.getDouble("StartZ"),
				tag.getDouble("EndX"),
				tag.getDouble("EndZ"),
				tag.getInt("PeakGrade"),
				tag.getDouble("HalfWidth"),
				tag.getInt("Duration"),
				windX,
				windZ,
				tag.getInt("Elapsed"));
	}
}
