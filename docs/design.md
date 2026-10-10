# waystone_xp_payment 设计文档（v0.1 设计冻结候选）

> 状态：设计阶段（未写代码）。本文档是「实现映射层」：把用户 m00435 的 9 条答复 + 额外要求，
> 落到已核实的上游 API 与具体类签名上。所有上游结论都标了文件:行号，便于复核。
> 工程遵循 `D:\deepseek\Minecraft模组开发通用约束.md`（条款分级 / 拷打 / 代码质量 / 项目管理）。

---

## 1. 目标与验收

**一句话**：为 Waystones 增加一台双端附属设备——储经验流体的储罐；当具有传送功能的方块
（石碑 / warp plate / 纪念石 / sharestone）**正压在该设备上方**时，该方块传送所需的**经验**
改由**设备的共享流体池**支付（不够的部分再由玩家支付；两者都不够则整笔回滚并取消传送）。

| # | 用户要求（m00435） | 设计落点 |
|---|---|---|
| 1 | 双端模组 | 单 jar，common 代码 + `Dist.CLIENT` 隔离的 GUI 类；无服务端独有缺失 |
| 2 | 判定几何：石碑在设备**正上方**；且覆盖同一 mod 内其它需要扣经验的传送设备 | 判定 `fromWaystone.getPos().below() == 设备位置`；`fromWaystone` 由 Waystones 对石碑/plate/纪念石/sharestone 统一设置（§3.2） |
| 3 | 哪些流体算"经验"（未答） | 默认 `#c:experience` + `create_enchantment_industry:experience`，可通过 config map / 配方扩展（§6） |
| 4 | 换算率可配置，默认参考机械动力（Create）体系 | 默认 `mbPerXp = 1`（CEI 权威值：1 mB = 1 XP，§3.3）；config 可改，配方可覆盖 |
| 5 | 设备容量 4 桶 | `capacityMb = 4000`（默认，可配置；1 桶 = 1000 mB） |
| 6 | **完全替换**玩家经验 | 从需求树中摘掉 `ExperiencePointsRequirement`，替换为流体支付需求（§4.2） |
| 7 | 流体不足：先扣罐、再扣玩家；玩家也不足 → 回滚流体与经验并取消传送 | `canAfford` 纯查询无副作用 + `consume` 计算后一次扣减 + 实现 `rollback`（§4.3） |
| 8 | 所有使用者共享同一个池；授权系统：GUI 内白/黑名单模式，可联动 FTB Teams，可对指定队伍设定名单 | 池 = 设备自身储罐（每设备一个共享池）；`AccessList{mode, players, teams}` + `TeamResolver` 软依赖 FTB Teams（§5） |
| 9 | 客户端不做额外 hook | 不加任何客户端需求显示 hook → UI 仍显示原经验花费（**已接受的限制**，§9） |
| 补 | 参考主流科技模组的"流体消费者"，接受流体输入，保证兼容 | 暴露 NeoForge `Capabilities.FluidHandler.BLOCK`（§3.4），任意科技模组管道/泵可注入 |
| 补 | 消耗的流体可 config 手动配置，或 KubeJS 以**合成表（配方）**注册；可注册为具体流体命名空间，也可注册为 tag | 自定义配方类型 `waystone_xp_payment:xp_fluid` + `[fluidConversions]` config map；KubeJS 两阶段接入（§6、ADR-10） |
| 补 | mod id = `waystone_xp_payment` | 全项目命名空间 |
| 补 | 验证标准：gradle build + runServer 冒烟 | §10 |

**非目标（首版明确不做）**：不注册自己的经验流体；不做经验↔等级的双向机器（酿造/提取）；
不加客户端花费显示 hook；不做跨维度能量网/无线池；不覆盖 `ExperienceLevelRequirement`（按等级计费，见 ADR-3）。

---

## 2. 环境与版本槽位

| 槽位 | 值 | 证据 |
|---|---|---|
| Minecraft | 1.21.1 | 用户要求 |
| NeoForge | 21.1.8（与上游 Waystones 一致，避免矩阵漂移） | `_ref/Waystones/gradle.properties` |
| Java | 21（`java version "21.0.2" 2024-01-16 LTS`） | 本机 PATH |
| Waystones | `net.blay09.mods:waystones-neoforge:21.1.46+1.21.1` | jar HTTP 200，1,169,397 字节；maven.twelveiterations.com/repository/maven-public/ |
| 传递依赖 | `balm-neoforge:21.0.59+1.21.1`(runtime)、`unbreakables-neoforge:21.1.6+1.21.1`(runtime)、`kuma-api-neoforge:21.0.8+1.21`(compile via Balm)、`org.jetbrains:annotations:24.1.0` | 上游 POM |
| 可选 FTB Teams | `dev.ftb.mods:ftb-teams-neoforge:2101.1.11`（`https://maven.ftb.dev/releases/`） | §5 |
| 可选 KubeJS | `dev.latvian.mods:kubejs-neoforge:2101.7.2-build.379`（1.21.1 线；`https://maven.latvian.dev/releases/`） | §6，阶段② |
| 构建插件 | ModDevGradle（`net.neoforged.moddev`）——具体版本在骨架步骤用插件市场实际解析确认，不写猜的版本号 | 待步骤 S1 实测 |

命名：mod id `waystone_xp_payment`；包 `com.olynk.waystonexppayment`；仓库 `0Lynk/waystone_xp_payment`（私有）。

---

## 3. 上游机制映射（已核实到行）

### 3.1 钩子点
- `WaystoneTeleportEvent.Pre`（`api/event/WaystoneTeleportEvent.java:17-41`）在
  `core/WaystoneTeleportManager.java:437-438` fire，`:439-442` 可取消。
- 事件触发时**需求已解析完毕**：`InternalMethodsImpl.java:63-72` 先 `init.accept(context)` 再
  `context.setRequirements(resolveRequirements(context))`；`resolveRequirements` 在
  `InternalMethodsImpl.java:244-258` 里解析配置字符串 + apply 修饰器。
- 因此 `Pre` 里可以：**读出本次传送的真实经验花费** → **替换 `context.getRequirements()`**。
- 随后 `validateRequirements`（`:483-490`）用 `context.getRequirements().canAfford(player)`
  （非创造模式），`consumeRequirements`（`:492-496` → `consume`）在 `:311`（立即传送）与
  `:353`（异步/延迟路径，`:348` 会重新 validate）调用。
- ⚠ 全仓扫描**未发现 manager 调用 `rollback`**：回滚安全主要靠我们自己的 `canAfford` 无副作用（ADR-9）。

### 3.2 传送源判定
- 石碑/plate/纪念石/sharestone 都继承 `block/entity/WaystoneBlockEntityBase.java:55`；warp plate 在
  `WarpPlateBlockEntity.java:265-281` 走
  `WaystonesAPI.createUncheckedDefaultTeleportContext(entity, target, it -> { it.setFromWaystone(getWaystone()); it.setWarpItem(...); })`。
- ⇒ `context.getFromWaystone()`（`api/WaystoneTeleportContext.java`）对上述全部传送源都可用；
  取其 `getPos()`（`api/Waystone.java`）+ 目标维度，判定 `pos.below()` 是否为我们的方块。
- 好处：**一行判定覆盖用户 #2 的全部传送源**，且不依赖方块类型判断（第三方附属模组若也 setFromWaystone 自动受益）。

### 3.3 花费提取与换算
- `requirement/ExperiencePointsRequirement.java:10-92`：`private int points`；**有 `public int getPoints()` 与 `setPoints(int)`**；
  `consume` = `player.giveExperiencePoints(-points)`；`rollback` = `+points`；`canAfford` 用等级累计经验 + 进度余量判定。
- 需求树入口：`requirement/CombinedRequirement.java:43-45` `getRequirements(): Collection<WarpRequirement>`（公开）；
  另有 `api/requirement/WarpRequirement.java:8-21` 的 `canAfford/consume/rollback/appendHoverText/isEmpty`。
- ⇒ 花费 = 递归遍历需求树，累加所有 `ExperiencePointsRequirement.getPoints()`。**不自己重算配置字符串**
  （否则会漏掉 `scaled_add_xp_cost` / `min_xp_cost` / `max_xp_cost` / 维度修正，见 ADR-2）。
- 换算默认值来自 CEI（Create: Enchantment Industry，v2.5.4b，分支 `1.21.1/6.0.0-dev`，仅参考不作依赖）：
  `common/fluids/experience/ExperienceHelper.java` 的 `getExperienceFromFluid` 对本家流体 `return fluid.getAmount()`
  ⇒ **1 mB = 1 XP**；其它流体读 data map `CEIDataMaps.FLUID_UNIT_EXPERIENCE`（mB per XP）。
  这是"参考机械动力体系"的权威落点。

### 3.4 流体输入契约（兼容性）
- NeoForge 的标准做法：`RegisterCapabilitiesEvent` 里
  `event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, BE_TYPE.get(), BE::getFluidHandler)`。
  参考实现（主流科技模组侧）：CEI `common/registry/CEIBlockEntities.java:99-113` 对 7 个机器 BE 逐个注册同一能力；
  CEI 的 `ExperienceHatch*`（`common/fluids/experience/`）就是"接受流体输入"的经验流体消费者样本。
- ⇒ 只要我们的 BE 暴露 `IFluidHandler`（`Capabilities.FluidHandler.BLOCK`），Create 流体网络、Mekanism 机械管道、
  Thermal 流体管道、AE2 流体、IE 等**全部通过同一能力接口**注入/抽取，无需逐个兼容适配。
- 附加便利：手持流体容器（桶/流体罐）右键设备可手动灌入/抽出（config 可关）。

---

## 4. 核心机制

### 4.1 触发序列
```
Pre 事件
 ├─ fromWaystone 为空 / 维度不符          → 不干预（原样）
 ├─ pos.below() 不是我们的方块            → 不干预
 ├─ 花费 XP ≤ 0                          → 不干预
 ├─ 玩家不被授权使用该池                  → 不干预（回退为玩家自付，ADR-4）
 ├─ 创造模式（instabuild）且 skipPaymentInCreative → 不干预（保持原版行为，ADR-5）
 └─ 否则：residual = 原需求树剔除全部 ExperiencePointsRequirement
          context.setRequirements(new FluidPaymentRequirement(residual, level, tankPos, costXp, settings))
```
之后 Waystones 正常走 `canAfford` → `consume`。

### 4.2 `FluidPaymentRequirement`（实现 `WarpRequirement`）
```java
final class FluidPaymentRequirement implements WarpRequirement {
    private final WarpRequirement residual;   // 冷却 / 物品等非经验需求，原样委托
    private final ServerLevel level;
    private final BlockPos tankPos;            // 存位置而非 BE 引用，异步路径下重新解析，避免过期引用
    private final int costXp;                  // Pre 时刻解析出的真实花费
    private final XppSettings settings;        // mbPerXp / 流体白名单快照

    @Override public boolean canAfford(Player player);         // 纯查询，无副作用
    @Override public void consume(Player player);              // 一次性扣减：罐 + 玩家
    @Override public void rollback(Player player);             // 退回已扣：罐 + 玩家
    @Override public void appendHoverText(Player player, List<Component> tooltip);  // 服务端提示用
    @Override public boolean isEmpty();
}
```
- `canAfford` = `residual.canAfford(player) && split.affordable()`；`split` 由
  `PaymentSplit.compute(costXp, tankMb, mbPerXp, playerXp)` 得出（纯函数，可单测）。
- `consume` = 按 split 扣 `tank.drain(fluidUsed)` + `player.giveExperiencePoints(-xpFromPlayer)`，
  然后 `residual.consume(player)`。
- `rollback` = 反向：`tank.fill(...)` + `player.giveExperiencePoints(+xpFromPlayer)` + `residual.rollback(player)`。
- ⚠ 罐内流体可能是多种流体（不同 rate）：split 计算以**罐内可读经验总量**
  `sum(rate(f) * mb(f))` 的形式先折成"可提供的 XP"，扣减时按流体逐个 drain（§6.4）。

### 4.3 回滚语义（用户 #7）
| 情形 | 结果 |
|---|---|
| 罐够全部花费 | 只扣罐，玩家经验不动 |
| 罐部分够 | 先扣罐（`xpFromFluid = min(costXp, tankXp)`），余下扣玩家经验 |
| 罐 + 玩家都不够 | `canAfford = false` → Waystones 抛 `NotEnoughXp` → **传送取消，未发生任何扣减**（`canAfford` 无副作用） |
| 传送在 `consume` 之后失败（上游异常路径） | `rollback` 退回罐与玩家经验（防御性实现；上游当前未调用，见 ADR-9） |

---

## 5. 授权模型（用户 #8）

- 池语义：**每台设备一个共享池**，所有使用者共用；容量固定（4000 mB 默认）。
- `AccessMode { WHITELIST, BLACKLIST }`；`AccessList { AccessMode mode; Set<UUID> players; Set<UUID> teams; }`
  - WHITELIST：只有名单内的玩家（或其所属队伍在 `teams` 内）可动用该池；其余回退为玩家自付。
  - BLACKLIST：名单内的被排除，其余可用。
- `TeamResolver`（接口）+ 两个实现：
  - `NoTeamResolver`（无 FTB Teams 时，队伍集合为空）；
  - `FtbTeamsResolver`（`ModList.get().isLoaded("ftbteams")` 时才加载该类，避免类缺失崩溃）。
  队伍判定 = 玩家所属 FTB Team 的 UUID 是否在 `teams` 集合中。
- GUI（`XpPaymentTankScreen` + `XpPaymentTankMenu`）：流体量表 + 容量文本 + 模式切换按钮 +
  条目列表（玩家 / 队伍）+ 添加/移除控件；修改经自定义网络包（`CustomPacketPayload`）到服务端并校验权限
  （仅设备所有者 `/op` 可改，ADR-6）。
- 附加命令（便于测试与自动化，也可作无 GUI 的兜底）：`/xpp access <add|remove|mode|list>`、`/xpp info`。

---

## 6. 数据与配置

### 6.1 `[fluidConversions]`（config，`config/waystone_xp_payment-common.toml`）
```toml
mbPerXp = 1            # 默认换算率（mB / 1 XP），参考 CEI
capacityMb = 4000      # 4 桶
allowBucketInteraction = true
skipPaymentInCreative = true

[fluidConversions]
"#c:experience" = 1                          # tag 形式
"create_enchantment_industry:experience" = 1 # 具体流体命名空间形式
```
- 键可以是 `#tag` 或 `namespace:fluid`；值 = 该流体的 `mbPerXp`（可覆盖全局默认）。

### 6.2 配方类型 `waystone_xp_payment:xp_fluid`
语义：**声明某流体/流体 tag 的经验换算率**（因此是"数据驱动的换算注册表"，同时天然可用 KubeJS 注册）。
```json
{ "type": "waystone_xp_payment:xp_fluid",
  "fluid": { "tag": "c:experience" },
  "mb_per_xp": 1 }
```
```json
{ "type": "waystone_xp_payment:xp_fluid",
  "fluid": { "fluid": "create_enchantment_industry:experience" },
  "mb_per_xp": 1 }
```
- 数据包路径：`data/<ns>/recipe/*.json`；也可由 KubeJS 写：
  `ServerEvents.recipes(e => e.custom({ type: 'waystone_xp_payment:xp_fluid', fluid: { tag: 'c:experience' }, mb_per_xp: 1 }))`。
- 查找优先级（ADR-10）：**config 精确流体 → config tag → 配方精确流体 → 配方 tag → 不接受该流体**。
- 换算是"查询表"，不参与合成，因此不占用合成机会，也不会与其它模组配方冲突。

### 6.3 设置快照
`XppSettings`（record）：`mbPerXp`、`capacityMb`、流体→rate 查询表（config + 已加载配方构建，配方重载时重建）。
`Pre` 时刻取一次快照，保证一次传送全程使用同一套参数（不因中途 reload 而语义漂移）。

### 6.4 多流体罐的扣减规则
- 罐内允许混合不同 rate 的流体吗？**不允许**：设备只接受"被声明的经验流体"，且**只接受与罐内现有流体相同**的流体
  （`IFluidHandler.isFluidValid` 用 `FluidStack.isSameFluid` 判定）。理由：避免拆分/排序歧义与
  "先扣贵还是先扣便宜"的意外行为。记 ADR-7（替代：允许混合、按 rate 排序扣减——复杂且对玩家不可预期）。

---

## 7. 类清单（首版）

```
src/main/java/com/olynk/waystonexppayment/
  WaystoneXpPaymentMod.java            @Mod 主类：DR 注册、能力注册、配置加载
  config/XppConfig.java                ModConfigSpec（COMMON），映射为 XppSettings
  config/XppSettings.java              不可变设置快照 + 流体→rate 查询
  registry/XppBlocksItems.java         DeferredRegister: Block/Item/BlockEntityType/MenuType/RecipeType/Serializer/CreativeTab
  block/XpPaymentTankBlock.java        BaseEntityBlock；useWithoutItem 开菜单；桶交互；破坏掉落（ADR-8）
  block/entity/XpPaymentTankBlockEntity.java   FluidTank + AccessList + NBT + MenuProvider + getFluidHandler(Direction)
  fluid/FluidRates.java                配方/配置装载与查询（ServerAboutToStart + RecipeManager reload 重建）
  payment/XpRequirementScanner.java    递归提取经验花费（含 residual 剔除）
  payment/PaymentSplit.java            record + compute(costXp, tankXp, playerXp) 纯函数
  payment/FluidPaymentRequirement.java WarpRequirement 实现
  auth/AccessMode.java, auth/AccessList.java, auth/TeamResolver.java,
  auth/NoTeamResolver.java, auth/FtbTeamsResolver.java
  event/XpPaymentEvents.java           WaystoneTeleportEvent.Pre 订阅（GAME 总线）
  menu/XpPaymentTankMenu.java, client/XpPaymentTankScreen.java, client/XppClientSetup.java
  net/XppNetwork.java + net/AccessUpdatePayload.java   名单/模式修改的 C2S 包
  command/XppCommands.java             /xpp access|info（测试与自动化用）
  compat/kubejs/XppKubeJSPlugin.java   阶段②：RecipeSchema 原生 schema（compileOnly）
src/main/resources/
  META-INF/neoforge.mods.toml          modId/version/依赖（waystones 必选，ftbteams/kubejs 可选）
  assets/waystone_xp_payment/{blockstates,models,textures,lang/en_us.json,lang/zh_cn.json}
  data/waystone_xp_payment/{recipe/*.json, loot_table/*.json, tags/*}
src/test/java/com/olynk/waystonexppayment/  PaymentSplitTest, FluidRatesTest, XpRequirementScannerTest
```

---

## 8. 关键决策（ADR，含替代路径与倾向）

| # | 决策 | 替代路径 | 倾向与理由 |
|---|---|---|---|
| 1 | 用自定义 `WarpRequirement` + `Pre` 注入 | ① 纯配置 `multiply_xp_cost(0)`；② 用内置 `ItemRequirement` 拿物品付；③ 只认正下方原版容器、不加方块；④ 认正下方容器 + 不替换需求（改监听扣费） | **选本方案**。①做不到"消耗流体"；②代价是物品不是流体；③无 GUI/无授权/无容量；④无法读取"本次花费"，会重复扣费。只有 ① 在"只想不花自己经验"时可替代——已告知用户 |
| 2 | 花费取自**已解析需求树**（`ExperiencePointsRequirement.getPoints()`） | 自己解析 config 字符串重算 | 取自上游：上游 `scaled_add_xp_cost`/`min/max_xp_cost`/维度修正都会改变最终点数，重算必然漂移。代价：引用 `requirement` 包（非 `api` 包）的内部类 → **锁定 21.1.46**，上游改名会在编译期报错（优于运行时静默错值） |
| 3 | 首版不覆盖 `ExperienceLevelRequirement`（按等级计费） | 动态把等级成本换算成 XP 点数后一并由流体付 | 等级成本依赖玩家当前等级，语义易变且默认配置不用它。**记为已知限制 + Issue**，不写空配置项 |
| 4 | 未授权 → 回退玩家自付（不阻断传送） | 未授权直接取消传送 | 授权语义是"资源使用许可"，不是"传送许可"；阻断会激怒被误配名单的玩家。回退行为可预测且无损 |
| 5 | 创造模式默认不扣罐（沿用原版跳过判定） | 创造也扣罐 | 与 `WaystoneTeleportManager.java:483-490` 的 `instabuild` 跳过保持一致；否则管理员跑图会莫名抽干公共池。可配置 |
| 6 | GUI 改名单需所有者/OP 权限 | 任何使用者都能改 | 共享池的授权名单是设备级配置，不应被使用者改写。命令与 GUI 走同一权限校验 |
| 7 | 罐内只允许单一流体（浓度不可混合） | 允许混合、按 rate 排序扣减 | 避免"先扣贵/便宜"歧义；也让 `isFluidValid` 含义明确 |
| 8 | 破坏设备丢弃内部流体（tooltip 提示） | 用物品数据组件把内容物封存进掉落物 | 首版求稳（数据组件 + 迁移逻辑是额外风险面）；封存列为后续增强 Issue |
| 9 | `canAfford` 无副作用 + 防御性 `rollback` | 依赖上游 rollback | 上游 `WaystoneTeleportManager` 未见 rollback 调用点 → 唯一可靠保障是 `canAfford` 不改状态 |
| 10 | KubeJS 两阶段：① 先支持 `e.custom({...})`（无需依赖）；② 再加原生 schema 插件 | 一步到位直接做插件 | 阶段①零风险且已满足"用 KubeJS 配方注册"的诉求；阶段②提升书写体验但引入 compileOnly 依赖与 API 漂移风险。先交付 ① |
| 11 | FTB Teams 走软依赖 + 隔离类 | 硬依赖 / 不做 | 用户明确要联动但不能强制装 FTB；`ModList.get().isLoaded` + 隔离类保证未装时零风险 |
| 12 | 暴露 NeoForge 流体能力 | 自定义兼容层逐个适配科技模组 | 能力接口就是跨模组契约（CEI `CEIBlockEntities.java:99-113` 同构证据）；逐个适配不可维护 |

---

## 9. 已知限制（诚实标注）
1. **客户端 UI 仍显示经验花费**（用户 #9 明确不做客户端 hook）：玩家会看到"消耗 27 经验"，
   而实际由流体池支付。服务端行为正确，展示不准确。已记录；若未来要改，需要客户端侧
   `resolveRequirements` 覆盖或 `Clientbound` 提示包。
2. 按等级计费的需求（`ExperienceLevelRequirement`）首版不覆盖（ADR-3）。
3. 破坏设备丢弃罐内流体（ADR-8）。
4. 依赖 Waystones 内部 `requirement` 包的两个类（ADR-2）→ 上游大版本变更需同步适配。

## 10. 验证计划
| 阶段 | 手段 | 通过判据 |
|---|---|---|
| V1 纯函数单测 | JUnit 5：`PaymentSplit`（全罐够/部分够/都不够/rate>1 余数/cost=0/边界）+ `FluidRates` 优先级 + `XpRequirementScanner` 需求树提取 | `./gradlew test` 全绿；关键分支覆盖 |
| V2 编译与打包 | `./gradlew build` | `BUILD SUCCESSFUL` + 产出 jar |
| V3 服务端冒烟 | `./gradlew runServer`（`eula=true`，`--nogui`，日志断言后自动停服） | 日志：模组加载成功、无我们模组的 ERROR/Exception、服务器正常停服 |
| V4 设备链路（能力/储罐/持久化） | 可行时用 NeoForge GameTest；否则 `/xpp info` + 桶交互 + 重启验证 NBT | 注液→容量正确→重载后仍在→非目标流体被拒 |
| V5 端到端传送（真实石碑） | **需人工或后续自动化**：runServer 内建石碑数据较复杂，首版诚实标注为未自动覆盖，写入 Issue | 人工：放设备+石碑→传送→罐减少、玩家经验不动 |

证据（原始日志行 / 命令输出 / 截图）汇总到 `docs/verification.md`。

## 11. 项目管理与 GitHub 交付
- 仓库：`0Lynk/waystone_xp_payment`（**私有**）；默认分支 `main`；Trunk-Based（分支 ≤3 天）；仅 Draft PR。
- CI：`.github/workflows/build.yml`（JDK 21 temurin → `./gradlew build` → 上传 jar 工件），无需密钥。
- 文档：`README.md`（含 zh_CN 版）、`CHANGELOG.md`、`LICENSE`（MIT，纯自研代码；CEI 仅取"1 mB = 1 XP"这一公开行为数值，不复制代码）、`.gitignore`（`run/`、`.gradle/`、`build/`）。
- **凭据现状**：用户指定的 `C:\Users\Dogay\.dsh\github_token.txt` **当前存在但为空（0 字节）** → 建仓/推送步骤需要用户写入 PAT（或改用其它授权方式）。

## 12. 待确认（开工前）
1. **PAT**：`C:\Users\Dogay\.dsh\github_token.txt` 为空 → 何时写入？（阻塞 GitHub 交付，不阻塞编码）
2. 工程目录：默认 `D:\deepseek\waystone_xp_payment`（独立 git 仓库），是否可用？
3. 默认流体白名单：`#c:experience` + `create_enchantment_industry:experience` 是否可接受？（用户 Q3 未答）
4. 设备命名：`xp_payment_tank`（显示名"经验流体储罐 / XP Payment Tank"）是否合意？
5. 许可证：MIT 是否可接受？
6. 设备获取方式：首版配方（暂定 1 桶 + 4 玻璃 + 1 铁锭 + 1 漏斗类）是否合意；是否需要"仅创造模式可得"。
