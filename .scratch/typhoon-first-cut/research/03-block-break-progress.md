# Research: 风力破坏如何呈现挖掘进度（1.21.1 Fabric / Mojmap）

Ticket: [`.scratch/typhoon-first-cut/issues/03-block-break-progress.md`](../issues/03-block-break-progress.md)  
Target: Minecraft **1.21.1**, Fabric API `0.116.15+1.21.1`, mappings: **official Mojmap** (project `build.gradle`).  
Primary evidence: loom-mapped jars under `.gradle/loom-cache/minecraftMaven/` and protocol wiki.

---

## TL;DR

- **不需要假玩家**，也不必走 `ServerPlayerGameMode` 的真实挖掘流水线。
- 裂纹/进度是**纯客户端视觉**：服务端调用 `ServerLevel.destroyBlockProgress(breakerId, pos, stage)`，向附近玩家发 `ClientboundBlockDestructionPacket`。
- 真正破坏与掉落另调 `Level.destroyBlock(pos, true)`（或 `dropResources` + `removeBlock`）；完成后把进度清成 `-1`。
- 并发多块需要**每个同时裂开的方块一个唯一 `breakerId`**（客户端按 id 只保留一条进度）。
- Fabric API **没有**专门的 block-break-progress 封装；直接用原版 API 即可。

---

## 1. 端到端数据流

```
服务端台风逻辑（每 tick）
  → 维护每块进度 0.0..1.0（或离散 stage）
  → ServerLevel.destroyBlockProgress(id, pos, stage0to9)
       → 遍历同维度玩家
       → 跳过 entityId == id 的玩家（见下）
       → 距离平方 < 1024（约 32 格）才发包
       → new ClientboundBlockDestructionPacket(id, pos, progress)
  → ClientPacketListener.handleBlockDestruction
       → ClientLevel.destroyBlockProgress
       → LevelRenderer.destroyBlockProgress
            → 贴 destroy_stage_0..9 裂纹
  → 进度满：destroyBlockProgress(id, pos, -1) 清裂纹
            + level.destroyBlock(pos, true) 掉落并移除
            +（可选）已含 levelEvent 2001 破碎粒子/音效
```

**引用（bytecode / class）**

| 层 | Mojmap 类/方法 | 包 |
|----|----------------|-----|
| 服务端广播 | `ServerLevel.destroyBlockProgress(int, BlockPos, int)` | `net.minecraft.server.level` |
| 抽象 API | `Level.destroyBlockProgress`（abstract） | `net.minecraft.world.level` |
| 数据包 | `ClientboundBlockDestructionPacket(id, pos, progress)` | `net.minecraft.network.protocol.game` |
| 客户端收包 | `ClientPacketListener.handleBlockDestruction` | `net.minecraft.client.multiplayer` |
| 客户端渲染存储 | `LevelRenderer.destroyBlockProgress` + `BlockDestructionProgress` | `net.minecraft.client.renderer` / `net.minecraft.server.level` |
| 玩家真实挖掘对照 | `ServerPlayerGameMode.incrementDestroyProgress` | `net.minecraft.server.level` |
| 破坏+掉落 | `LevelWriter.destroyBlock` / `Level.destroyBlock` | `net.minecraft.world.level` |
| 破碎 FX | `LevelEvent.PARTICLES_DESTROY_BLOCK = 2001` | `net.minecraft.world.level.block` |

协议名（wiki）：**Set Block Destroy Stage** / `block_destruction` — 字段为 entity id、位置、stage。  
见 [Java Edition protocol/Packets — Set Block Destroy Stage](https://minecraft.wiki/w/Java_Edition_protocol/Packets)。

Yarn 同名：`destroyBlockProgress`、`ClientboundBlockDestructionPacket`、`BlockDestructionProgress`（与 Mojmap 一致）。

---

## 2. `destroyBlockProgress` 语义（服务端）

签名：

```java
// Level (abstract) / ServerLevel (impl)
public void destroyBlockProgress(int id, BlockPos pos, int progress);
```

`ServerLevel` 实现要点（javap 反汇编）：

1. 遍历 `server.getPlayerList().getPlayers()`。
2. 仅同维度（`player.level() == this`）。
3. **若 `player.getId() == id` 则跳过**——设计给「真实玩家挖掘」：挖的人不靠此包看自己的裂纹（本地 `MultiPlayerGameMode` 预测），周围人看包。
4. 距离：\(\Delta x^2+\Delta y^2+\Delta z^2 < 1024\) → 半径 **32 方块**。
5. 发包：`new ClientboundBlockDestructionPacket(id, pos, progress)`。

### 对风力破坏的含义

- 风力 **不是玩家**，应使用 **非玩家 entity id 的 `id`**（见 §5）。
- 若误用某在线玩家的 entity id 当 breaker：
  - 该玩家本人**收不到**裂纹包（被 skip）。
  - 其他人会以为是该玩家在挖。
- **不需要**实体真实存在于世界；`id` 只是客户端 `Int2ObjectMap` 的键。

---

## 3. 进度 stage 取值

### 包 / 渲染

| `progress` | 客户端行为（`LevelRenderer.destroyBlockProgress`） |
|------------|-----------------------------------------------------|
| **0–9** | 显示 `destroy_stage_0` … `destroy_stage_9` 裂纹 |
| **&lt; 0 或 ≥ 10** | 移除该 `id` 的裂纹条目（清进度） |

`BlockDestructionProgress.setProgress` 会把值 clamp 到 ≤ 10，但渲染侧对 ≥10 走删除分支。

### 原版玩家如何换算 stage

`ServerPlayerGameMode.incrementDestroyProgress`：

```text
partial = blockState.getDestroyProgress(player, level, pos) * (ticksMining + 1)
stage   = (int)(partial * 10.0f)   // 0..10+
if (stage != lastSentState)
    level.destroyBlockProgress(player.getId(), pos, stage)
```

即：内部 0.0–1.0 完成度 × 10 → 整数 stage；**仅在 stage 变化时发包**。

风力侧可：

- 自管 `float damage ∈ [0,1]`，每 tick `stage = min(9, (int)(damage * 10))`，变化才 `destroyBlockProgress`；
- 或固定时长 N tick：`stage = tick * 10 / N`。

完成时务必：

```java
level.destroyBlockProgress(breakerId, pos, -1);
```

### 客户端过期

`LevelRenderer` 每 20 tick 扫描一次：`ticks - updatedRenderTick > 400` 则丢弃条目（约 **20 秒**无更新自动消裂纹）。长时间缓慢破坏需周期性刷新同 stage 或略升 stage，否则裂纹会消失。

---

## 4. 是否必须伪装玩家挖掘？

| 路径 | 裂纹 | 掉落 | 评价 |
|------|------|------|------|
| **A. `destroyBlockProgress` + `destroyBlock(pos, true)`** | ✅ | ✅（手挖/空工具战利品表） | **推荐**。非玩家来源完全可用。 |
| B. 假 `ServerPlayer` + `ServerPlayerGameMode` | ✅（用假玩家 id） | ✅（走 player 工具逻辑） | 过重；权限/交互/advancement 副作用。 |
| C. 仅 `destroyBlock` / `removeBlock` 无进度 | ❌ 瞬间消失 | ✅ | 无「挖掘感」。 |
| D. 自定义粒子/模型 | 自定义 | 自管 | 第一刀不必要。 |

**结论：不必伪装玩家。** 进度 API 与挖掘逻辑解耦；`id` 不是「必须是玩家」，只是原版用 entity id 当槽位键。

---

## 5. breakerId 分配与多块并发

### 客户端结构

```text
LevelRenderer:
  Int2ObjectMap<BlockDestructionProgress> destroyingBlocks;  // key = id
  Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress; // key = pos.asLong()
```

- **同一 `id` 同时只能对应一个位置**（换位置会替换该 id 条目）。
- **同一位置可有多条**不同 id 的进度（`SortedSet`，按 progress/id 排序；渲染取可见裂纹）。
- 因此：**同时裂开的方块数 ≤ 同时占用的唯一 id 数**。

### 推荐 id 策略（台风）

```text
// 避免与真实实体 network id 碰撞（玩家/生物通常为正且递增）
// 使用固定负号池，例如：
static final int WIND_BREAKER_BASE = -100_000;
IntArrayList freeIds; // 池化
// 每块开始裂：id = alloc()
// 完成/取消：destroyBlockProgress(id, pos, -1); free(id)
```

注意：

- `ServerLevel` 对 `player.getId() == id` 跳过发包——负 id 不会等于正常玩家 id，**所有附近玩家（含单人主机）都能看到裂纹**。
- 池大小 ≈ 同时最大裂块数（与扫描预算票相关）；第一刀可先固定例如 64/256 个槽。
- 不要用 `0` 或随机正 id 撞实体。

### 多块性能

- 每 stage 变化 × 附近玩家：一次轻量包（VarInt + BlockPos + byte）。
- 原版仅在 stage 变化时发；风力应同样 **debounce**。
- 广播已有 32 格距离裁剪；台风核心远距离玩家自然收不到。
- 瓶颈通常在「扫哪些块 / 每 tick 破坏多少」，不在裂纹包本身。

---

## 6. 完成后掉落与方块移除

### 推荐 API

```java
// LevelWriter defaults:
// destroyBlock(pos, drop) → destroyBlock(pos, drop, null) → destroyBlock(pos, drop, entity, 512)
boolean ok = level.destroyBlock(pos, true); // drop = true, entity = null
level.destroyBlockProgress(breakerId, pos, -1);
```

`Level.destroyBlock(BlockPos, boolean, Entity, int)` 行为摘要：

1. 空气 → false。
2. 非火：`levelEvent(2001, pos, Block.getId(state))` → **破碎粒子+音效**（`LevelEvent.PARTICLES_DESTROY_BLOCK`）。
3. `drop == true` → `Block.dropResources(state, level, pos, be, entity, ItemStack.EMPTY)`。
4. `setBlock` 成流体 legacy / 空气（flags 3 + 传入 recursion 上限）。
5. `gameEvent(BLOCK_DESTROY, …)`。

### 掉落细节 / 限制

- 工具为 **`ItemStack.EMPTY`**：需要正确工具才掉的方块（石类等）可能 **不掉落**；土/木材等无要求方块正常掉。
- 第一刀若希望「风吹一律掉落物」：用 `Block.getDrops(...)` 自管，或构造假工具 stack，或 `Block.dropResources` 变体；**仍不需要假玩家**。
- 若要掉落物被风吹走：掉落后对 `ItemEntity` 施加速度（另票/实现时再定）。
- `ServerPlayerGameMode.destroyBlock` 会走 `playerWillDestroy`、工具耐久、正确工具检查——风力 **不要** 默认走这条，除非刻意模拟某玩家。

### 与进度的顺序

1. 最后一帧可发 stage 9（可选）。
2. `destroyBlock(pos, true)`（含 2001 FX）。
3. `destroyBlockProgress(id, pos, -1)`（清裂纹；块没了不清也会过期，但应显式清并释放 id）。

---

## 7. 与 `WorldEvent` / `LevelEvent` 的关系

问题里的 “WorldEvent” 在 Mojmap 1.21.1 对应 **`LevelEvent`**（不是 GameEvent）。

| 用途 | API |
|------|-----|
| 裂纹进度 | **`destroyBlockProgress` / `ClientboundBlockDestructionPacket`**（不是 LevelEvent） |
| 破碎粒子+音效 | `LevelEvent.PARTICLES_DESTROY_BLOCK` (**2001**)，`destroyBlock` 已调用 |
| 方块实体 UI 动画等 | `blockEvent` / `ClientboundBlockEventPacket`（与挖掘裂纹无关） |

不要用 LevelEvent 模拟 0–9 裂纹阶段；原版裂纹通道就是 destruction packet。

---

## 8. Fabric API

在依赖的 `fabric-api-0.116.15+1.21.1` sources 中 **未发现** 独立的 block breaking progress 辅助 API。  
实现应直接依赖原版：

- `net.minecraft.server.level.ServerLevel#destroyBlockProgress`
- `net.minecraft.world.level.LevelWriter#destroyBlock`
- 可选：`net.minecraft.world.level.block.Block#dropResources` / `getDrops`

---

## 9. 单人 vs 多人（本图范围）

- 单人集成服：主机玩家 entity id ≠ 风力 breaker 负 id → 能看到裂纹。
- 多人：现有 API 已按距离同步；权威仍在服务端。本图不展开联机方案。
- 客户端无需 typhoon 专用包即可显示原版裂纹。

---

## 10. 建议的第一刀实现草图（决策用，非提交代码）

```java
// 伪代码 — ServerLevel level, 自管 WindBreakSession
void tickCrack(WindBreakSession s) {
    s.progress = Math.min(1f, s.progress + s.ratePerTick);
    int stage = Math.min(9, (int) (s.progress * 10f));
    if (stage != s.lastStage) {
        level.destroyBlockProgress(s.breakerId, s.pos, stage);
        s.lastStage = stage;
    }
    if (s.progress >= 1f) {
        level.destroyBlock(s.pos, true);           // drops + 2001 FX
        level.destroyBlockProgress(s.breakerId, s.pos, -1);
        freeBreakerId(s.breakerId);
        // TODO: impulse ItemEntities with wind
    }
}
```

状态存在台风运行时（或区块扫描器），**不要**依赖 `ServerPlayerGameMode` 字段。

---

## 11. 限制清单（实现时注意）

1. 裂纹 **纯视觉**；不改 hardness、不触发玩家挖掘事件。
2. 每 breaker id 同时一块；并发靠 id 池。
3. 广播半径 ~32；更远看不到裂纹（破坏结果仍靠方块更新同步）。
4. ~20s 无更新客户端自动清裂纹。
5. 与玩家 id 冲突会导致该玩家看不到自己的「风裂纹」。
6. 空工具掉落规则可能吞需要稿子的掉落——产品需明确是否「总是掉」。
7. 不调用 `playerWillDestroy` / 原版 `BlockEvent.BREAK` 类玩家钩子（Fabric 的 player block break 事件也不会自然触发）；若其它模组要监听「风毁」，需自抛事件或额外集成。

---

## Sources

1. Loom Mojmap 1.21.1 classes (javap):  
   `ServerLevel.destroyBlockProgress`, `ServerPlayerGameMode.incrementDestroyProgress`,  
   `ClientboundBlockDestructionPacket`, `LevelRenderer.destroyBlockProgress`,  
   `Level.destroyBlock`, `LevelWriter.destroyBlock*`, `LevelEvent.PARTICLES_DESTROY_BLOCK`,  
   `BlockDestructionProgress`.
2. [Minecraft Wiki — Java Edition protocol/Packets (Set Block Destroy Stage)](https://minecraft.wiki/w/Java_Edition_protocol/Packets)
3. Project: `minecraft_version=1.21.1`, `fabric_api_version=0.116.15+1.21.1`, `loom.officialMojangMappings()`.
4. Yarn 1.21.1+build.3 tiny：方法名与 Mojmap 对齐（`destroyBlockProgress` 等）。

Research date: 2026-08-12.
