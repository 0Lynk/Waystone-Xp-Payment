# waystone_xp_payment — 协作约定（每个会话自动加载，保持精简）

NeoForge 1.21.1 的 Waystones 附属模组（XP 流体付款 + 储罐访问界面）。
构建 / 部署 / 发布细则见 `docs/变更记录/`；本文件只放**长期规则**。

## 交付节奏（省 token）
- **一轮功能 = 一个 PR + 一篇变更记录**：同一轮的多个小修攒在一起提交，不逐个开 PR、不逐条轮询 CI。
- PR 流程固定：branch from origin/main → push（PAT extraheader）→ 建 PR → 轮询 mergeable → `PUT merge {merge_method:rebase}` → 删远程分支 → 本地 `reset --hard origin/main`。
- 未经用户确认不发 Release；发版前必须补齐当轮变更记录。

## 上下文纪律
- **工具输出一律落盘**：GitHub 走 `node D:\deepseek\_tools\gh.js --url <api-url> [--method POST] [--body json] [--out 落盘路径] [--show 字段,…]`（stdout 只回一行：`STATUS … | 选中字段 | SAVED <路径>`；必须用具名参数，PowerShell 会丢掉空字符串位置参数），日志用 `Select-String` 过滤，禁止把 JSON / 整文件正文打进上下文。
- 先 `grep` 后 `read`；读大文件必须带 offset/limit；已被压缩块覆盖的内容不重读。
- 独立工具调用合并进同一次请求；`delivery_check` 一次成型（`external.target` 必须是本地路径、`numeric.result` 必须是纯数字、`file` 传 .md）。
- 折叠只在可折内容 ≥30K 时做，一次多段；CI 只查一次。
- 汇报默认 ≤600 字；预览图一轮最多一张（多状态拼成一张）。

## 子代理
- 任务书 ≤300 字要点式：目标 / 契约文件路径 / 我方取舍覆盖 / 禁止事项 / 汇报格式。
- 要求回报 ≤500 字结构化：改了哪些文件 + 跑过的命令 + 结果 + **未验证项**；不要贴代码与日志。

## 代码风格
- **基准**：Google Java Style。4 空格缩进、行宽 120、import 分组（java / javax / 第三方 / `net.minecraft` / `com.olynk`）组间空行、无通配符 import。
- **分支**：≤3 分支用 if-else 与提前返回的卫语句；≥4 分支或按类型分发才上 switch / 策略。
- **状态**：不可变优先——`final` 字段、record、`List.of` / `List.copyOf`；可变状态必须能说出为什么。
- **错误处理**：可恢复的失败用返回值表达（`null` 或空集合，并在 javadoc 写清契约），不可恢复抛异常；反射 / 网络边界允许 catch 后记日志降级，但必须带上被降级的对象与原因。
- **注释**：只写 WHY（TODO / 出处引用 / 正确性论证 / 教训 / 魔法常量理由 / 承重细节 / 为什么不用 X）。公开 API 的 javadoc 写契约与空值约定，不写实现步骤。
- **可测接缝**：需要脱离 `MinecraftServer` 单测的规则抽成包级纯函数（`editAllowed`、`PlayerNames#label`、`pickPool`），并在 javadoc 里说明为什么和调用方分开。
- **禁止**：`System.out` / `printStackTrace`（走 `WaystoneXpPaymentMod.LOGGER`）、协议版本写裸字面量、`catch (Exception e) {}` 吞异常。

## 语言约定
- **文档用中文**：`docs/` 下的正文、文件名、目录名，以及本文件。
- **git 历史用英文**：commit message、PR 标题与正文（合并后的 commit body 就是它）。
- **代码用英文**：包名、类名、字段名、局部变量、注释与 javadoc；`src/` 下的文件名与目录名。
- **例外**：`src/main/resources/assets/waystone_xp_payment/lang/*.json` 按语言保留本地化文本。
- 历史提交里的中文标题不回改（改写要 force-push）；规则从本文件入库后向前生效。

## 实机
- 构建必须沙箱外：`GRADLE_USER_HOME=D:\deepseek\.gradle-home`、`JAVA_TOOL_OPTIONS='-Duser.language=en -Duser.country=US'`、`gradle build --offline`。
- 产物覆盖测试实例 `D:\Minecraft\ShatteredPrism-Windows-MinGW-w64-Portable-1.9.1\instances\中毒机霸症\minecraft\mods\`，源/目标 sha256 必须一致，再让用户**完全重启客户端**复测。
