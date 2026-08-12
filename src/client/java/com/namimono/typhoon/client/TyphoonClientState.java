package com.namimono.typhoon.client;

import com.namimono.typhoon.field.TyphoonFields;
import com.namimono.typhoon.field.TyphoonRecord;
import com.namimono.typhoon.field.TyphoonSample;
import com.namimono.typhoon.field.TyphoonSnapshot;
import java.util.List;
import java.util.Optional;

/**
 * 客户端缓存的台风快照（由服务端同步）；非权威。
 */
public final class TyphoonClientState {

	private static TyphoonFields fields = new TyphoonFields();

	private TyphoonClientState() {
	}

	public static void apply(List<TyphoonSnapshot> storms) {
		fields = TyphoonFields.fromSnapshots(storms);
	}

	public static void clear() {
		fields = new TyphoonFields();
	}

	public static Optional<TyphoonRecord> tracked() {
		return fields.tracked();
	}

	public static Optional<TyphoonSample> sampleAt(double x, double z) {
		return tracked().map(record -> record.field().sample(x, z));
	}

	public static boolean hasStorms() {
		return !fields.isEmpty();
	}
}
