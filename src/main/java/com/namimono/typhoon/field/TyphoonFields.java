package com.namimono.typhoon.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 当前维度进行中的台风集合：生成、清除、列表、推进。纯逻辑接缝。
 */
public final class TyphoonFields {

	private final Supplier<String> idFactory;
	private final List<TyphoonRecord> storms = new ArrayList<>();

	public TyphoonFields() {
		this(() -> UUID.randomUUID().toString().replace("-", "").substring(0, 8));
	}

	public TyphoonFields(Supplier<String> idFactory) {
		this.idFactory = idFactory;
	}

	public static TyphoonFields fromSnapshots(List<TyphoonSnapshot> snapshots) {
		TyphoonFields fields = new TyphoonFields();
		for (TyphoonSnapshot snapshot : snapshots) {
			fields.storms.add(TyphoonRecord.fromSnapshot(snapshot));
		}
		return fields;
	}

	public TyphoonRecord spawn(TyphoonSpawnRequest request) {
		String id = idFactory.get();
		String name = request.name() != null ? request.name() : "Typhoon-" + id;
		int peak = request.resolvedPeakGrade();
		double halfWidth = request.resolvedInfluenceHalfWidthBlocks();
		int duration = request.resolvedDurationTicks();
		TyphoonField field = TyphoonField.create(
				request.startX(),
				request.startZ(),
				request.endX(),
				request.endZ(),
				peak,
				halfWidth,
				duration,
				request.windOverrideX(),
				request.windOverrideZ());
		TyphoonRecord record = new TyphoonRecord(
				id,
				name,
				request.startX(),
				request.startZ(),
				request.endX(),
				request.endZ(),
				field);
		storms.add(record);
		return record;
	}

	public void clear() {
		storms.clear();
	}

	public List<TyphoonSummary> list() {
		List<TyphoonSummary> out = new ArrayList<>(storms.size());
		for (TyphoonRecord storm : storms) {
			out.add(storm.toSummary());
		}
		return Collections.unmodifiableList(out);
	}

	public void tick(int deltaTicks) {
		for (TyphoonRecord storm : storms) {
			storm.field().tick(deltaTicks);
		}
		storms.removeIf(TyphoonRecord::finished);
	}

	/** 第一刀 Boss 栏跟踪：最近生成且仍进行中的一场。 */
	public Optional<TyphoonRecord> tracked() {
		if (storms.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(storms.get(storms.size() - 1));
	}

	public List<TyphoonSnapshot> snapshots() {
		List<TyphoonSnapshot> out = new ArrayList<>(storms.size());
		for (TyphoonRecord storm : storms) {
			out.add(storm.toSnapshot());
		}
		return Collections.unmodifiableList(out);
	}

	public boolean isEmpty() {
		return storms.isEmpty();
	}
}
