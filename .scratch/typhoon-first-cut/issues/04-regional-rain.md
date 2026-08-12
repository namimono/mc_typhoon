# 区域降雨且不抢全局天气

Type: research
Status: resolved

## Question

第一刀要在台风影响带内表现降雨，风眼内停雨、略亮，且不抢原版全局 `/weather`。在 1.21.1 Fabric 上，客户端雨粒子/天空/潮湿感有哪些可叠加或局部覆盖的做法？哪些必须 mixin？给出可行路径与风险。

## Answer

### 结论（第一刀推荐路径）

**服务器只维护台风几何/强度，绝不碰 `setWeather` / `rainGradient` / 天气 S2C 包。客户端用叠加 FX 画“台风雨”，风眼用 strength=0 停雨 + 可选天空略亮。**

详细依据与 API 表见：[`research/04-regional-rain.md`](../research/04-regional-rain.md)

### 原版事实（为何不能“假装全局下雨”）

- 天气是**维度级标量** `World.getRainGradient`；`isRaining()` 全局；`hasRain(pos)` 才是位置门（天空可见 + 高度图 + 群系降水类型）。
- 客户端雨帘/溅射/雨声全在 `WorldRenderer.renderWeather` + `tickRainSplashing`，入口先看全局 gradient。
- `/weather` 经 `GameStateChangeS2CPacket`（START/STOP/RAIN_LEVEL_CHANGE）写客户端 gradient——抢它就抢全局。
- **视觉雨 ≠ 真天气**：耕地/炼药锅/`Entity.isInRain` 都走 `hasRain`，第一刀可不做。

### 可选做法

| 做法 | 不抢 `/weather`？ | 风眼 | Mixin | 第一刀 |
|------|-------------------|------|-------|--------|
| **A. 客户端叠加** 粒子 + 雨声 + 可选天空染色（由台风 state 采样 strength） | ✅ | strength=0 | 天空可选 | **推荐** |
| B. 临时改 `rainGradient` | ❌ | 冲突 | 轻 | 否决 |
| C. 改 `hasRain`/`isRaining` 做空间真天气 | 半 | 可 | 重 | 延后 |
| D. `DimensionRenderingRegistry` 整维天气替换 | 仅当自管全 Overworld | 可 | Fabric 接管 | 过重 |
| E. Mixin 雨帘/溅射按列跳过风眼 | ✅ | 可压掉全局雨 | 中 | 可选加固 |

Fabric **没有**区域降雨 API；`registerWeatherRenderer` 是**整维度**替换，不适合作带状台风。

### 第一刀切分

**Server**

- 台风权威状态（路径/宽度/风眼/强度剖面）；同步到客户端。
- 不调用天气 API，不发 RAIN_LEVEL_*。

**Client（主路径，几乎无 mixin）**

```
strength(xz) = 0 外圈 | 0 风眼 | f(强度) 影响带
strength>0 → 在相机附近 spawn ParticleTypes.RAIN + WEATHER_RAIN 音效
strength=0 → 不生成、停台风雨声
```

**可选 mixin（体感，非必须起步）**

- `ClientWorld.getSkyColor` / `getSkyBrightness`：带内略暗、风眼相对略亮（**不要**写 `setRainGradient`）。
- 若要求「全局 `/weather rain` 时风眼里也停雨帘」：再 mixin `renderWeather` / `tickRainSplashing` 按列 `isEye` 跳过。

**明确不做（第一刀）**

- 潮湿感 / `isInRain` / 灭火
- 耕地浇水、炼药锅接水
- 雷暴闪电
- 完整复刻原版雨帘（可后续打磨）

### 风险（摘要）

- 全局雨 + 台风带：粒子可能叠一层 → 全局已下雨时降低叠加密度。
- 全局雨 + 风眼：无 cancel mixin 时风眼仍见原版雨 → 产品接受或加 E。
- Sodium/Iris/Particle Rain：优先叠加与 Fabric 事件，少改 `renderWeather` 主体。
- 沙漠 `hasPrecipitation=false`：建议台风雨**强制显示**（可读性）。

### 必须 mixin？

| 需求 | 必须？ |
|------|--------|
| 带内雨粒子 + 雨声 | **否** |
| 风眼停**台风**雨 | **否** |
| 风眼停**原版**雨帘 | 是（若硬需求） |
| 天空略暗/略亮 | 是（小） |
| 真潮湿/作物 | 是（延后） |
