# SOP：给内置默认数据包登记一种经验流体

内置表是「玩家什么都不装也能用」的那一份兜底清单，路径
`src/main/resources/data/waystone_xp_payment/recipe/builtin/*.json`。它不是收藏品清单：
**每加一条，就等于替所有玩家向那个 mod 的流体承诺了一个汇率**，所以这条 SOP 的重点全在
「怎么把 id 和汇率证出来」，而不是「怎么抄 JSON」。

## 0. 什么时候该加

| 该加 | 不该加 |
|---|---|
| 流体就是**玩家经验**的容器（灌进去 = 存 XP，抽出来 = 还 XP） | 名字里有 experience、但语义是别的东西（附魔之源、怪物精华、研究点） |
| 该 mod 在 1.21.1 / NeoForge 上有稳定版本，且被主流整合包使用 | 只在 1.20.1 或 Fabric 上存在，或已停更 |
| 你能实测出「1 桶 = 多少 XP」 | 只能查到「大概是 1 mB = 1 XP」 |

最后一行是硬门槛。内置表错了不会崩存档，但会让服主在不知情的情况下多收或少收经验，
这正是 0.3.x 时代 `Math.max(1, rate)` 造成过的错（浓流体被当成 1 mB/XP，错 500 倍且无日志）。

## 1. 确认流体 id

只认**精确 id**（`命名空间:路径`），标签（`#c:experience`）从 0.4.0 起完全无效。三种取证方式，
按可靠性排序：

1. **灌进本模组的储罐再读方块数据**（最可靠）：
   ```
   /data get block <x> <y> <z> Fluid
   ```
   输出里 `id: "modid:fluid_name"` 就是它。
2. **看 mod 自己的语言文件**：解压 jar，`assets/<modid>/lang/en_us.json` 里出现
   `"block.<modid>.<name>": "Liquid XP"` / `"item.<modid>.<name>_bucket": "Bucket of Experience"`
   这类键，说明该 mod 注册了同名流体；`.json` 里 `data/c/tags/fluid/experience.json` 列出谁加入了
   `c:experience` 标签——**只当线索**，标签说明「社区认为它是经验」，不说明汇率。
3. **看本 mod 自己的日志**：一次成功的传送会打印
   `Teleport paid from the source tank at ...: create_enchantment_industry:experience -> ...`，
   已经把 id 写出来了。

flowing / still 不需要各写一条：声明 `x:xp_fluid`，`x:flowing_xp_fluid` 自动同率（反之亦然）。

## 2. 量出「1 桶值多少 XP」

**不许抄别的 mod 的数。** 同一个数字在不同 mod 里语义可能相反（有的 1 桶 = 100 XP，有的 1 桶 = 1000 XP）。
唯一可信的做法是实测一次：

1. 用该 mod 自己的接口把流体换回玩家经验（例如它的「抽取」按钮、泵、桶右键）。
2. 记录「抽了多少 mB」和「涨了多少 XP」。
3. `xp_per_bucket = XP × 1000 / mB`。允许小数（内部按千分之一 XP 存整数，不会丢精度到「算不出 0.5」）。

> 实测样例（本 mod 已验证）：把 `create_enchantment_industry:experience` 的储罐抽 12 mB，
> 玩家涨 12 XP ⇒ `12 × 1000 / 12 = 1000`，即 `xp_per_bucket: 1000`。

如果没法在游戏里实测，就**不要加**——把它留在候选清单里，等有人验证。

## 3. 写声明文件

路径：`src/main/resources/data/waystone_xp_payment/recipe/builtin/<modid>_<fluid>.json`
（文件名不带 `builtin/` 前缀也无妨，但保持「一个流体一个文件、名字能一眼对上 id」）。

```json
{
  "type": "waystone_xp_payment:xp_fluid",
  "fluid": "modid:fluid_name",
  "xp_per_bucket": 1000
}
```

规则：

- 两个字段都必填；写错字段名（例如旧的 `mb_per_xp`）会让整条声明被原版解析器拒绝并报
  `No key xp_per_bucket`，看起来像「mod 没生效」。
- `xp_per_bucket` 取值 1–1000000，允许小数。内置表里**不要写 0**：`0` 是「注销」，
  语义是覆盖别人，放在最低优先级的内置表里没有意义。
- 一个流体一条，不要在一个文件里堆多条（覆盖规则按 recipe id 排序，堆在一起会让谁赢变得难读）。

## 4. 让测试看住它

`src/test/java/com/olynk/waystonexppayment/recipe/BuiltinDeclarationsTest.java` 会遍历内置目录，
逐条断言：`type` 正确、`fluid` 是合法资源位置、`xp_per_bucket` 在 1..1000000、流体不重复、
目录非空。新增一条之后：

- 结构问题会被它直接抓出来，不需要额外写测试；
- 若该测试里还有一份「期望流体清单」的显式断言，把新 id 补进去。

## 5. 本地验证

```powershell
Push-Location 'D:\deepseek\waystone_xp_payment'
$env:GRADLE_USER_HOME='D:\deepseek\.gradle-home'
$env:JAVA_TOOL_OPTIONS='-Duser.language=en -Duser.country=US'
.\gradlew.bat test --offline --console=plain
.\gradlew.bat runServer --offline --console=plain
```

启动日志里应有：

```
Loaded 4 experience fluid declaration(s), 4 fluid(s) in the table
```

条数 = 内置条数 + 开发服 `run/world/datapacks/` 里的条数。若把一条写成旧格式，这里会先出现
`Parsing error loading recipe ...: No key xp_per_bucket`，然后计数少一条。

验证「覆盖」时再放一个同名流体、不同 `xp_per_bucket` 的包，重启后应看到两条
`declared ...` 加一条 `WARN ... declared more than once`，最终生效的是包位置靠后的那条
（这条顺序目前只有单测固化，跨真实数据包仍未实机验证 `[NEEDS VERIFICATION]`）。

## 6. 文档

- `docs/数据包契约-经验流体.md`：若新增了「常见流体」说明，补一行；
- `CHANGELOG.md`：`## Unreleased` 下 `Changed`/`Added` 记一句；
- `docs/变更记录/<简述>_<YYYY-MM-DD>.md`：四问式，写清**怎么量出这个汇率的**（这是以后唯一能复查的证据）。

## 7. 提交流程

分支 `feat/builtin-<fluid>` → commit（英文，四问式）→ PR → CI（`build` + `language` + CodeQL）→
rebase 合并 → 删除分支。只有 JSON 变化时不必重新部署 jar，除非同时改了代码。

## 8. 常见坑

| 坑 | 症状 | 处理 |
|---|---|---|
| 抄了别的 mod 的汇率 | 玩家多付/少付，无报错 | 重做第 2 步的实测 |
| 用了标签或模糊名 | 装与不装某 mod 表现不同 | 改成精确 id |
| 忘了 mod 更新会改汇率 | 老声明变成错价 | 大版本更新后重测，必要时在 CHANGELOG 标注 |
| 把 «mob essence» / «附魔之源» 当经验 | 罐子能付款但换不回玩家经验 | 不属于本表 |
| 文件写在 `data/<ns>/recipe/` 之外的目录 | 静默不加载 | 必须在 `recipe/` 下 |
| 直接改 `run/` 里的开发数据包就以为改了内置表 | 只在本地生效 | 内置表在 `src/main/resources/` |

## 附录 A：当前内置清单（2026-11-03）

| 流体 id | `xp_per_bucket` | 来源依据 | 状态 |
|---|---|---|---|
| `create_enchantment_industry:experience` | 200 | 实测：10 XP 换 50 mB（机主 2026-10-31）；旁证：10 mB 注一瓶附魔之瓶 | 已验证 |
| `industrialforegoing:essence` | 10 | 实测：10 XP 换 1000 mB；旁证：250 mB 一瓶附魔之瓶 | 已验证 |
| `cyclic:xpjuice` | 10 | 同上（同一个兑换器） | 已验证 |
| `openblocks_reborn:xp_juice` | 10 | 同上 | 已验证 |
| `enderio:fluid_xp_juice_still` | 10 | 同上；声明 still 侧，`_flowing` 由 counterpart 规则自动同率 | 已验证 |
| `beyonddimensions:xp_fluid` | 10 | 机主实测：与 Ender IO 等四种同价 | 已验证 |
| `sophisticatedcore:xp_still` | 10 | 机主实测：与 Ender IO 等四种同价 | 已验证 |

两条换算基准都是「**造出这份流体要花多少经验**」（正向兑换），不是「抽回来能得多少」，因为正向是机主实测到的、
反向没有被测过。两者的差别在附魔之瓶上很明显：

- CEI：`xp_per_bucket 200` ⇒ 10 mB ≈ 2 XP 的标价，但 10 mB 能注出一瓶附魔之瓶（3–11 XP）——瓶子比标价值钱；
- 另外四种：`xp_per_bucket 10` ⇒ 250 mB ≈ 2.5 XP 的标价，一瓶附魔之瓶同样 3–11 XP。

也就是说四种「便宜」流体的瓶子套利空间更大。这不影响本 mod 的账：储罐按标价付传送费，
付得少只是玩家更省，不会凭空造经验；但如果哪天要按「瓶子价」定价，改的就是这张表里的数字。

另外两种（Beyond Dimensions、Sophisticated Core）原先沿用了 CEI 的旧假设 1000，机主 2026-11-03 实测它们
与上面四种「便宜」流体同价，已一并改为 10。至此七条全部有实测依据，表里不再有假设值。

换新的流体仍然按第 2 步取证：把 1000 mB 抽成玩家经验数一下涨多少，`xp_per_bucket = XP × 1000 / mB`。

## 附录 B：生态候选（2026-10 检索，未进内置表）

检索方式：① 扫描整合包内 jar 的经验流体贴图；② 联网检索 1.21.1 / NeoForge 的「液态经验」类 mod。
第 ① 步可以复用仓库里的脚本：

```powershell
.\tools\find-xp-fluids.ps1 -ModsDirectory '<实例>\minecraft\mods'
```

它只按贴图文件名找**候选**（`*_still.png` / `*_flow*.png` 里带 xp / experience / juice / essence），
命中不等于有效——仍然要回到第 1、2 步取证。

已进表（2026-10-31 实测）：Create: Enchantment Industry、Industrial Foregoing、Cyclic、
OpenBlocks Reborn、Ender IO。仍未验证的候选：

| mod | 线索 | 缺什么 |
|---|---|---|
| Reliquary | 长期有 XP juice | 1.21.1 NeoForge 版本是否还在、id、汇率 |
| Liquid Experience（Modrinth 独立 mod） | 名字与功能都符合 | 支持的版本、id、汇率 |
| XP Works | 名字指向经验 | 同上；且需确认是流体而非物品 |

参考：[Liquid Experience (Modrinth)](https://modrinth.com/mod/liquid-experience)、
[液态经验 · 循环 (Cyclic)](https://www.mcmod.cn/item/102645.html)、
[液态经验 · 机械动力：附魔工业](https://www.mcmod.cn/item/640223.html)、
[液态经验 · 超越维度](https://www.mcmod.cn/item/887732.html)。

想加其中任何一个，回到第 1 步重新取证。
