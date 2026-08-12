package com.namimono.typhoon.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.namimono.typhoon.field.TyphoonFields;
import com.namimono.typhoon.field.TyphoonRecord;
import com.namimono.typhoon.field.TyphoonSpawnRequest;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class TyphoonCommandsFeedbackTest {

	@Test
	void spawnFeedbackIncludesNamePathAndDefaults() {
		TyphoonFields fields = new TyphoonFields(() -> "cd12");
		TyphoonRecord record = fields.spawn(TyphoonSpawnRequest.of(0, 0, 100, 0));
		String text = TyphoonCommands.formatSpawnFeedback(record, new BlockPos(0, 64, 0), new BlockPos(100, 64, 0));
		assertTrue(text.contains("Typhoon-cd12"));
		assertTrue(text.contains("id=cd12"));
		assertTrue(text.contains("(0,0)→(100,0)"));
		assertTrue(text.contains("峰值=6"));
		assertTrue(text.contains("12000 tick"));
	}
}
