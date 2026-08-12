# 19 — 台风雨与风向粒子

**What to build:** 玩家在影响带内能感到台风雨随局部强度变化，风眼里雨停；不修改原版全局天气命令状态。粒子表现主风向，使人不看 UI 也能感到风从哪来。服务端以台风几何/强度同步为据，客户端负责雨与粒子表现。

**Blocked by:** 15 — 命令生成可存档台风与信息面

**Status:** ready-for-human

- [x] 影响带内、局部强度>0 时有台风雨表现；风眼（强度 0）停雨
- [x] 不抢写原版 `/weather` 全局天气权威
- [x] 粒子能表现当前主风向（含过眼反转后可感知变化）
- [x] 不做湿润/作物逻辑；无雷达 UI

## Comments

### 2026-08-12 实现子代理

- **Commits:** `e157b6d`（台风雨接缝 + S2C 同步 + 客户端雨/风向粒子）；`223f68872f97211a94c1f0d7bc9b58930e853e24`（票状态 ready-for-human + 自审小清理）
- **测试:** `./gradlew test` 全绿（49 tests：含 TyphoonRainFeel 4；其余既有场/风力/抗风/破坏/NBT/命令反馈）
- **接缝:**
  - 领域：`TyphoonRainFeel` / `TyphoonRainEffect`（局部强度>0 下雨，雨强=强度/6；风眼/带外停雨；风向取自采样含过眼反转与覆盖）
  - 同步：`TyphoonSyncPayload` + `TyphoonSync`（快照 S2C；JOIN/tick/spawn/clear；不调用 setWeather / rainLevel）
  - 客户端：`TyphoonClientState` + `TyphoonClientWeather`（RAIN + CLOUD 风向粒子 + WEATHER_RAIN 音效叠加）
- **审查（相对 `c33279c`，Standards + Spec 双轴自审；子代理环境未再派生子代理）：**
  - **Standards:** 无仓库级 CODING_STANDARDS。领域/适配分层清晰。判断性：每 tick 全量快照同步（单人第一刀可接受）；粒子密度魔法数。已去掉未用 `hasStorms()`。无明显硬违规。
  - **Spec:** 票19清单与决议04/spec 台风雨段均覆盖：带内强度>0 有雨、风眼停雨、不抢 `/weather`、粒子表现主风向含反转、无湿润/作物/雷达、服务端同步几何/强度、客户端负责表现。未越界做掉落物吹走（票18）。
  - 结论：可交人工验收（粒子/音效体感需手工）。
