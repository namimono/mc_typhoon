package com.namimono.typhoon.field;

/**
 * 一场进行中的台风：身份 + 台风场。
 */
public final class TyphoonRecord {

	private final String id;
	private final String name;
	private final double startX;
	private final double startZ;
	private final double endX;
	private final double endZ;
	private final TyphoonField field;

	TyphoonRecord(
			String id,
			String name,
			double startX,
			double startZ,
			double endX,
			double endZ,
			TyphoonField field) {
		this.id = id;
		this.name = name;
		this.startX = startX;
		this.startZ = startZ;
		this.endX = endX;
		this.endZ = endZ;
		this.field = field;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public TyphoonField field() {
		return field;
	}

	public int peakGrade() {
		return field.peakGrade();
	}

	public double influenceHalfWidth() {
		return field.influenceHalfWidth();
	}

	public int durationTicks() {
		return field.durationTicks();
	}

	public double pathProgress() {
		return field.pathProgress();
	}

	public boolean finished() {
		return field.finished();
	}

	TyphoonSnapshot toSnapshot() {
		return new TyphoonSnapshot(
				id,
				name,
				startX,
				startZ,
				endX,
				endZ,
				field.peakGrade(),
				field.influenceHalfWidth(),
				field.durationTicks(),
				field.windOverrideX(),
				field.windOverrideZ(),
				field.elapsedTicks());
	}

	static TyphoonRecord fromSnapshot(TyphoonSnapshot snapshot) {
		TyphoonField field = TyphoonField.create(
				snapshot.startX(),
				snapshot.startZ(),
				snapshot.endX(),
				snapshot.endZ(),
				snapshot.peakGrade(),
				snapshot.influenceHalfWidth(),
				snapshot.durationTicks(),
				snapshot.windOverrideX(),
				snapshot.windOverrideZ());
		field.restoreElapsedTicks(snapshot.elapsedTicks());
		return new TyphoonRecord(
				snapshot.id(),
				snapshot.name(),
				snapshot.startX(),
				snapshot.startZ(),
				snapshot.endX(),
				snapshot.endZ(),
				field);
	}

	TyphoonSummary toSummary() {
		return new TyphoonSummary(id, name, pathProgress());
	}
}
