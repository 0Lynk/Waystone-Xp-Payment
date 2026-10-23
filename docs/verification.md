# Verification — waystone_xp_payment

Every claim in `README.md` / `CHANGELOG.md` should be traceable to something below. Commands are the ones
actually run; the environment caveats that change how a command looks are stated where they matter.

**Current release: 0.3.0.** Section 0.3 below is the record for it; section 0 is the 0.2.0 record; sections 1-4 were
written for 0.1.0 and are kept as they stand. Where they disagree on a number (test count, jar size), section 0.3 wins,
then section 0, then the older ones.

## 0. The 0.2.0 release (2026-10-11)

0.2.0 turned the access screen into an owner-gated, UUID-backed list (row selection + row menu, team member
panel, filter picker, status line) and fixed three reported client bugs, two of which disconnected the player. `CHANGELOG.md` lists the changes; the release
round itself is recorded in `docs/变更记录/2026-10-11_v0.2.0发版准备.md`. One user-visible detail worth writing
down: the row-menu glyph is drawn as three `·` (U+00B7) because `⋯` (U+22EF) is not in the 1.21.1 default font
atlas.

| step | command | result |
|---|---|---|
| full build incl. the unit tests (this is what CI runs) | `gradle build --offline` | **BUILD SUCCESSFUL in 6s** (`6 actionable tasks: 3 executed, 3 up-to-date`) |
| unit tests | part of the same `gradle build` | **55 tests, 0 failures, 0 errors** (`build/test-results/test/*.xml`): `FluidRateTableTest` 17, `PendingRequestTest` 12, `ContextMenuLayoutTest` 11, `PaymentSplitTest` 8, `XppMembersStatusTest` 6, `MixinShadowPolicyTest` 1 |
| jar | — | `build/libs/waystone_xp_payment-0.2.0+mc1.21.1.jar`, 155,417 bytes, sha256 `e6012a103bf25096555bcafbbfad4e5399abcaec304692782a5089f0cfbf8118` |
| version inside the jar | read `META-INF/neoforge.mods.toml` out of that jar | `version = "0.2.0"`, expanded at build time from `mod_version` in `gradle.properties:13` |
| published asset | GitHub release `v0.2.0` (tag on `5a96493`) | `state=uploaded`, `size=155417`, `digest=sha256:e6012a10…` — byte-identical to the local jar |
| modpack deployment | the 0.2.0 jar copied into the `mods` folder of a real 1.21.1 pack (Waystones 21.1.45, FTB Teams 2101.1.11) | same sha256 on both sides; **the in-game retest is the pack owner's, not done here** |

### Guards added in 0.2.0

`MixinShadowPolicyTest` walks every `@Mixin(X.class)` in the main source set and asserts that each `@Shadow`
member is declared by `X` itself. The bug it guards against: `WaystoneButtonClientMixin` shadowed
`protected boolean active`, which `net.blay09.mods.waystones.client.gui.widget.WaystoneButton` does not declare
(it comes from `AbstractWidget`), so the mixin still compiled but failed at apply time —
`InvalidMixinException @Shadow field active was not located in the target class
net.blay09.mods.waystones.client.gui.widget.WaystoneButton` — and every client that opened the teleport list was
disconnected. The guard was reverse-tested: putting the illegal shadow back fails it immediately. So that the
test can load its targets, `build.gradle`'s `test` task now adds `sourceSets.main.compileClasspath`.

### Not verified for 0.2.0 (do not claim otherwise)

- The 0.2.0 access screen as *rendered*: row selection, the `···` row menu, the member panel, the four status-line
  states and the three-stage `Esc` order were exercised by unit tests and static reading only.
- The FTB Teams reflective path (`Team.getMembers`, `Team.getOnlineMembers`, `TeamManager.getTeamByID`) was only
  checked with `javap` against `ftb-teams-neoforge-2101.1.11.jar`; it has never run in a game with FTB Teams
  loaded.
- Whether `registrar("2")` (`event/XppModEvents.java:49`) takes part in NeoForge's payload-version handshake is
  assumed, not measured.
- Everything in section 3's "Not covered by this smoke test" still stands for 0.2.0: no player teleports, no
  client, no pipe-filled tank.

## 0.3 版本记录（0.3.0：2026-10-17 发布，2026-10-19 实机验收）

0.3.0 把两端协议从 3 跳到 4（目的地罐客户端提示 + 预加载查询），并修掉「未加载区块里目的罐被判到错槽位」「源端无罐时第一次点击必失败」「成功加载被记成失手」三项。改动清单见 `docs/变更记录/2026-10-17_发布0.3.0.md`，实机账目见 `docs/测试记录/2026-10-19_0.3.0实机验收.md`。本节只收可复核的事实。

| step | command | result |
|---|---|---|
| 构建 | `gradle build --offline`（沙箱外：`GRADLE_USER_HOME=D:\deepseek\.gradle-home`、`JAVA_TOOL_OPTIONS='-Duser.language=en -Duser.country=US'`） | `build\libs\waystone_xp_payment-0.3.0+mc1.21.1.jar`，**192,657 B**，sha256 `07d698351fc290e859b1de9b1e95d762e48eece6bb8b6e1973030f865b5285d5`，mtime 2026-10-19 22:35 |
| 单测 | 同一次 build；解析 `build\test-results\test\*.xml`（20 个类，全部写于 22:35:42） | **119 tests / 0 failures / 0 errors / 0 skipped** |
| jar 内版本 | 解 jar 读 `META-INF/neoforge.mods.toml` | `version = "0.3.0"` |
| 已发布资产 | GitHub Release `v0.3.0`（id 415645925，tag `54aac34`，2026-10-17T07:11:42Z） | 唯一资产 `waystone_xp_payment-0.3.0+mc1.21.1.jar`，**189,878 B**，`digest=sha256:71fb6dbeb0fb6e0ebd65859114b0bc82ac980c24529617417b08f290174bf818`，下载数 1；下载到本地实测 hash 与 digest 一致 |
| 三处部署（实际六处逐一比对） | 正式服 `D:\Minecraft\重度机霸症Server\mods\`；客户端 `PrismLauncher-…-11.1.1\instances\{1.21.1,1.21.1-fork}\minecraft\mods\`；客户端 `ShatteredPrism-…-1.9.1\instances\{1.21.1,1.21.1-fork}\minecraft\mods\`；本仓 `build\libs\` | 六处全部 **192,657 B / `07d69835…`**，一致（`instances\中毒机霸症\` 是未更新的旧测试实例，仍留 0.2.0） |
| **附件 vs 部署产物** | 直接读两个 jar 的条目表与类大小（`System.IO.Compression`） | **❌ 不一致**：126 vs **127** 条目；`payment/TankPlacement$RecordedHalf.class` **仅部署 jar 有**；`TankPlacement.class` 1,355→**2,741** B；`PayerBudget.class` 2,760→**2,878** B；`event/XppTeleportHandler.class` 8,745→**10,137** B；两者 `neoforge.mods.toml` 都写 `0.3.0` |
| 实机验收（服务端日志） | `Select-String latest.log -Pattern 'paid from the destination tank'` | 7 条，目的地罐坐标全为 `1186, 64, 1212`；其中两组间隔 < 5 s（4.108 s / 4.798 s）均由目的地罐付款 |

### 0.3.0 的产物身份问题（先看这条再信其他数字）

**已发布的 0.3.0 附件不是被验收的那个 jar。** tag `v0.3.0` 打在 `54aac34`，而部署与验收用的是其后 4 个提交（`c60e2dc` 未加载区块罐位候选、`85c3406` 驻留区块不耗配额、`4710981` 只为失手记防抖、`2afeb49` 归因与拒绝加载日志）构建的 `2afeb49`。换言之第四~六轮（M3/M4/M5）的修复只存在于部署产物，**不在任何已发布资产里**。直接后果：`测试流程.md` 的 I2 与 I4 均不通过；任何人从 Release 安装都会装到仍会把未加载区块的目的地罐判到错槽位的版本。本条的结论是「对外分发未放行」，只影响已经装在六处的 HEAD 构建。

### Not verified for 0.3.0（不要声称验证过）

- **已发布的 Release 附件从未在任何环境跑过**（下载数 1 就是本次核对自己下的）。
- E7（等级要求仍由玩家出）、E8（并发双人同罐）**未测**；E8 记为「未测（环境受限）」，理由见测试记录 §6 —— `E-1` 缺陷未修，双客户端两账号条件已具备，只差同 tick 时序，不能记成「不需要测」。
- E3（罐+玩家都不够）、E4（rollback 不吞流体）、E6（创造模式免费）**只有口头**：这三条走的是静默分支，日志里 `cannot pay`/`DENIED`/`not enough`/`Insufficient`/`rollback` 各 0 条，缺失败前后 `/xp query` 与 `/data get block`。
- M1（非 owner 的 OP 编辑他人罐，D6/C-1）本轮未复测，上一轮为口头通过 —— **证据不足，不算闭环**。
- 界面（F 组全部状态）、成员面板（M2）、D3/D4/D5/D7/D8/D10 本轮无截图。
- E13（同一目的地 5 s 内只加载一次）未取证；E14/E15/E16 未测，协议 3→4 的握手实测仍未做。
- 未修项：`E-1`（零代价窗口）、`D9`/`F7`（越距校验）、`S6`（汇率重建后 `cachedPoolXp` 不重算）、`Q-7`（队伍条目 UUID 回落到 36 字符）。
- 0.2.0 遗留仍然成立：FTB Teams 反射路径从未在装了 FTB Teams 的游戏里跑过；`registrar("4")` 是否参与 NeoForge 的 payload 版本握手仍是假设。



## 1. Build (0.1.0)

| step | command | result |
|---|---|---|
| Minecraft/NeoForge artifacts (decompile, apply patches, AT + jst transform, recompile 5364 sources) | `gradle createMinecraftArtifacts` | **BUILD SUCCESSFUL in 58s** |
| full build incl. the unit tests (this is what CI runs) | `gradle build` | **BUILD SUCCESSFUL in 10s** (latest run, after the NBT fix) |
| jar | — | `build/libs/waystone_xp_payment-0.1.0+mc1.21.1.jar`, 81,759 bytes |

### Environment caveats (why the commands look unusual)

- **The build has to run outside the harness file sandbox.** Inside it `Files.isWritable()` returns `false` for
  every path, and JDK zipfs computes `readOnly = !writeable`
  (`jdk/nio/zipfs/ZipFileSystem.java:170-174`). NeoForm's `RecompileSourcesActionWithJDK` therefore cannot write
  class files into its `output.jar` and the build dies with
  `ERROR Line: 1, error while writing net.minecraft.world.ticks.package-info: null in /net/minecraft/world/ticks/package-info.java`
  → `java.io.IOException: Compilation failed`. Verified with a probe JVM: same file, same user,
  `isWritable=false` inside the sandbox and `isWritable=true` outside it.
- Earlier in the session `maven.neoforged.net` was unreachable, which is why some runs used `--offline` plus a
  machine-local init script (`$GRADLE_USER_HOME/init.d/fix-repos.gradle`, deliberately *not* in this repository).
  The host came back and the canonical `gradle build` (tests included) now runs online.

## 2. Unit tests (0.1.0)

| runner | command | result |
|---|---|---|
| Gradle (canonical) | `gradle build` → `:test` | `FluidRateTableTest` 11/11, `PaymentSplitTest` 8/8 — **19 tests, 0 failures, 0 errors** (`build/test-results/test/*.xml`) |
| JUnit console (used while the repository was unreachable) | `java -jar junit-platform-console-standalone-1.10.2.jar execute --class-path "build\classes\java\main;build\classes\java\test" --select-package com.olynk.waystonexppayment` | 19 tests successful, 0 failed |

Both test classes are pure logic (no Minecraft types on their paths), which is why they can run either way.

## 3. Dev server smoke test (0.1.0) — PASSED (with an explicit list of what it does *not* cover)

`gradle runServer` boots a dedicated server with the mod (log: `D:\deepseek\.tmp\runserver5.log`,
`runserver6.log`, `runserver7.log`).

```
[21:27:32] [modloading-worker-0/INFO] [co.ol.wa.WaystoneXpPaymentMod/]: waystone_xp_payment 0.1.0 initialized: Waystones teleports can be paid from experience fluid tanks
[21:27:39] [Server thread/INFO] [minecraft/DedicatedServer]: Done (5.233s)! For help, type "help"
[21:30:07] [Server thread/INFO] [minecraft/RconThread]: RCON running on 0.0.0.0:25575
[21:32:00] [Server thread/INFO] [co.ol.wa.WaystoneXpPaymentMod/]: Loaded 1 recipe-declared experience fluid rate(s): [minecraft:water=10]
```

With RCON enabled (`run/server.properties`) the running server was then queried directly:

```
> setblock 0 100 0 waystone_xp_payment:xp_payment_tank
Changed the block at 0, 100, 0
> data get block 0 100 0
0, 100, 0 has the following block data: {access: {teams: [], players: [], whitelist: 1b}, x: 0, y: 100, z: 0,
  id: "waystone_xp_payment:xp_payment_tank"}
> setblock 0 101 0 waystones:waystone
Changed the block at 0, 101, 0
> data merge block 0 100 0 {Fluid:{id:'minecraft:water',amount:1000}}
Modified block data of 0, 100, 0
> data get block 0 100 0
0, 100, 0 has the following block data: {access: {teams: [], players: [], whitelist: 1b}, Fluid: {amount: 1000,
  id: "minecraft:water"}, x: 0, y: 100, z: 0, id: "waystone_xp_payment:xp_payment_tank"}
> data merge block 0 100 0 {access:{whitelist:0b}}
Modified block data of 0, 100, 0
> data get block 0 100 0
0, 100, 0 has the following block data: {access: {teams: [], players: [], whitelist: 0b}, Fluid: {amount: 1000,
  id: "minecraft:water"}, ...}
```

That proves, on a live server: the block and block entity are registered and placeable; the fluid tank and the
access list both persist and round-trip through save/load; Waystones is loaded next to us; and the
data-driven side works (`waystone_xp_payment:xp_fluid` recipe parsed from a datapack, rate table rebuilt both
on server start and after `/reload`). `run/config/waystone_xp_payment-common.toml` is generated with the
documented keys and defaults.

This smoke test exposed and then re-verified one real defect:

- **Tank NBT layout** — `saveAdditional` used to write `FluidTank.writeToNBT(...)` into a *nested* `tank` tag
  (`{tank:{Fluid:{...}}}`), which persisted fine but made the block entity unreadable/unwritable through
  vanilla `/data`. Now the tank helpers receive our own tag (`{Fluid:{...}}`, the conventional layout), which the
  last RCON session above verifies.

### Not covered by this smoke test (needs a player or a GameTest, do not claim it works)

1. An actual teleport paying from the tank (fluid decreasing, player XP unchanged) — no player can be present
   on this headless server.
2. The access screen and its buttons.
3. The FTB Teams path (FTB Teams is not installed here).
4. The `NotEnoughXp` cancellation path with a partially filled tank.
5. Filling the tank through the fluid capability with pipes (no tech mod installed here); the fluid was placed
   through NBT instead, which exercises `FluidTank`/`FluidStack` serialisation but not the capability call path.

### Client-side fixes verified statically only

The right-click disconnect (`XppStatePayload` encoding an empty `FluidStack`) and the greyed-out teleport button
(Waystones checks affordability on the *client*) were fixed after reports from a real modpack, but no client was
ever run here, so the client rendering path is **not** runtime-verified. What was checked instead:

- `FluidStack.java:123-142` (NeoForge sources) proves the plain `STREAM_CODEC` throws for an empty stack, and
  `94-119` proves `OPTIONAL_STREAM_CODEC` encodes it as a varint 0.
- `javap` on the modpack's own `ftb-teams-neoforge-2101.1.11.jar` resolves every reflective lookup the fixed
  `FtbTeamsResolver` performs (`ALL-REFLECTION-OK` probe against that jar).
- `javap` on the modpack's own `waystones-neoforge-1.21.1-21.1.45.jar` shows the mixin target
  `public static WarpRequirement WaystonesAPI.resolveRequirements(WaystoneTeleportContext)` exists, and the built
  jar contains `waystone_xp_payment.mixins.json` plus `[[mixins]] config = ...` in `neoforge.mods.toml`.

The client behaviour itself (button enabled, tooltip, screen) still needs a human at a client: if the mixin fails
to apply, the log shows `Mixin apply failed` for `waystone_xp_payment.mixins.json` during client startup.


## 4. Retrying the whole thing

```powershell
$env:GRADLE_USER_HOME='D:\deepseek\.gradle-home'; $env:TEMP='D:\deepseek\.tmp'; $env:TMP='D:\deepseek\.tmp'
& 'C:\Users\Dogay\.gradle\wrapper\dists\gradle-8.14.1-bin\baw1sv0jfi8rxs14qo3h49cs\gradle-8.14.1\bin\gradle.bat' `
  build --console=plain --no-daemon          # tests included
& 'C:\Users\Dogay\.gradle\wrapper\dists\gradle-8.14.1-bin\baw1sv0jfi8rxs14qo3h49cs\gradle-8.14.1\bin\gradle.bat' `
  runServer --console=plain --no-daemon      # then query it over RCON on port 25575
```

Windows note: the build must not run inside the harness file sandbox (see section 1).
