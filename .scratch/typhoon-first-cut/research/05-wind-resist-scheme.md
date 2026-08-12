# Research: 方块「抗风」启发式可用的原版韧性信号（1.21.1 Fabric / Mojmap）

Ticket: [`.scratch/typhoon-first-cut/issues/05-wind-resist-scheme.md`](../issues/05-wind-resist-scheme.md)  
Target: Minecraft **1.21.1**, Fabric API `0.116.15+1.21.1`, mappings: **`loom.officialMojangMappings()`（官方 Mojmap，非 Yarn）**  
Primary evidence: loom-mapped jar  
`.gradle/loom-cache/minecraftMaven/.../minecraft-common-*-1.21.1-loom.mappings...jar`、`Blocks` / `BlockBehaviour` javap、jar 内 `data/minecraft/tags/block/*.json`、Fabric convention-tags-v2 源码与 `data/c/tags/block/*`。

**本文件只记事实，不分配 1–6 抗风等级。**

---

## TL;DR

- **原版没有「抗风」字段**；最接近的数值信号是 `destroyTime`（hardness / `BlockState.getDestroySpeed`）与 `explosionResistance`（`Block.getExplosionResistance`）。
- **`Material` 在 1.21.1 已不存在**（jar 内无 `Material.class`）；仍有 `MapColor`、`SoundType`、`PushReaction`。
- **纯 hardness 不可靠**：水/岩浆 hardness=100；air 默认 0；泥土 0.5 > 玻璃 0.3；铁块与钻石块同为 **5.0 / 6.0**。
- **实用覆盖面**：`destroyTime`（或 hardness+blast 分箱）做**默认启发式** + **BlockTag / 自定义 tag 例外表**（玻璃、树叶、流体、不可破坏、金属存储块等）。
- Fabric **没有** NeoForge 式 DataMap；常用手段是 datapack `tags/block` JSON、`FabricTagProvider`、可选自定义 JSON/Codec 表。Convention tags（`c:`）已覆盖玻璃/石/黑曜石/storage_blocks 等族。

---

## 0. 工程映射确认

| 项 | 值 |
|----|-----|
| `minecraft_version` | `1.21.1`（`gradle.properties`） |
| `fabric_api_version` | `0.116.15+1.21.1` |
| mappings | `mappings loom.officialMojangMappings()`（`build.gradle`） |
| Java | 21 |

Yarn 对照名（社区常用，本工程代码用 Mojmap）：

| 概念 | Mojmap | Yarn（常见） |
|------|--------|----------------|
| 硬度（设置） | `Properties.destroyTime` / `strength` | `hardness` / `strength` |
| 硬度（读取） | `BlockState.getDestroySpeed` / `BlockBehaviour.defaultDestroyTime` | `getHardness` 等 |
| 爆炸抗性 | `explosionResistance` / `Block.getExplosionResistance` | `blastResistance` / `getBlastResistance` |
| 属性构建 | `BlockBehaviour.Properties` | `AbstractBlock.Settings` |

常量：`Block.INDESTRUCTIBLE = -1.0f`，`Block.INSTANT = 0.0f`。

---

## 1. 原版暴露的「韧性」信号

### 1.1 Hardness / destroySpeed

- 注册：`BlockBehaviour.Properties.strength(float)` → 同时设 hardness 与 blast（相等）；`strength(float, float)` → `(destroyTime, explosionResistance)`；亦可单独 `destroyTime` / `explosionResistance`。
- `strength(h)` 实现：调用 `strength(h, h)`。
- `instabreak()` → `strength(0)`。
- 读取：`state.getDestroySpeed(level, pos)` → 返回 state 内缓存的 `destroySpeed`（来自 Properties.destroyTime）。
- `defaultDestroyTime()` 读 Properties 上的 destroyTime。

### 1.2 Blast resistance

- 字段：`BlockBehaviour.explosionResistance`（构造时从 Properties 拷贝）。
- 读取：`Block.getExplosionResistance()`。
- `explosionResistance(f)` 写入 `Math.max(0, f)`（负值会被钳到 0；bedrock 用 `strength(-1, 3600000)` 走 strength 路径设 blast）。

### 1.3 BlockTags（原版，`net.minecraft.tags.BlockTags`）

与材料族/工具相关、可作例外或二次映射的常用 tag（非穷举）：

| TagKey | 用途线索 |
|--------|----------|
| `LEAVES`, `LOGS`, `PLANKS`, `WOOL`, `WOOL_CARPETS` | 有机/软质族 |
| `STONE_BRICKS`, `BASE_STONE_OVERWORLD`（含 stone…**deepslate**）, `DIRT`, `SAND`, `ICE`, `SNOW` | 土石 |
| `IMPERMEABLE` | **仅玻璃方块**（含染色/遮光），**不含 glass pane** |
| `MINEABLE_WITH_{AXE,PICKAXE,SHOVEL,HOE}` | 工具族（斧≈木，镐≈石/矿） |
| `NEEDS_STONE_TOOL` / `NEEDS_IRON_TOOL` / `NEEDS_DIAMOND_TOOL` | 工具档（铁块在 stone；钻石块在 iron；黑曜石/下界合金在 diamond） |
| `BEACON_BASE_BLOCKS` | 铁/金/钻石/绿宝石/下界合金块 |
| `WITHER_IMMUNE`, `DRAGON_IMMUNE`, `FEATURES_CANNOT_REPLACE` | 「几乎不动」类 |
| `BLOCKS_WIND_CHARGE_EXPLOSIONS` | 仅 barrier + bedrock（**风弹爆炸**语义，不是台风抗风） |

路径：`data/minecraft/tags/block/<name>.json`（1.21 用 **`block`** 单数，不是旧版 `blocks`）。

### 1.4 MapColor / Material

- **`Material`：1.21.1 jar 中不存在**（已移除）。不可再 `Material.GLASS` 之类分支。
- **`MapColor` 仍在**（`net.minecraft.world.level.material.MapColor`）：`defaultMapColor()` / `state.getMapColor(...)`。仅为地图色，**不是**机械强度；同色块可差很多。
- 仍可用的弱信号：`SoundType`（WOOD/GLASS/METAL/…）、`PushReaction`、`replaceable`、`liquid`/`air` Properties 标志。

### 1.5 Fabric API helpers

| API | 与抗风相关的事实 |
|-----|------------------|
| `ConventionalBlockTags`（`c:`，convention-tags-v2） | `GLASS_BLOCKS`/`GLASS_PANES`、`STONES`、`OBSIDIANS`、`STORAGE_BLOCKS_*`、`STRIPPED_LOGS` 等；跨模组约定 |
| `FabricTagProvider` / datapack tags | 自定义 `typhoon:wind_resist_*` 或在 JSON 里 `#minecraft:leaves` 嵌套 |
| `fabric-tag-api-v1` `FabricTagFile.remove()` | datapack 可 `fabric:remove` 条目 |
| `fabric-block-api-v1` | 主要是外观/`BlockFunctionalityTags`；**无硬度封装** |
| Attachment API | 挂在 entity/block entity/chunk 等实例；**不是**按方块类型的 data map |
| BlockApiLookup | 可对「方块类型→抗风 provider」做查找，仍需自己填表 |
| **NeoForge DataMap** | **本 Fabric 依赖树中不存在** |

`BlockState` 的 Property（facing 等）**不是**韧性通道。

---

## 2. 代表方块 hardness / blastResistance（代码值）

来源：1.21.x `Blocks` 注册（loom jar javap + Blocks 源码转储）。`strength(a)` ⇒ hardness=blast=`a`；`strength(a,b)` ⇒ `(a,b)`。

| 方块/族 | hardness (`destroyTime`) | blast (`explosionResistance`) | 备注 |
|---------|--------------------------|-------------------------------|------|
| Leaves（`leaves()` / `leavesProperties`） | **0.2** | **0.2** | |
| Glass / pane / stained | **0.3** | **0.3** | tinted 的 ofLegacyCopy(GLASS) |
| Snow layer | 0.1 | 0.1 | |
| Snow block | 0.2 | 0.2 | |
| Netherrack | 0.4 | 0.4 | |
| Dirt / sand | 0.5 | 0.5 | **> 玻璃** |
| Grass | 0.6 | 0.6 | |
| Wool | 0.8 | 0.8 | |
| Terracotta | 1.25 | 4.2 | |
| Stone / stone bricks | **1.5** | **6.0** | |
| Cobblestone / bricks | 2.0 | 6.0 | |
| Planks / wood fence / wood slab | **2.0** | **3.0** | |
| Log / wood（`strength(2)`） | **2.0** | **2.0** | blast **低于** planks |
| Oak door | 3.0 | 3.0 | |
| Deepslate | **3.0** | **6.0** | |
| End stone | 3.0 | **9.0** | blast > deepslate |
| Gold / copper block | 3.0 | 6.0 | |
| Cobbled deepslate / deepslate bricks（copy） | **3.5** | **6.0** | |
| Iron block / iron bars / diamond block / raw iron | **5.0** | **6.0** | **铁=钻石（数值）** |
| Ancient debris | 30 | 1200 | |
| Obsidian / crying / netherite block | **50** | **1200** | |
| Reinforced deepslate | 55 | 1200 | |
| Bedrock | **-1** | **3600000** | 不可生存破坏 |
| Water / lava | **100** | **100** | 流体陷阱 |
| Air（无 strength） | **0**（字段默认） | **0** | |

`Properties` 私有构造函数**未**显式初始化 destroyTime/explosionResistance → Java float 默认 **0**（air 即此）。

---

## 3. 数据驱动做法（Fabric 1.21.1）

### 3.1 自定义 BlockTags + datapack JSON（最常规）

```
data/typhoon/tags/block/wind_fragile.json
{
  "values": [
    "#minecraft:leaves",
    "#c:glass_panes",
    "#c:glass_blocks"
  ]
}
```

- 运行时：`state.is(TYPHON_TAG)` / `builtInRegistryHolder().is(tag)`。
- Datagen：`FabricTagProvider.BlockTagProvider`。
- 可用 `#minecraft:…` / `#c:…` 嵌套，减少枚举。

### 3.2 「启发式默认 + 例外表」模式（同类 mod 常见事实模式）

1. 跳过：`destroySpeed < 0`（bedrock）、fluid/air/replaceable（按需）、或 `WITHER_IMMUNE`/`FEATURES_CANNOT_REPLACE`。
2. 若命中自定义 / 原版 / `c:` tag 例外 → 用例外档。
3. 否则对 `getDestroySpeed`（可选再看 `getExplosionResistance`）分箱得默认档。
4. 可选：单方块 override 表（JSON Codec / 配置）。

**没有**原版「按 Material 自动分档」；Material 已删。

### 3.3 Block state properties / data maps

- **State properties**：方向/水位等，不含强度。
- **NeoForge DataMap**：非 Fabric 标准能力。
- 等价物：自定义 datapack JSON、tag、或 `BlockApiLookup`/静态 `Map<Block, …>`。

### 3.4 纯 hardness 是否可靠？

| 现象 | 事实 |
|------|------|
| 玻璃 vs 泥土 | 玻璃 0.3 < 泥土 0.5 → 纯数值会把土排得「更脆」或玻璃不够脆，取决于阈值 |
| 树叶 | 0.2，很低，与「脆弱」一致 |
| 木门 3.0 vs 木板 2.0 | 同「木」硬度不一致 |
| 原木 blast 2 vs 木板 blast 3 | 同族 blast 不一致 |
| 铁块 = 钻石块 | 5/6 相同；仅靠 hardness/blast **分不开** |
| 金块 3/6 | 比铁块「软」 |
| 水/岩浆 | hardness 100 → 会被当成极硬 |
| Air | 0 → 极脆 |
| Bedrock | -1 → 必须特判 |
| Deepslate vs stone | 3 vs 1.5，同 blast 6 → hardness 可区分，blast 不能 |
| End stone | blast 9 > deepslate 6 | hardness 同为 3 |

**结论（事实）：** hardness 可作粗默认；材料体感（玻璃/叶/金属档/不可破坏/流体）必须靠 **tag 例外** 或其它信号（`NEEDS_*_TOOL`、`c:storage_blocks/*`、`c:obsidians` 等）。

---

## 4. 对启发式的推荐信号（仍非产品定档）

**主信号（覆盖未知方块）：** `BlockState.getDestroySpeed`（必要时辅以 `getExplosionResistance`，注意多数建筑石都是 blast=6）。

**应显式例外 / 优先 tag 的族（事实依据：hardness 错位或族内不一致）：**

1. 流体 / air / `replaceable` / 无碰撞装饰  
2. `minecraft:leaves`、`#c:glass_blocks`、`#c:glass_panes`（pane **不在** `IMPERMEABLE`）  
3. `LOGS`/`PLANKS`/木制品（hardness 2–3 混杂）  
4. `NEEDS_*_TOOL` / `c:storage_blocks/*` / `BEACON_BASE_BLOCKS`（区分铁 vs 钻石等）  
5. `c:obsidians`、`NEEDS_DIAMOND_TOOL`、bedrock / `WITHER_IMMUNE`  
6. `BASE_STONE_OVERWORLD` / `c:stones` / deepslate 变种（若要用石 vs 深板岩体感）

**弱信号：** `MapColor`、`SoundType`（可辅助，不可单独当真）。

---

## 5. 引用索引

- 工程：`/workspace/build.gradle`（`officialMojangMappings`）、`gradle.properties`（1.21.1 / Fabric API）  
- Mojmap API：`BlockBehaviour.Properties.strength/destroyTime/explosionResistance`；`BlockState.getDestroySpeed`；`Block.getExplosionResistance`；`Block.INDESTRUCTIBLE`  
- 数值：`Blocks` 注册（leaves `0.2f`，glass `0.3f`，planks `2,3`，log `2`，stone `1.5,6`，deepslate `3,6`，iron/diamond `5,6`，obsidian `50,1200`，bedrock `-1,3600000`，water `100`）  
- Tags：jar `data/minecraft/tags/block/*`；Fabric `data/c/tags/block/*` + `ConventionalBlockTags`  
- Wiki 对照：[Breaking](https://minecraft.wiki/w/Breaking)、[Explosion § Blast resistance](https://minecraft.wiki/w/Explosion#Blast_resistance)（与代码一致时可作交叉验证）
