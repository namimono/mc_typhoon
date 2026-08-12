# 14 — 台风场核心：推进与采样

**What to build:** 台风场接缝能按路径与时长推进台风中心，并对任意坐标给出是否在影响带、局部强度、主风向（含过眼自动反转与手动覆盖优先）、路径进度；强度剖面按已定对称比例（风眼 / 眼墙 / 斜坡）。用测试证明风眼为 0、带外无影响、过眼风向翻转等行为，不依赖完整游戏客户端。

**Blocked by:** None

**Status:** ready-for-human

- [x] 给定起点终点、峰值、影响宽度、时长，可 tick 推进中心并报告路径进度
- [x] 相对中心的 `|s|` 分段采样符合：风眼 0～0.05L=0、眼墙至 0.15L=峰值、斜坡至 0.45L 线性到 0
- [x] 默认主风向随过眼反转；存在覆盖时整场固定为覆盖方向
- [x] 距路径超过影响宽度（或剖面为 0 的更远处）采样为不受影响 / 局部强度 0
- [x] 上述行为有 JUnit 覆盖，且只通过台风场接缝断言

## Comments

### 2026-08-12 实现子代理

- **Commits:** `d7ab2aa`（接缝实现 + JUnit）；`a79a810`（死字段清理 + 票状态 ready-for-human）
- **测试:** `./gradlew test --tests com.namimono.typhoon.field.TyphoonFieldTest` 与全量 `./gradlew test` 均通过（7 tests）
- **接缝:** `com.namimono.typhoon.field.TyphoonField` / `TyphoonSample`（纯逻辑，无 MC 依赖）
- **审查（相对 `ce7a397`）:**
  - **Standards:** 无仓库级 CODING_STANDARDS；清理未用 `endX`/`endZ` 字段后无明显硬违规。判断性：`create` 参数较多、风向覆盖用一对 `Double`（Primitive Obsession / Data Clumps 倾向），第一刀可接受。
  - **Spec:** 票 14 清单与决议 11 剖面/过眼反转/覆盖优先均已覆盖；未越界实现命令/SavedData/玩家风力/破坏/雨粒子。
  - 结论：可交人工验收。
