# 16 — 玩家风力体感

**What to build:** 生存/冒险玩家在影响带内按脚下局部强度受到风力：1 轻偏，2–3 顺逆变速，4 禁冲刺，5 跳偏，6 力学地狱（强抬升+猛吹，风不直接扣血）。创造且可飞行或旁观时关闭玩家风力。主风向（含过眼反转）作用在推力方向上。

**Blocked by:** 15 — 命令生成可存档台风与信息面

**Status:** ready-for-human

- [x] 局部强度 0 / 带外：无玩家风力
- [x] 1–6 档效果符合规格挂载表（含 4 禁冲刺、6 力学地狱且不直接扣血）
- [x] 推力方向跟随当前主风向（过眼反转可感知）
- [x] 创造可飞与旁观不受玩家风力
- [x] 台风场/相关逻辑有测或可演示验收上述档位差异

## Comments

### 2026-08-12 实现子代理

- **Commits:** `6ec1b86`（玩家风力接缝 + 适配层 + 单测）；`dca2964`（票状态 ready-for-human + 适配层小清理）
- **测试:** `./gradlew test` 全绿（25 tests：TyphoonField 7 + TyphoonFields 7 + PlayerWindFeel 9 + Nbt 1 + CommandsFeedback 1）
- **接缝:**
  - 领域：`PlayerWindFeel` / `PlayerWindEffect`（由 `TyphoonSample` 局部强度 floor 档 + 资格派生；顺逆风倍率 ±20%/±40%）
  - 适配：`PlayerWindApplier`（世界 tick：推力/抬升、MOVEMENT_SPEED 暂态修正、禁冲刺、离地跳偏）；挂在 `Typhoon` END_WORLD_TICK
- **审查（相对 `a2c5aed`，Standards + Spec 双轴自审；子代理环境未再派生子代理）：**
  - **Standards:** 无仓库级 CODING_STANDARDS。领域/适配分层清晰。判断性：`HORIZONTAL_PUSH` 魔法数表（体感刻度，可接受）；跳偏用「离地放大推力」近似跳跃轨迹。无明显硬违规。
  - **Spec:** 票16清单与决议07挂载表均覆盖：0/带外关闭、1轻偏、2–3顺逆变速、4禁冲刺、5跳偏、6力学地狱且 `dealsDirectDamage()==false`、创造可飞/旁观关闭、推力跟主风向含过眼反转。未越界实现抗风破坏/掉落物/台风雨。
  - 结论：可交人工验收。
