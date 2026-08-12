# Typhoon first cut — wayfinder map

## Destination

画出「可玩第一刀」的清晰实现路线：用命令生成一场台风；强度沿路径呈弱→强→风眼→强→弱；水平风推动玩家；迎风弱方块出现挖掘式破坏并掉落被吹走；台风区域自管降雨且风眼局部停雨变弱；粒子表现风向。单人优先。地图结束时，实现前没有未决决策挡路。

## Notes

- 需求来源：`需求/20260812初始功能/清单.md`
- 工程：Fabric / Minecraft 1.21.1 / 模组 id `typhoon`（骨架几乎为空）
- 会话应参考：`/grilling`、`/domain-modeling`、`/research`；必要时 `/prototype`
- 已定方向（建图时钉死，细节在对应票里展开）：
  - 不追求圆形风场；1D 强度剖面 + 路径宽度裁剪
  - 抗风：自定义标签/数据表，覆盖大部分方块
  - 雨：台风区域驱动，不抢原版全局 `/weather`
  - 命令生成进第一刀；自然随机生成不进
  - 迎风面简化版进第一刀
  - 风向可视化：粒子为主 + 轻量状态提示；不做雷达
- 默认只产出决策与路线，不在本图内直接实现玩法代码（除非某张票 Notes 另说）

## Decisions so far

<!-- 关闭的票在此索引：一句话结论 + 链接到票 -->

- [第一刀台风领域用语](issues/01-domain-vocabulary.md) — 用语钉在根目录 `CONTEXT.md`：台风/中心/路径/强度剖面/风眼/影响宽度·带/主风向；局部强度·峰值等级·抗风等级分用；迎风面·背风·台风雨·风力破坏。
- [台风状态存哪、怎么 tick](issues/02-typhoon-state-storage.md) — 权威状态用服务端 `SavedData`（类比 `Raids`）+ `ServerTickEvents` 世界 tick；不推荐 Entity / 不作第一刀 Attachment 主方案。全文：`research/02-typhoon-state-storage.md`
- [风力破坏如何呈现挖掘进度](issues/03-block-break-progress.md) — 不必假玩家；`ServerLevel.destroyBlockProgress` 播 0–9 裂纹（负 id 池）+ `destroyBlock` 掉落。全文：`research/03-block-break-progress.md`
- [区域降雨且不抢全局天气](issues/04-regional-rain.md) — 不碰全局 `/weather`；服务端只同步台风几何/强度，客户端叠加雨粒子与音效；风眼 `strength=0`；湿润/作物逻辑不进第一刀。全文：`research/04-regional-rain.md`

## Not yet specified

- 强度曲线的具体距离/宽度/峰值参数（等剖面模型与体感阈值更清楚后再拆票）
- 台风命名与展示文案的最终形式（「海燕」类信息面板放哪）
- 掉落物被风吹走的物理手感与速度曲线
- 等级解锁的「风味」灾害（门乱开、屋顶等）是否进第一刀的后续增量，还是严格停在破坏+吹玩家

## Out of scope

- 天气雷达与科技线
- 飞行碎片伤害建筑/玩家
- 灾后统计结算界面
- 自然随机生成、复杂生命周期转向/突然消散
- 真实旋转/圆形风场模拟
- 多人联机权威同步方案（单人优先；日后另开图）
