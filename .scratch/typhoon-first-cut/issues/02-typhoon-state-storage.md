# 台风状态存哪、怎么 tick

Type: research
Status: resolved

## Question

在 Fabric + Minecraft 1.21.1 下，单人优先的第一刀：一场「命令生成的台风」（中心/路径位置、主风向、强度剖面参数、生命周期）应存在哪里、如何随世界 tick、如何随存档保存加载？对比 SavedData / Attachment / 自定义 Entity 等可行方案，给出推荐与依据（官方文档或 yarn/mojang 映射下的源码事实）。

## Answer

**推荐：服务端 `SavedData`（Mojmap；Yarn 名 `PersistentState`）作为权威状态 + `ServerTickEvents` 世界 tick 驱动。**

### Where to store

- 实现 `TyphoonSavedData extends net.minecraft.world.level.saveddata.SavedData`。
- 挂在对应维度的 `ServerLevel.getDataStorage()`（`DimensionDataStorage`；Yarn: `PersistentStateManager`），文件 id 建议 `typhoon_storms` → 世界维度 `data/typhoon_storms.dat`。
- 容器内用 `List`/`Map` 存多场 `TyphoonInstance`（路径进度、主风向、强度剖面参数、生命周期）。**对齐原版 `Raids extends SavedData` + 多个 `Raid` 记录**，不是 Entity。
- 变更时调用 `setDirty()`（Yarn: `markDirty()`），否则存档不写盘。

### How to tick

- 在 `ModInitializer` 注册  
  `ServerTickEvents.END_WORLD_TICK`（或 `START_WORLD_TICK`）  
  → `TyphoonSavedData.get(level).tick(level)`。  
- 仅处理目标维度（第一刀 Overworld）。  
- **不要**依赖 Entity 的 chunk entity-ticking；`Entity.isAlwaysTicking()` 默认 `false`，远距离路径会停。

### How to persist

- `SavedData.Factory` + `computeIfAbsent(factory, id)` 负责创建/从 NBT 加载。  
- 世界保存时 `DimensionDataStorage` 写出 dirty 的 SavedData。单人 integrated server 同路径，无需特例。

### Alternatives (ranked)

| Rank | Approach | Verdict |
|------|----------|---------|
| 1 | **SavedData + world tick** | **第一刀主方案**；稳定、可多场台风、与 `Raids` 同型 |
| 2 | Fabric Attachment on `ServerLevel` | 可用但次优：API 标 `@Experimental`；level 持久化底层仍是 SavedData（`fabric_attachments`）；适合小字段/flag，不适合可变多风暴仿真 |
| 3 | Custom Entity | 不推荐作权威状态：受 chunk 实体 tick 约束，区域过程语义错误 |
| 4 | CCA 等组件库 | 第一刀不需要；Wiki 亦优先 Attachment 而非 CCA |

### Key class names (1.21.1 Mojmap / this repo)

- `SavedData`, `SavedData.Factory`, `DimensionDataStorage`, `ServerLevel#getDataStorage`
- Tick: `net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents`
- Optional load hook: `ServerWorldEvents.LOAD`
- Attachment (if used later): `AttachmentRegistry` / `AttachmentType` / `AttachmentTarget`

### Full writeup

`.scratch/typhoon-first-cut/research/02-typhoon-state-storage.md`
