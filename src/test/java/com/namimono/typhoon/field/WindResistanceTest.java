package com.namimono.typhoon.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class WindResistanceTest {

	@Test
	void exceptionTagsAssignLeafGlassWoodStoneDeepslateIronAndTopTier() {
		assertEquals(1, WindResistance.resolve(WindBlockFacts.exception(1)).orElseThrow());
		assertEquals(2, WindResistance.resolve(WindBlockFacts.exception(2)).orElseThrow());
		assertEquals(3, WindResistance.resolve(WindBlockFacts.exception(3)).orElseThrow());
		assertEquals(4, WindResistance.resolve(WindBlockFacts.exception(4)).orElseThrow());
		assertEquals(5, WindResistance.resolve(WindBlockFacts.exception(5)).orElseThrow());
		assertEquals(6, WindResistance.resolve(WindBlockFacts.exception(6)).orElseThrow());
	}

	@Test
	void immuneAndNonParticipatingBlocksHaveNoGrade() {
		assertTrue(WindResistance.resolve(WindBlockFacts.immuneBlock()).isEmpty());
		assertTrue(WindResistance.resolve(WindBlockFacts.skip()).isEmpty());
	}

	@Test
	void hardnessBinsFillGapsWhenNoExceptionTag() {
		assertEquals(1, WindResistance.resolve(WindBlockFacts.hardness(0.0f)).orElseThrow());
		assertEquals(1, WindResistance.resolve(WindBlockFacts.hardness(0.39f)).orElseThrow());
		assertEquals(2, WindResistance.resolve(WindBlockFacts.hardness(0.4f)).orElseThrow());
		assertEquals(2, WindResistance.resolve(WindBlockFacts.hardness(0.99f)).orElseThrow());
		assertEquals(3, WindResistance.resolve(WindBlockFacts.hardness(1.0f)).orElseThrow());
		assertEquals(3, WindResistance.resolve(WindBlockFacts.hardness(1.99f)).orElseThrow());
		assertEquals(4, WindResistance.resolve(WindBlockFacts.hardness(2.0f)).orElseThrow());
		assertEquals(4, WindResistance.resolve(WindBlockFacts.hardness(3.49f)).orElseThrow());
		assertEquals(5, WindResistance.resolve(WindBlockFacts.hardness(3.5f)).orElseThrow());
		assertEquals(5, WindResistance.resolve(WindBlockFacts.hardness(19.9f)).orElseThrow());
		assertEquals(6, WindResistance.resolve(WindBlockFacts.hardness(20.0f)).orElseThrow());
	}

	@Test
	void negativeHardnessIsImmuneEvenWithoutImmuneFlag() {
		OptionalInt grade = WindResistance.resolve(WindBlockFacts.hardness(-1.0f));
		assertTrue(grade.isEmpty());
	}

	@Test
	void exceptionTagBeatsHardnessBinning() {
		// Dirt hardness 0.5 would bin to 2; glass-like exception forces 1
		assertEquals(1, WindResistance.resolve(WindBlockFacts.exceptionWithHardness(1, 0.5f)).orElseThrow());
		// Netherite hardness 50 would bin to 6; immune wins
		assertTrue(WindResistance.resolve(WindBlockFacts.immuneWithHardness(50.0f)).isEmpty());
	}

	@Test
	void nonParticipatingBeatsEverything() {
		assertFalse(WindResistance.resolve(
				new WindBlockFacts(true, true, 1, 5.0f)).isPresent());
	}
}
