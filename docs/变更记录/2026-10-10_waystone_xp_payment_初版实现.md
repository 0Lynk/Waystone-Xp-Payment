# 2026-10-10 waystone_xp_payment 初版实现

## 改了什么

从空目录建成一个可构建、可测试、可启动服务器的 NeoForge 1.21.1 / Waystones 附属模组。

- 构建层：`build.gradle`、`settings.gradle`、`gradle.properties`、Gradle Wrapper、`.gitignore`
- 元数据：`src/main/resources/META-INF/neoforge.mods.toml`、`pack.mcmeta`
- 模组入口：`WaystoneXpPaymentMod.java`（注册配置、注册表、Balm 事件订阅）
- 配置：`config/XppConfig.java`（COMMON spec）、`config/XppSettings.java`（不可变快照 + 默认值兜底）
- 流体计价：`fluid/FluidRateTable.java`（纯逻辑查找表）、`fluid/FluidRates.java`（运行时视图：配置 + 配方声明）
- 支付：`payment/PaymentSplit.java`（纯计算）、`payment/XpRequirementScanner.java`（读已解析的需求树）、
  `payment/FluidPaymentRequirement.java`（先池后玩家、付不起整笔取消）
- 授权：`auth/AccessList.java`（白/黑名单 + NBT）、`auth/TeamResolver.java`、`auth/TeamResolvers.java`（隔离加载）、
  `auth/FtbTeamsResolver.java`（FTB Teams 软依赖）
- 注册与方块：`registry/XppRegistry.java`、`block/XpPaymentTankBlock.java`、
  `block/entity/XpPaymentTankBlockEntity.java`（FluidTank + 能力 + NBT）
- 事件与命令：`event/XppTeleportHandler.java`、`event/XppModEvents.java`、`event/XppGameEvents.java`、
  `command/XppCommands.java`
- 数据驱动：`recipe/XpFluidRecipe.java`、`recipe/XpFluidRecipeSerializer.java`
- 资源：方块状态/模型/贴图（16×16 程序生成）、`lang/en_us.json`、`lang/zh_cn.json`、储罐合成配方
- 测试：`PaymentSplitTest`（9 例）、`FluidRateTableTest`（10 例）
- 交付文档：`README.md`（漏斗顺序：做什么 → 为什么在乎 → 怎么用 → 怎么装）、`LICENSE`（MIT）、
  `CHANGELOG.md`、`.github/workflows/build.yml`

## 为什么改

用户需求：做一个 Waystones 附属，让"传送方块正下方的设备"用共享经验流体池代替玩家个人经验支付传送费；
接受流体可由配置文件**或** KubeJS/数据包注册；必须能自己操作 GitHub 交付私有仓库。
设计决策与依据见 `docs/design.md`（ADR-1..12）。

## 改动前后对比

| | 改动前 | 改动后 |
|---|---|---|
| 仓库 | 只有 `docs/design.md` | 可构建的模组源码 + 资源 + 测试 + CI |
| 构建 | 无 | `gradle build -x test --offline` → BUILD SUCCESSFUL，产出 `build/libs/waystone_xp_payment-0.1.0+mc1.21.1.jar` |
| 测试 | 无 | 19 个纯逻辑单测全绿（见 `docs/verification.md`） |
| 服务器 | 无 | `gradle runServer` 可启动并加载模组（冒烟日志见 `docs/verification.md`） |

## 根因分析与教训

1. **Balm 必须显式声明**：`compileJava` 报 `cannot access BalmEvent` / `cannot find symbol: variable Balm`。
   根因是 Waystones 的 POM 把 `balm-neoforge` 声明为 **runtime** 依赖，因此它不在 `compileClasspath` 上，
   而我们编译期要用 `BalmEvent` 与 `Balm.getEvents()`。修复：`implementation "net.blay09.mods:balm-neoforge:…"`。
   教训：软/硬依赖的**传递作用域**不能靠"上游能编译"来推断，必须看 POM 的 scope。
2. **沙箱内无法构建 NeoForge**：文件沙箱让 `Files.isWritable()` 对所有路径返回 `false`，
   而 JDK zipfs 用 `readOnly = !Files.isWritable(zip)` 判定可写性（`jdk/nio/zipfs/ZipFileSystem.java`），
   NeoForm 的 `recompile` 因此把 `output.jar` 当只读，编译产物写不进去 →
   `error while writing …: null`（`ReadOnlyFileSystemException` 无 message）→ `IOException: Compilation failed`。
   规避：构建/运行一律在沙箱外执行（本机已确认沙箱外 `isWritable=true`）。
3. **依赖仓库 TCP 黑洞会挂死 Gradle**：`maven.neoforged.net`（202.160.130.145:443）在本机短暂不可达时，
   Gradle 会停在 `HttpClientHelper.executeGetOrHead`，表现为任务"无输出卡住"。
   `--offline` 是关键诊断手段（它直接报出当时在下载的构件是 JUnit 引擎）。
4. **工具限制**：`bash` 工具在本机不可用（`ctx.shell.execute is not a function`），全程改用 `pwsh`；
   PowerShell/curl 的 HTTPS 不可用（schannel `SEC_E_NO_CREDENTIALS`），联网统一走 Node `fetch`。
