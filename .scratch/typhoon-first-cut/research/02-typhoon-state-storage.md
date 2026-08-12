# Research: 台风状态存哪、怎么 tick、如何存档

**Ticket:** `.scratch/typhoon-first-cut/issues/02-typhoon-state-storage.md`  
**Scope:** Fabric + Minecraft **1.21.1**, 模组使用 **official Mojang mappings**（`build.gradle`: `loom.officialMojangMappings()`），Fabric API `0.116.15+1.21.1`。单人优先（integrated server 仍是权威服务端）。

## Primary sources used

| Source | What was verified |
|--------|-------------------|
| Loom remapped MC jar `1.21.1` (Mojmap) via `javap` | `SavedData`, `SavedData.Factory`, `DimensionDataStorage`, `ServerLevel`, `Raids`/`Raid`, `Entity` |
| Fabric API sources in loom-cache: `fabric-data-attachment-api-v1` 1.4.7+… | Attachment API public types + `AttachmentPersistentState` |
| Fabric API sources: `fabric-lifecycle-events-v1` 2.6.0+… | `ServerTickEvents`, `ServerWorldEvents`, mixins into `ServerLevel`/`MinecraftServer` |
| Fabric docs (current site) | [Saved Data](https://docs.fabricmc.net/develop/serialization/saved-data), [Data Attachments](https://docs.fabricmc.net/develop/serialization/data-attachments) — **API shape for latest MC; 1.21.1 details taken from jar** |
| Fabric Wiki (redirect) | [Persistent State → docs](https://wiki.fabricmc.net/tutorial:persistent_states); [Global World Data](https://wiki.fabricmc.net/tutorial:global_data) notes Attachment as preferred over CCA on recent versions |
| Yarn 1.21.1 mappings | Mojmap ↔ Yarn names for docs cross-read |

**Version caveat:** Current Fabric docs pages are versioned for newer Minecraft and show `SavedDataType` / `computeIfAbsent(TYPE)` without a free-string id. **1.21.1 jar facts below are authoritative for this repo.**

---

## Naming map (1.21.1)

| Mojmap (this repo) | Yarn | Role |
|--------------------|------|------|
| `net.minecraft.world.level.saveddata.SavedData` | `PersistentState` | Per-dimension (or per storage) persisted blob |
| `SavedData.Factory<T>` | `PersistentState.Type` / factory record | constructor + NBT deserializer + optional DataFix |
| `net.minecraft.world.level.storage.DimensionDataStorage` | `PersistentStateManager` | Load/save `SavedData` to world `data/` |
| `ServerLevel.getDataStorage()` | `ServerWorld.getPersistentStateManager()` | Access storage |
| `SavedData.setDirty()` | `markDirty()` | Mark for write-on-save |
| `CompoundTag` | `NbtCompound` | NBT root |
| `ServerLevel` | `ServerWorld` | Server dimension |

Verified Yarn tiny: `class_18` → Yarn `PersistentState` / Mojmap `SavedData`; `class_26` → Yarn `PersistentStateManager` / Mojmap `DimensionDataStorage`.

---

## What “typhoon state” needs

First-cut storm is **not** a local actor; it is a **regional process**:

- Path center / progress along path  
- Primary wind direction  
- 1D intensity profile parameters (weak→strong→eye→strong→weak) + path width  
- Lifecycle (age, phase, despawn)  
- Possibly multiple concurrent storms later  

It must:

1. **Live on the logical server** (singleplayer integrated server included).  
2. **Tick every world tick** even when players are far from the geometric “center.”  
3. **Survive save/quit/reload** of the world.  
4. Stay **dimension-scoped** (Overworld first cut).

---

## Option A — `SavedData` on `ServerLevel` (**recommended**)

### API facts (1.21.1 Mojmap jar)

```text
abstract class SavedData {
  abstract CompoundTag save(CompoundTag, HolderLookup.Provider);
  void setDirty();
  void setDirty(boolean);
  boolean isDirty();
  // File write only runs when isDirty()
}

record SavedData.Factory<T extends SavedData>(
  Supplier<T> constructor,
  BiFunction<CompoundTag, HolderLookup.Provider, T> deserializer,
  DataFixTypes type  // may be null for mod data
)

class DimensionDataStorage {
  <T extends SavedData> T computeIfAbsent(SavedData.Factory<T>, String id);
  <T extends SavedData> T get(SavedData.Factory<T>, String id);
  void save();
}

ServerLevel#getDataStorage() -> DimensionDataStorage
```

`ServerLevel.getDataStorage()` delegates to `ServerChunkCache.getDataStorage()` (javap). Files land under the dimension’s **`data/<id>.dat`** (Fabric Saved Data docs; wiki same idea).

### Vanilla precedent: `Raids`

Closest first-party pattern to “multiple timed regional events per dimension”:

- `Raids extends SavedData` holds a map of `Raid` instances.  
- Constructed via `Raids.factory(ServerLevel)` → `SavedData.Factory`.  
- Loaded in `ServerLevel` init: `getDataStorage().computeIfAbsent(Raids.factory(this), Raids.getFileId(...))`.  
- **Ticked from `ServerLevel.tick`**: bytecode invokes `this.raids.tick()`; `Raids.tick` iterates `raidMap` and calls each `Raid.tick()`.  
- Each `Raid` is a plain POJO with its own NBT `save` / ctor-from-NBT — **not** an `Entity`.

This is the shape typhoon should copy: **one `TyphoonSavedData` container + list/map of storm records**.

### Suggested shape for this mod

```java
// Conceptual — not production code
public final class TyphoonSavedData extends SavedData {
  public static final String ID = "typhoon"; // -> data/typhoon.dat

  private final List<TyphoonInstance> storms = new ArrayList<>();

  public static SavedData.Factory<TyphoonSavedData> factory() {
    return new SavedData.Factory<>(
      TyphoonSavedData::new,
      TyphoonSavedData::load,
      null // no datafixer for first cut
    );
  }

  public static TyphoonSavedData get(ServerLevel level) {
    return level.getDataStorage().computeIfAbsent(factory(), ID);
  }

  public static TyphoonSavedData load(CompoundTag tag, HolderLookup.Provider regs) { /* ... */ }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider regs) { /* write storms */ return tag; }

  public void tick(ServerLevel level) {
    // advance path, lifecycle; apply wind/break/rain queries
    // remove finished storms
    setDirty(); // or only when something actually changed
  }

  public TyphoonInstance spawn(...) {
    storms.add(...);
    setDirty();
    return ...;
  }
}
```

**Registration / access:** no Fabric registry entry required. First access via `computeIfAbsent` creates or loads. Optionally warm-load on `ServerWorldEvents.LOAD` (Fabric lifecycle docs/source note it is for world-specific metadata / SavedData init).

**Dirty contract:** `SavedData.save(File, …)` returns early if `!isDirty()` (javap). Mutating methods must call `setDirty()` or data will not flush — confirmed by Fabric Saved Data tutorial and jar.

### Pros / cons

| Pros | Cons |
|------|------|
| First-party, stable, used by raids/maps/forced chunks | Manual NBT (or Codec-composed NBT) serialization |
| Natural multi-storm container | No built-in client sync (send packets yourself) |
| Independent of chunk entity ticking | Must wire tick hook yourself |
| Dimension-scoped matches Overworld weather | File id collisions if two mods use same string (prefix with mod id: `typhoon_storms`) |
| Matches “server authoritative process” | — |

---

## Option B — Fabric Data Attachment API on `ServerLevel`

### API facts (Fabric API 0.116.15 sources in this project)

- Package: `net.fabricmc.fabric.api.attachment.v1`  
- Types: `AttachmentRegistry`, `AttachmentType`, `AttachmentTarget`  
- Targets via mixin: **`Entity`, `BlockEntity`, `ServerLevel`, `ChunkAccess`** (`AttachmentTarget` javadoc).  
- Marked **`@ApiStatus.Experimental`**.  
- Persistence: `AttachmentRegistry.createPersistent(id, Codec)` or builder `.persistent(Codec)`.  
- Optional client sync: `.syncWith(StreamCodec, AttachmentSyncPredicate)`.  
- Mutable values: docs + javadoc encourage **immutable** values; if mutating in place you must still trigger dirty/sync paths carefully.

### How level attachments actually persist

Fabric does **not** invent a second storage backend. `ServerWorldMixin` (attachment) on `ServerLevel.<init>`:

```java
var type = new SavedData.Factory<>(
  () -> new AttachmentPersistentState(world),
  (nbt, wrapperLookup) -> AttachmentPersistentState.read(world, nbt, server.registryAccess()),
  null
);
world.getDataStorage().computeIfAbsent(type, AttachmentPersistentState.ID); // "fabric_attachments"
```

`AttachmentPersistentState extends SavedData` writes all persistent level attachments into one `fabric_attachments.dat`-style SavedData (`ID = "fabric_attachments"`).

### Fit for typhoon

| Use | Fit |
|-----|-----|
| Single level-wide config / “is typhoon system enabled” | Good |
| Entire multi-storm mutable simulation state | Poor / awkward — one big mutable list attachment fights immutability + full resync design |
| Per-entity temporary wind force on player | Possible secondary use |
| Per-chunk “in typhoon rain region” cache | Possible secondary, not primary authority |

Fabric docs (“Larger Attachments”) explicitly: attachments **shine for individual values**; large composite state should be split or handled carefully because replace-triggers-full-sync.

### Pros / cons

| Pros | Cons |
|------|------|
| Less boilerplate for simple fields | Experimental API surface |
| Optional sync predicates | Mutable multi-storm state is wrong tool |
| Level attach still Survives restart | Backed by SavedData anyway — no deeper magic |
| Nice for flags/config next to storms | Harder to inspect as a dedicated `typhoon.dat` |

**Verdict:** viable as **secondary** (e.g. player-local UI flags); **not** primary storm registry.

---

## Option C — Custom `Entity`

### API facts

- `Entity.tick()` / `baseTick()` exist; world runs entity ticks only for entities in **entity-ticking** chunks (`ServerLevel.isPositionEntityTicking`, entity tick list).  
- `Entity.isAlwaysTicking()` default implementation returns **`false`** (javap).  
- Persistence: `save` / `load` / `shouldBeSaved()` — tied to chunk entity storage, not a free-form world process.  
- Fabric: `FabricEntityType` / `EntityType.Builder` for registration (`fabric-object-builder-api-v1`).

### Fit for typhoon

A storm spanning a path with width and lifecycle is closer to **raid/weather** than to a mob or projectile:

- If the entity’s chunk unloads or leaves entity-ticking range, **simulation stops** unless you force-load chunks or override always-ticking and keep a ticket — expensive and brittle for a long path.  
- “Center entity” does not naturally own **regional** queries (wind at arbitrary player positions, block scan budgets along path).  
- Client will treat it as an entity (tracking, interpolation, possible culling) which fights “weather field” semantics.  
- Command spawn/despawn is fine on entities, but SavedData + command is equally fine.

### Pros / cons

| Pros | Cons |
|------|------|
| Built-in position, UUID, some tracking | Chunk/entity ticking model is wrong for regional process |
| Automatic chunk NBT save when loaded | Unload = freeze unless always-ticking/force-load |
| Easy to “look at” in F3/entities | Wrong abstraction vs vanilla raids/weather |
| — | Heavier multiplayer tracking later |

**Verdict:** **not recommended** for authoritative typhoon state in first cut. Optional later: pure client/visual marker entity, or non-authoritative VFX — still not the save of record.

---

## Option D — Component libraries (Cardinal Components etc.)

Fabric Wiki *Global World Data* (recent): for attaching data to entities/chunks prefer **Fabric Data Attachment API**; prioritize over Cardinal Components on recent MC versions.

CCA still works in the ecosystem but is an extra dependency and overlaps Attachment. **No need for first cut** given SavedData for the storm registry.

---

## How to tick (Fabric hooks)

### Fabric lifecycle (sources in loom-cache)

```text
ServerTickEvents.START_SERVER_TICK  // MinecraftServer, head of tick before tickWorlds
ServerTickEvents.END_SERVER_TICK    // end of server tick
ServerTickEvents.START_WORLD_TICK   // ServerLevel.tick after inBlockTick flag set
ServerTickEvents.END_WORLD_TICK     // ServerLevel.tick TAIL
```

Mixin wiring (`fabric-lifecycle-events-v1`):

- World ticks injected into `ServerLevel.tick`  
- Server ticks injected into `MinecraftServer.tick` around `tickWorlds` / TAIL  

`ServerWorldEvents.LOAD` / `UNLOAD` for world open/close (LOAD javadoc explicitly mentions initializing SavedData).

### Recommended tick path for typhoon

Prefer **per-world** tick so Overworld storms only run on Overworld:

```java
ServerTickEvents.END_WORLD_TICK.register(level -> {
  if (level.dimension() != Level.OVERWORLD) return; // first cut
  TyphoonSavedData.get(level).tick(level);
});
```

- **`END_WORLD_TICK`**: good for applying knockback/wind after main world simulation; Fabric notes end tick can also start async work for next tick.  
- **`START_WORLD_TICK`**: fine if you want effects before block/entity ticks.  
- Do **not** rely on entity tick for authority.

Mirror vanilla: `Raids.tick()` is called from inside `ServerLevel.tick` once per world tick — your Fabric event is the mod-safe equivalent without mixing into `ServerLevel`.

### What `tick` should do (server)

1. Advance lifecycle / path position for each storm.  
2. Query nearby players → apply horizontal wind (separate ticket 07).  
3. Budgeted windward block damage (tickets 03/08).  
4. Regional rain influence (ticket 04) — **not** global `/weather` ownership.  
5. `setDirty()` when persisted fields change.  
6. Remove finished storms.  
7. (Later) emit S2C sync for particles/status (client is non-authoritative).

---

## Persist / load summary

| Mechanism | Where on disk (conceptually) | Load trigger | Save trigger |
|-----------|------------------------------|--------------|--------------|
| **SavedData (recommended)** | Dimension `data/<id>.dat` via `DimensionDataStorage` | First `computeIfAbsent` / world load | World save when `isDirty()` |
| Level Attachment | Same storage backend, shared `fabric_attachments` SavedData | Level construct + read NBT | Attachment dirty → SavedData dirty |
| Entity | Chunk entity lists | Chunk load | Chunk save if `shouldBeSaved()` |

Singleplayer: integrated server runs the same server save path; no special case required.

---

## Ranking for first cut

| Rank | Approach | Role |
|------|----------|------|
| **1** | **`TyphoonSavedData extends SavedData` + `ServerTickEvents.*_WORLD_TICK`** | **Primary authority, multi-storm registry, persist, tick** |
| 2 | Level `AttachmentType` (persistent Codec) | Only if state is a small immutable snapshot; optional helper flags |
| 3 | Custom Entity | Visual/marker only if ever needed; not save-of-record |
| 4 | CCA / other component libs | Skip for first cut |

### Decision rationale (short)

Vanilla already solved “multiple regional timed events per dimension” with **`Raids extends SavedData` + world tick**. A command-spawned typhoon with path, intensity profile, and lifecycle is the same class of system. Attachments are Experimental and geared to attachable field-like data; entities couple simulation to chunk entity ticking (`isAlwaysTicking` defaults false). Fabric’s world tick events are the supported hook to drive SavedData simulation without a mixin into `ServerLevel`.

---

## Concrete first-cut checklist

1. Add `TyphoonSavedData` under server-common code (`com.namimono.typhoon.…`).  
2. Factory + file id e.g. `"typhoon_storms"`.  
3. Inner `TyphoonInstance` (id, path params, wind dir, profile, age/phase) with NBT or Codec.  
4. `TyphoonSavedData.get(ServerLevel)` → `computeIfAbsent`.  
5. Register `ServerTickEvents.END_WORLD_TICK` in `ModInitializer`.  
6. Command (ticket 06) calls `get(level).spawn(...)` then `setDirty()`.  
7. Client FX: separate S2C payloads from server snapshot — not entity tracking.  
8. Call `setDirty()` whenever path progress / lifecycle / list membership changes.

---

## Citations index

1. Mojmap 1.21.1 jar: `net.minecraft.world.level.saveddata.SavedData` (+ `Factory`), `DimensionDataStorage`, `ServerLevel.getDataStorage()`, `ServerLevel` raid field/tick invoke.  
2. Mojmap 1.21.1 jar: `net.minecraft.world.entity.raid.Raids` / `Raid` — SavedData multi-event + `tick()`.  
3. Mojmap 1.21.1 jar: `Entity.isAlwaysTicking()` → `iconst_0` (false).  
4. Fabric API sources: `net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents`, `ServerWorldEvents`.  
5. Fabric API sources: `attachment.v1.*`, `AttachmentPersistentState`, `mixin.attachment.ServerWorldMixin`.  
6. Fabric docs: https://docs.fabricmc.net/develop/serialization/saved-data  
7. Fabric docs: https://docs.fabricmc.net/develop/serialization/data-attachments  
8. Fabric Wiki: https://wiki.fabricmc.net/tutorial:persistent_states (redirect), https://wiki.fabricmc.net/tutorial:global_data  
9. Project `gradle.properties`: `minecraft_version=1.21.1`, `fabric_api_version=0.116.15+1.21.1`; `build.gradle`: official Mojang mappings.
