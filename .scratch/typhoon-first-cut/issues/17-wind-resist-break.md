# 17 — 抗风解析与迎风风力破坏

**What to build:** 方块抗风按例外 tag + hardness 分箱（含免疫与不参与）；迎风面用轴对齐同列遮挡判定；局部强度超过抗风时出现挖掘式裂纹并最终掉落；扫描限在玩家附近窗口与每 tick 预算内；超额驱动渐裂节奏；强度不够则清进度。平地不特判、顶面不算迎风。

**Blocked by:** 15 — 命令生成可存档台风与信息面

**Status:** ready-for-human

- [x] 抗风解析：叶/玻璃=1 … 铁=5、钻石与下界合金块=6；黑曜石族与基岩等免疫；流体/空气等不参与
- [x] 迎风/背风判定符合同列水平遮挡；背风不优先破坏
- [x] 可见裂纹进度并在完成后掉落；使用非玩家破坏进度手段（负 id 池等已决议方案）
- [x] 扫描范围与预算、并发裂纹上限符合规格量级
- [x] 超额速率量级可感（渐裂）；超额≤0 时清除进度
- [x] 抗风与迎风/进度步进尽量经台风场接缝或可注入探测测到

## Comments

### 2026-08-12 实现子代理

- **Commits:** `f180659`（抗风/迎风/破坏引擎 + 适配层 + tag + 单测）；本提交（票状态 ready-for-human）
- **测试:** `./gradlew test` 全绿（45 tests：Field 7 + Fields 7 + PlayerWind 9 + Nbt 1 + CommandsFeedback 1 + WindResistance 6 + WindwardFace 6 + WindBreakEngine 8）
- **接缝:**
  - 领域：`WindResistance` / `WindBlockFacts`（例外 tag → hardness 分箱；免疫/不参与）；`WindwardFace` + `SolidProbe`（主轴同列遮挡）；`WindBreakEngine` / `WindBreakWorld` / `WindBreakAction`（玩家窗 ±32/±16、预算 96、并发 24、负 id 池、超额渐裂与清进度）
  - 适配：`ServerWindBreakWorld` + `WindBreakApplier`（`destroyBlockProgress` / `destroyBlock`）；`data/typhoon/tags/block/wind_resist_*` + `wind_immune`
  - 挂在 `Typhoon` END_WORLD_TICK（跟踪中的一场）
- **审查（相对 `3d2921c`，Standards + Spec 双轴自审；子代理环境未再派生子代理）：**
  - **Standards:** 无仓库级 CODING_STANDARDS。领域/适配分层清晰，探测可注入。判断性：`debugSeedCrack` 测试钩；坐标用原始 int 避免领域依赖 MC `BlockPos`；扫描按曼哈顿近距轮转（规格未钉死遍历序）。无明显硬违规。
  - **Spec:** 票17清单与决议 03/05/08、ADR-0001 均覆盖：抗风例外+分箱+免疫/不参与、迎风同列遮挡与背风不破、负 id 裂纹与 destroy 掉落、扫描窗/预算/并发量级、超额速率与 ≤0/风眼清进度。未越界实现掉落物吹走/撞击或台风雨。
  - 结论：可交人工验收。
