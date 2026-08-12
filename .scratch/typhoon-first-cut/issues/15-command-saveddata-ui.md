# 15 — 命令生成可存档台风与信息面

**What to build:** 玩家可用 `/typhoon spawn|clear|list` 生成、清除、查看台风；台风写入服务端 SavedData 并随世界 tick 推进；聊天给出 spawn/list 反馈；Boss 栏显示名称、局部强度/峰值与路径进度。默认参数（峰值 6、半宽 16 区块、时长半天等）按规格生效。

**Blocked by:** 14 — 台风场核心：推进与采样

**Status:** ready-for-human

- [x] `spawn` 接受起终点及可选峰值/宽度（区块）/时长/名称/风向覆盖，缺省值符合规格
- [x] `clear` 清除当前维度台风；`list` 列出 id/名称/进度
- [x] 台风经 SavedData 保存加载后仍在，且继续推进
- [x] 进行中 Boss 栏显示名称与局部/峰值；进度条反映路径进度；结束后清除
- [x] 权限约作弊 2 级；不实现改参/转向/暂停子命令

## Comments

### 2026-08-12 实现子代理

- **Commits:** `61001b7`（实现）；本提交更新票状态
- **测试:** `./gradlew test` 全绿（16 tests：TyphoonField 7 + TyphoonFields 7 + Nbt 1 + CommandsFeedback 1）
- **接缝:**
  - 领域：`TyphoonFields` / `TyphoonSpawnRequest` / `TyphoonRecord` / `TyphoonDisplay`（复用票14 `TyphoonField`）
  - 适配：`TyphoonSavedData` + `TyphoonNbt`、`TyphoonCommands`、`TyphoonBossBars`；`ServerTickEvents.END_WORLD_TICK`
- **审查（相对 `452cead`，Standards + Spec 双轴自审；子代理环境未再派生子代理）：**
  - **Standards:** 无仓库级 CODING_STANDARDS。适配与领域分层清晰。判断性：`TyphoonCommands.buildSpawn` 嵌套 optional 参数链偏长（Brigadier 惯例）；Boss 栏静态 `Map` 按维度跟踪（单人优先可接受）。无明显硬违规。
  - **Spec:** 票15清单与决议06/02/12均覆盖：缺省峰值6/半宽16区块/12000tick/默认名、SavedData id `typhoon_storms`、权限 LEVEL_GAMEMASTERS(2)、Boss 栏名称+局部/峰值与路径进度、多场只跟最近一场、无改参/转向/暂停。NBT/快照往返有单测；命令与 Boss 栏属适配层，按规格可手工验收。
  - 结论：可交人工验收。
