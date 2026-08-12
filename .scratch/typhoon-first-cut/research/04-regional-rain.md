# Research: Regional rain without hijacking global `/weather` (Fabric 1.21.1)

Ticket: [issues/04-regional-rain.md](../issues/04-regional-rain.md)  
MC: Fabric / yarn 1.21.1 · Fabric API 0.116.x  
Names below use **Yarn** (Fabric default). Mojang equivalents noted where useful.

---

## 1. How vanilla weather actually works

### 1.1 Global scalar, not a field

Weather is **one pair of floats per world dimension**, not a spatial field:

| Yarn | Mojang | Role |
|------|--------|------|
| `World.rainGradient` / `getRainGradient(delta)` | `Level.rainLevel` / `getRainLevel` | 0..1 intensity, lerped |
| `World.thunderGradient` | `Level.thunderLevel` | storm darkness/lightning |
| `World.isRaining()` | `Level.isRaining()` | `getRainGradient(1) > 0.2` |
| `World.hasRain(BlockPos)` | `Level.isRainingAt(BlockPos)` | **position-aware gate** for gameplay |

`isRaining()` is **global**. Position only enters at `hasRain(pos)`:

```
hasRain(pos) =
  isRaining()
  && canSeeSky(pos)
  && heightmap(MOTION_BLOCKING, pos).y <= pos.y
  && biome.getPrecipitation(pos) == RAIN
```

Bytecode source: mapped `Level.isRaining` / `isRainingAt` in local loom 1.21.1 jar.

### 1.2 Biome precipitation (local type, not local intensity)

`Biome.hasPrecipitation()` / `getPrecipitation(pos)` → `NONE | RAIN | SNOW` from climate + temperature.  
This only chooses **rain vs snow vs none** per column; intensity still comes from the global gradient.

### 1.3 Server authority + packet path

Server drives timers via `ServerWorld.setWeather` / weather cycle (`ServerLevel.setWeatherParameters`).  
Client is updated with `GameStateChangeS2CPacket` / `ClientboundGameEventPacket`:

- `START_RAINING` / `STOP_RAINING` → `ClientWorld.setRainGradient(1|0)`
- `RAIN_LEVEL_CHANGE` / `THUNDER_LEVEL_CHANGE` → set gradients to param

**`/weather` is this path.** Touching it steals the global dial for the whole Overworld.

### 1.4 Client rendering surface (`WorldRenderer` = Mojang `LevelRenderer`)

| Method | Gated by | Local checks |
|--------|----------|--------------|
| `renderWeather` (private; Mojang `renderSnowAndRain`) | early-out if `getRainGradient(tickDelta) <= 0` | per-column `Biome.hasPrecipitation` / `getPrecipitation` (rain vs snow tex) |
| `tickRainSplashing` (Mojang `tickRain`) | same global gradient | per splash pos: precip == RAIN; spawns `ParticleTypes.RAIN` / SMOKE; plays `WEATHER_RAIN` / `WEATHER_RAIN_ABOVE` |
| `ClientWorld.getSkyColor` / `getSkyBrightness` / `getCloudsColor` | multiplies darkness by `getRainGradient` | camera-centric, still **global** scalar |
| `BackgroundRenderer` / `FogRenderer` | uses `getRainGradient` | global fog thicken |

Rain texture: `textures/environment/rain.png` (`WorldRenderer.RAIN`).

### 1.5 “Wetness” and true weather side-effects

| Effect | Path | Needs global rain? |
|--------|------|--------------------|
| Entity wet / fire extinguish in rain | `Entity.isInRain()` → `world.hasRain(pos)` | **yes** (`isRaining()` first) |
| Farmland moisture | `FarmlandBlock` → `isRainingAt` | yes |
| Cauldron fill | precipitation tick → rain at pos | yes |
| Skeleton burn rules, etc. | various `isRaining` / `hasRain` | yes |
| Visual sheets + splash + rain SFX | `WorldRenderer` only | gradient > 0 |

**Visual rain ≠ gameplay weather.** First cut can deliver the former without the latter.

---

## 2. Fabric API: what helps, what doesn’t

### 2.1 `DimensionRenderingRegistry.registerWeatherRenderer` ([Fabric API docs](https://maven.fabricmc.net/docs/fabric-api-0.116.0+1.21.1/net/fabricmc/fabric/api/client/rendering/v1/DimensionRenderingRegistry.html))

- Registers a **full replacement** for that **dimension’s** weather pass.
- Hooked from Fabric’s `WorldRendererMixin`: `@Inject HEAD renderWeather`, cancel vanilla if renderer present.
- **Wrong granularity** for typhoon bands inside the Overworld: you either own all Overworld rain or none. Only useful if you reimplement vanilla + typhoon together and accept ownership of the whole pass.

Same story for `registerSkyRenderer` / `registerCloudRenderer` — dimension-wide overrides, not regional.

### 2.2 `WorldRenderEvents` (START / AFTER_ENTITIES / AFTER_TRANSLUCENT / LAST / END)

- Good for **additive** custom geometry/particles in world space.
- **Does not** cancel or modify vanilla `renderWeather`.
- Safe compose path for overlay rain.

### 2.3 No Fabric “regional rain” or `isRainingAt` event

There is no Fabric event that changes weather at a position. Anything spatial is custom state + mixin or pure client FX.

---

## 3. Option matrix (visual rain vs true weather)

| Approach | Regional? | Leaves `/weather` alone? | Eye clear? | Mixin? | Risk |
|----------|-----------|---------------------------|------------|--------|------|
| **A. Client overlay** (particles + SFX + optional sky tint from typhoon state) | yes | **yes** | yes (just don’t spawn) | optional (sky only) | Look slightly “mod-particle” vs vanilla sheets; no wetness |
| **B. Soft set `rainGradient` while player near typhoon** | fake-local | **no** (steals dial) | conflicts with global rain | no/light | Breaks `/weather` feel, eye can’t clear if global rain, multiplayer lies |
| **C. Mixin `hasRain` / `isRaining` / gradients for spatial weather** | yes | partial | possible | **heavy** | High conflict (weather mods, cauldrons, farms); hard to debug |
| **D. Dimension weather renderer for Overworld** | only if you reimplement | only if you proxy vanilla | possible | Fabric API (own mixin surface) | High ownership cost; Sodium/Iris weather interactions |
| **E. Mixin `renderWeather` + `tickRain` columns** only | yes (visual) | yes | suppress columns in eye | **medium** | Fragile inject points; shader packs; still no wetness |
| **A+E hybrid** | yes | yes | best of both | medium | Recommended if global rain + eye must both work |

**Map decision forbids B** (“雨：台风区域驱动，不抢原版全局 `/weather`”).

---

## 4. Recommended first-cut path

### Goal (this ticket’s slice)

- In **typhoon influence band**: rain **look + sound**.
- In **eye**: stop typhoon rain, sky **slightly brighter** than band.
- Outside typhoon: vanilla weather fully intact.
- **Out of first-cut scope**: crop irrigation, cauldron fill, entity `isInRain` wetness, lightning.

### 4.1 Server (not visual)

- Own typhoon state (center / path / width / intensity profile / eye radius) — same storage ticket as 02.
- **Never** call `ServerWorld.setWeather`, never push `RAIN_LEVEL_CHANGE` for typhoon.
- Sync compact typhoon snapshot to client (singleplayer integrated server → client receiver). Client needs geometry to paint rain.

### 4.2 Client — primary (mostly no mixin)

Implement `TyphoonClientWeather` driven by synced state:

```
strength(cameraXZ) =
  0  outside band
  0  inside eye
  f(radial/path intensity) in band   // 0..1
```

| Layer | How | Mixin? |
|-------|-----|--------|
| Splash / falling rain | `ClientTickEvents.END_CLIENT_TICK`: if `strength > 0`, spawn `ParticleTypes.RAIN` (and optional smoke on hot blocks) in a radius around camera, mimicking `tickRain` density curve | **no** |
| Ambient rain SFX | looping / periodic `world.playSound(..., SoundEvents.WEATHER_RAIN[_ABOVE], SoundCategory.WEATHER, ...)` scaled by strength; stop in eye | **no** |
| Sheet rain (vanilla look) | optional later: copy subset of `renderWeather` quads via `WorldRenderEvents.LAST` or private invoke | optional |
| Sky slightly darker in band / brighter in eye | `ModifyReturnValue` on `ClientWorld.getSkyColor` / `getSkyBrightness` using **camera** strength — **do not** write `rainGradient` | **yes, small** |
| Fog “damp” feel | optional `BackgroundRenderer` / fog color mixin, camera-local | optional |

**Coexistence with vanilla rain**

- When global `getRainGradient() > 0` **and** camera in typhoon **eye**: vanilla will still draw rain unless suppressed.
- First cut **accept** that rare edge (global storm + standing in eye still sees vanilla rain), **or** add light mixin:

```
@Inject renderWeather / tickRain:
  for each column / splash sample:
    if typhoon.isEye(columnXZ) → skip
    // optional: if typhoon.band && !globalRain → already handled by our particles
```

Suppress-only-in-eye keeps `/weather` valid everywhere else.

### 4.3 What must be mixin (if at all)

| Need | Must mixin? | Target (Yarn) |
|------|-------------|----------------|
| Band rain particles + sound | **No** | client tick + particle API |
| Eye stop **typhoon** rain | **No** | strength==0 |
| Eye stop **vanilla** rain sheets/splash | **Yes** (if required) | `WorldRenderer.renderWeather`, `tickRainSplashing` |
| Sky dim/bright without global gradient | **Yes** (for “略亮”) | `ClientWorld.getSkyColor`, `getSkyBrightness` (optional `getCloudsColor`) |
| True wetness / crops | **Yes**, later | `World.hasRain`, maybe `isRaining` — **not first cut** |
| Replace all Overworld weather | Fabric weather renderer | dimension-wide — **avoid** |

### 4.4 Client-only vs server summary

| Concern | Side |
|---------|------|
| Typhoon existence, path, intensity | **Server** (authority) |
| Sync state | Server → client packet / attachment |
| Rain particles, rain SFX, sky tint | **Client** |
| Vanilla `rainGradient` / `/weather` | **untouched** |
| Farmland / cauldron / `isInRain` | deferred; would need server-side `hasRain` mixin or custom systems |

---

## 5. Implementation sketch (first cut)

```
server: TyphoonInstance { path, width, eyeRadius, intensityProfile, rainStrengthScale }
client: TyphoonClientCache  // last synced instance(s)

each client tick:
  s = sampleRainStrength(player/camera pos)
  if s > 0:
    spawn N rain particles near camera (N ∝ s * particle setting)
    ensure rain ambient sound volume ∝ s
  else:
    fade/stop typhoon rain sound

optional mixin ClientWorld.getSkyColor:
  if s_band > 0: darken toward gray by k*s
  if in_eye: lighten slightly vs band (or vs vanilla clear)

optional mixin WorldRenderer.renderWeather HEAD/loop:
  skip columns in eye (only when global raining)
```

Density reference: vanilla `tickRain` uses ~`100 * rainLevel²` samples (fancy/graphics adjust), biome must be RAIN, heightmap near camera ±10.

---

## 6. Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Double rain (typhoon particles + vanilla sheets) when `/weather rain` + band | low–med | Cap particle rate when `getRainGradient()>0`; or only run overlay when global clear |
| Eye still rains under global storm | med | Eye-column cancel mixin; document if deferred |
| Sodium / Iris / Particle Rain mods | med | Prefer additive particles + Fabric events over rewriting `renderWeather`; test with common packs |
| Writing `setRainGradient` “just for looks” | high (map violation) | **Never**; packets from server will fight you |
| Sky mixin fights other atmosphere mods | low–med | Small ModifyReturnValue; config toggle |
| Performance (particle spam) | low | Clamp by `ParticlesMode`, distance, strength |
| Desert biomes (`hasPrecipitation=false`) | low | First cut: force typhoon rain anyway (storm ignores desert dryness) or respect biome — product choice; recommend **force rain in band** for readability |
| Multiplayer desync | out of scope | singleplayer first; same packet design later |

---

## 7. Explicit first-cut scope cut

**In**

- Regional **visual** rain in influence band  
- Eye: no typhoon rain FX, slight sky brighten  
- Rain SFX  
- Zero interaction with `/weather` timers/packets  

**Out**

- `World.hasRain` / entity wetness  
- Crop / cauldron / fire-in-rain gameplay from typhoon  
- Thunder / lightning for typhoon  
- Full vanilla rain-sheet reimplementation (nice-to-have polish)  
- Dimension-wide weather renderer  

---

## 8. Citations / primary sources

1. Yarn 1.21.1 `World`: `getRainGradient`, `isRaining`, `hasRain`, `setRainGradient` — [maven.fabricmc.net yarn-1.21.1 World](https://maven.fabricmc.net/docs/yarn-1.21.1+build.1/net/minecraft/world/World.html)  
2. Yarn 1.21.1 `WorldRenderer`: `renderWeather`, `tickRainSplashing`, `RAIN` texture — [WorldRenderer](https://maven.fabricmc.net/docs/yarn-1.21.1+build.1/net/minecraft/client/render/WorldRenderer.html)  
3. Yarn 1.21.1 `ClientWorld`: `getSkyColor`, `getSkyBrightness`, `getCloudsColor`  
4. Local decompile (loom mapped 1.21.1): `Level.isRaining` / `isRainingAt`, `LevelRenderer.renderSnowAndRain` / `tickRain`, `Biome.getPrecipitationAt`, `Entity.isInRain`  
5. Fabric API `DimensionRenderingRegistry` + `WorldRendererMixin.renderWeather` cancel hook — [DimensionRenderingRegistry 0.116](https://maven.fabricmc.net/docs/fabric-api-0.116.0+1.21.1/net/fabricmc/fabric/api/client/rendering/v1/DimensionRenderingRegistry.html); fabric-rendering-v1 sources in project loom cache  
6. Weather sync: `ClientboundGameEventPacket` / `GameStateChangeS2CPacket` `START_RAINING`, `STOP_RAINING`, `RAIN_LEVEL_CHANGE`  

---

## 9. Decision one-liner

**First cut = server typhoon state + client overlay (particles + SFX) + optional tiny sky mixins; never touch global rain gradient. Add eye-column vanilla cancel only if global-storm-in-eye must look clear.**
