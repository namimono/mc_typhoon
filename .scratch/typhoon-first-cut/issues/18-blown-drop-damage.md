# 18 — 掉落物吹走与撞击伤害

**What to build:** 影响带内、玩家附近的掉落物被主风向推动并随强度轻度抬升；达到水平速率门槛后撞到生存/冒险玩家或其他生物造成伤害（底伤×局部强度倍率、同对冷却、护甲减免）；创造可飞/旁观免疫；不改原版 despawn、不打建筑、不做独立碎片实体。

**Blocked by:** 17 — 抗风解析与迎风风力破坏

**Status:** ready-for-human

- [x] 终端速度随局部强度约 3/8/14 格/秒量级；有轻度抬升
- [x] 施力有窗口与每 tick 数量预算，不改原版 despawn，保留 merge
- [x] 水平速率 ≥ 5 格/秒才可致伤；公式与 1 秒同对冷却符合规格
- [x] 创造可飞/旁观不受撞击伤害；护甲可减免
- [x] 不做碎片打方块；撞后掉落物不因此消失

## Comments

### 2026-08-12 实现子代理

- **Commits:** `1f800d6`（掉落物运动/撞击接缝 + 适配层 + 单测）；`672c39e`（抬升冲量微调）；`54bf66a`（票状态 ready-for-human + 轮转光标修正）
- **测试:** `./gradlew test` 全绿（60 tests；新增 BlownDropMotion 6 + BlownDropImpact 5）
- **接缝:**
  - 领域：`BlownDropMotion` / `BlownDropMotionEffect`（终端 1→3、3→8、6→14；轻度抬升；~15 tick 指数逼近；预算 48、玩家窗 ±32/±16）；`BlownDropImpact` / `BlownDropImpactCooldown`（≥5 格/秒门槛；伤害 2×clamp(强度/3,0.5–2)；同对 20 tick；创造可飞/旁观免疫）
  - 适配：`BlownDropApplier`（世界 tick：轮转施力、AABB 重叠结算 `damageSources().generic()` 吃护甲、撞后不 discard）；会话态挂在 `TyphoonSavedData`
  - 挂在 `Typhoon` END_WORLD_TICK（跟踪中的一场）
- **审查（相对 `bcbbebe`，Standards + Spec 双轴自审；子代理环境未再派生子代理）：**
  - **Standards:** 无仓库级 CODING_STANDARDS。领域/适配分层清晰，可测接缝与票16/17一致。判断性：`stepTowardTerminal` 返回 `double[]`（可用小 record，可接受）；`liftImpulseBlocksPerSecond` 与目标抬升同值偏薄封装；抬升锚点为手感量，规格只要求「轻度」。无明显硬违规。
  - **Spec:** 票18清单与决议 09/10、spec 掉落物段均覆盖：终端速度锚点与抬升、玩家窗+预算、不改 despawn/保留 merge、≥5 致伤与公式/冷却、创造可飞/旁观免疫、generic 可护甲伤害、无碎片/不打建筑、撞后不消失。未越界改台风雨或其它票。
  - 结论：可交人工验收。
