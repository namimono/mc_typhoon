package com.namimono.typhoon.field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WindwardFaceTest {

	@Test
	void exposedFaceIntoWindIsWindward() {
		// Wind +X; block at (5,64,0) with air at (4,64,0) → windward
		SolidProbe solids = solidSet();
		assertTrue(WindwardFace.isWindward(5, 64, 0, 1.0, 0.0, solids));
	}

	@Test
	void solidUpwindNeighborMakesLeeward() {
		SolidProbe solids = solidSet(pos(4, 64, 0));
		assertFalse(WindwardFace.isWindward(5, 64, 0, 1.0, 0.0, solids));
	}

	@Test
	void flatGroundRowIsMostlyLeewardExceptWindwardEdge() {
		// Horizontal +X wind; solid row x=0..4 at y=64
		Set<Long> solids = new HashSet<>();
		for (int x = 0; x <= 4; x++) {
			solids.add(pos(x, 64, 0));
		}
		SolidProbe probe = (x, y, z) -> solids.contains(pack(x, y, z));

		assertTrue(WindwardFace.isWindward(0, 64, 0, 1.0, 0.0, probe)); // edge into wind
		assertFalse(WindwardFace.isWindward(1, 64, 0, 1.0, 0.0, probe));
		assertFalse(WindwardFace.isWindward(4, 64, 0, 1.0, 0.0, probe));
	}

	@Test
	void windProjectsOntoDominantHorizontalAxis() {
		// Mostly +X with small +Z → treat as +X
		SolidProbe open = solidSet();
		assertTrue(WindwardFace.isWindward(3, 10, 3, 0.9, 0.1, open));
		SolidProbe blocked = solidSet(pos(2, 10, 3));
		assertFalse(WindwardFace.isWindward(3, 10, 3, 0.9, 0.1, blocked));

		// Dominant +Z
		assertTrue(WindwardFace.isWindward(3, 10, 3, 0.1, 0.9, open));
		SolidProbe blockedZ = solidSet(pos(3, 10, 2));
		assertFalse(WindwardFace.isWindward(3, 10, 3, 0.1, 0.9, blockedZ));
	}

	@Test
	void topFaceIsNotConsideredWindwardByVerticalNeighbor() {
		// Solid below does not make this leeward; horizontal wind only
		SolidProbe onlyBelow = solidSet(pos(5, 63, 0));
		assertTrue(WindwardFace.isWindward(5, 64, 0, 1.0, 0.0, onlyBelow));
	}

	@Test
	void reversedWindChecksOppositeNeighbor() {
		SolidProbe solids = solidSet(pos(6, 64, 0));
		assertFalse(WindwardFace.isWindward(5, 64, 0, -1.0, 0.0, solids));
		assertTrue(WindwardFace.isWindward(5, 64, 0, -1.0, 0.0, solidSet()));
	}

	private static SolidProbe solidSet(long... packed) {
		Set<Long> set = new HashSet<>();
		for (long p : packed) {
			set.add(p);
		}
		return (x, y, z) -> set.contains(pack(x, y, z));
	}

	private static long pos(int x, int y, int z) {
		return pack(x, y, z);
	}

	private static long pack(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}
}
