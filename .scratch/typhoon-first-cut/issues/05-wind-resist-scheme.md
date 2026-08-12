# 抗风标签如何覆盖大部分方块

Type: grilling
Status: resolved
Blocked by: 01

## Question

自定义抗风等级（约 1–6）要用标签/数据表覆盖**大部分**方块，而不是清单里那几例。分类规则是什么：按材料族、按原版方块标签二次映射、还是「默认等级 + 例外表」？如何避免维护地狱，又保证玻璃/树叶/木/石/金属等体感合理？

## Answer

**模型：** 默认启发式（`BlockState.getDestroySpeed` / hardness 分箱）+ **标签例外表**。不按材料族全量手维，也不只二次映射原版标签。第一刀**不做**单方块 override JSON。

事实依据（hardness 会骗人：土>玻璃、木>石、铁=钻石、水=100）：[`research/05-wind-resist-scheme.md`](../research/05-wind-resist-scheme.md)。架构记录：[`docs/adr/0001-wind-resist-default-plus-tag-exceptions.md`](../../../docs/adr/0001-wind-resist-default-plus-tag-exceptions.md)。

### 查找顺序

1. **跳过 / 免疫** → 不参与风力破坏，结束  
2. **抗风例外 tag**（命中即用该档）  
3. **hardness 分箱** → 默认抗风等级  

实现：自定义 `typhoon:` BlockTag 嵌套原版 / `c:` 标签，少枚举单方块。不拿 `explosionResistance` 作分箱主信号。

### 例外表（第一刀必盖）

| 处理 | 族 | 抗风等级 |
| --- | --- | --- |
| 例外 | 树叶；玻璃块 + 玻璃板（用 `c:glass_*`，勿依赖不含 pane 的 `IMPERMEABLE`） | **1** |
| 例外 | 木制品（原木 / 木板 / 门 / 栅栏等） | **2** |
| 例外 | 普通石材（stone / cobble / stone bricks） | **3** |
| 例外 | 深板岩族 | **4** |
| 例外 | 铁系（铁块、铁栏杆等） | **5** |
| 例外 | 钻石块、**下界合金块**等「最高档可破坏」 | **6** |
| **免疫** | bedrock、barrier、黑曜石 / 哭泣黑曜石、强化深板岩、`WITHER_IMMUNE`、命令方块类 | — |
| **不参与** | 流体、空气、可替换草花等 | — |

设计意图：顶级风要能「毁灭世界」级破坏（含抗风 6），但**不能**拆基岩；黑曜石族与强化深板岩同样免疫。

羊毛 / 泥土 / 沙 / 陶瓦等第一刀可不进例外表，交给分箱。

### hardness 分箱（未命中例外时）

| 抗风等级 | hardness `h` |
| --- | --- |
| 1 | `0 ≤ h < 0.4` |
| 2 | `0.4 ≤ h < 1.0` |
| 3 | `1.0 ≤ h < 2.0` |
| 4 | `2.0 ≤ h < 3.5` |
| 5 | `3.5 ≤ h < 20` |
| 6 | `h ≥ 20` |
| 免疫 | `h < 0`（与跳过表叠加） |

### 刻意不做（第一刀）

- 单方块 override 表（离谱个案以后再用 datapack 补）  
- 以 blast 分箱  
- 纯原版 tag 二次映射当唯一来源  
