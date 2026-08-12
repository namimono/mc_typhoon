# 风力破坏如何呈现挖掘进度

Type: research
Status: resolved

## Question

服务端因「风力超过抗风」破坏方块时，如何在 1.21.1 Fabric 上让客户端看到类似手挖的破坏裂纹/进度，并在完成后掉落？查清可用 API、包、同步方式与限制（是否必须伪装成玩家挖掘、是否能对非玩家来源播进度）。

## Answer

**不必伪装玩家。** 用原版 `ServerLevel.destroyBlockProgress(breakerId, pos, stage)` 播 0–9 裂纹；完成后 `level.destroyBlock(pos, true)` 掉落并清进度 `destroyBlockProgress(..., -1)`。

### 推荐路径（第一刀）

1. **进度（视觉）**  
   - API：`net.minecraft.server.level.ServerLevel#destroyBlockProgress(int id, BlockPos pos, int progress)`  
   - 包：`ClientboundBlockDestructionPacket`（协议 Set Block Destroy Stage）  
   - `progress`：`0..9` 裂纹阶段；`<0` 或 `≥10` 清除  
   - 服务端向同维度、距离²&lt;1024（~32 格）的玩家广播；**跳过 `player.getId()==id` 的玩家**  
   - 客户端：`LevelRenderer` 用 `destroy_stage_*` 贴裂纹；约 20s 无更新自动过期  

2. **完成（逻辑 + 掉落）**  
   - `level.destroyBlock(pos, true)`（`LevelWriter` 默认重载 → entity=null, 空工具 `dropResources`）  
   - 已含 `LevelEvent.PARTICLES_DESTROY_BLOCK`（2001）破碎 FX  
   - 再 `destroyBlockProgress(id, pos, -1)` 并释放 id  

3. **breakerId**  
   - 仅作客户端槽位键，**不要求真实实体/玩家**  
   - 风力用**负 id 池**（避免撞实体 id）；**每个同时裂开的方块一个 id**  
   - 误用在线玩家 id → 该玩家本人看不到裂纹  

4. **不必 / 不要默认做的**  
   - 假 `ServerPlayer` / `ServerPlayerGameMode` 挖掘流水线  
   - 用 `LevelEvent` 模拟裂纹（LevelEvent 只管 2001 等 FX，不管 0–9 stage）  
   - Fabric API 无专用封装；直接调原版即可  

5. **掉落限制**  
   - 空工具战利品：需正确工具的方块可能不掉；若风毁要「总是掉」需自管 `getDrops`/假工具  

6. **性能**  
   - 仅在 stage 变化时发包（对齐原版 `lastSentState`）  
   - 并发上限 = id 池大小；包本身很轻，瓶颈在扫块预算  

全文与引用：`.scratch/typhoon-first-cut/research/03-block-break-progress.md`
