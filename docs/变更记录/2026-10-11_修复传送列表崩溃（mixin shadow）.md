# 修复：第一次打开传送列表就把客户端踢出去（mixin @Shadow 解析失败）

日期：2026-10-11
版本：0.1.0（未发版分支上的修复）

## 现象

玩家在储罐界面配置完权限后右键石碑，弹出错误界面并被踢出：

```
打开带有高级数据的屏幕失败：
org.spongepowered.asm.mixin.transformer.throwables.MixinTransformerError: An unexpected critical error was encountered
```

单人存档同样发生（不是服务端问题），且**重启后依然**——只要打开传送列表就复现。

## 根因

`WaystoneButtonClientMixin` 声明了一个目标类里根本不存在的影子字段：

```java
@Shadow
protected boolean active;
```

`active` 由 `net.minecraft.client.gui.components.AbstractWidget` 声明
（`AbstractWidget.java:37 public boolean active = true;`，且**没有 setter**），
`WaystoneButton` 只是继承它。Mixin 的 `@Shadow` 只解析**目标类自己声明**的成员，
继承来的找不到，于是：

```
[Render thread/FATAL] [mixin/]: Mixin apply for mod waystone_xp_payment failed
waystone_xp_payment.mixins.json:WaystoneButtonClientMixin from mod waystone_xp_payment
-> net.blay09.mods.waystones.client.gui.widget.WaystoneButton:
InvalidMixinException @Shadow field active was not located in the target class
net.blay09.mods.waystones.client.gui.widget.WaystoneButton. No refMap loaded.
```

（`logs/latest.log:3797`；`:3922` 是 `MixinApplyError ... FAILED during APPLY`；`:3983` 是
`Client disconnected with reason: 打开带有高级数据的屏幕失败：...`）

关键点：这是**类加载期**失败，不是渲染期。`WaystoneButton` 第一次被加载发生在
「第一次打开传送列表、构造按钮」时，所以：

- 编译期毫无警告，`gradlew build` 全绿；
- 该 mixin 是 PR #14（实时变灰）引入的，此后一直只在测储罐界面，因此潜伏到
  今天第一次打开传送列表才炸。

## 修复

1. `mixin/WaystoneButtonClientMixin.java`：删除非法的 `@Shadow` 字段，改为经父类写入
   （字段是 public）：

   ```java
   ((AbstractWidget) (Object) this).active = false;
   ```

   `@Shadow @Final private WarpRequirement warpRequirement;` 保留——`javap` 核实
   `WaystoneButton` 自己声明了 `private final ... warpRequirement`，合法。

2. `src/test/java/com/olynk/waystonexppayment/mixin/MixinShadowPolicyTest.java`（新增）：
   扫描 mixin 源码里的 `@Mixin(X.class)` 与 `@Shadow` 成员，用
   `Class.forName(X, false, loader)` 校验每个被 shadow 的成员确实由目标类声明。
   这正是能在 CI 里拦住这次事故的检查（编译期拦不住，只有对照真实类才拦得住）。
   `build.gradle` 的 `test` 任务补上 `classpath += sourceSets.main.compileClasspath`：
   NeoForge 制品（带原版客户端类）默认只在编译期 classpath 上，否则这条检查会
   以「类加载不了」被跳过而不是真正执行。

3. `client/XppClientRequirements.java`：`[client] no payment tank for this entry` 这条日志
   此前把「这里根本没有罐」和「罐存在但为空」合并成同一句话，掩盖了两种不同状态
   （两者都表现为按钮置灰，但结论相反）。现在分别打印候选罐的状态：

   ```
   [client] no payment tank for this entry (from=..., target=...): under source=no tank, under target=empty tank at 19, -60, 8
   ```

## 验证

- `gradle build --offline`：BUILD SUCCESSFUL，55 个测试全绿（含新增守卫测试，1 个跳过项
  属于「当前没有 shadow 可查」时的 assumption 分支）。
- 守卫测试对照真实类运行通过（`skip=0`，说明它确实检查到了 `warpRequirement` 这一条，
  不是空转）。
- 反向验证（negative control）：临时把非法的 `@Shadow protected boolean active;` 放回去、
  只跑守卫测试 → 立刻失败，报

  ```
  WaystoneButtonClientMixin.java shadows 'active', but
  net.blay09.mods.waystones.client.gui.widget.WaystoneButton does not declare it.
  @Shadow only resolves the members the target class declares itself; an inherited one fails
  Mixin APPLY when the class is first loaded. Reach inherited members through a cast instead.
  ```

  随后撤销该临时改动并重新全量构建（恢复全绿）。这条证据同时印证了根因判断：
  目标类确实不声明 `active`。
- **未验证**：实机 mixin 应用（需玩家重启客户端后右键石碑复测）；本地没有可运行的
  客户端环境，`gradlew runClient` 会拉起完整整合包，故不作为常规验证手段。

## 取舍

- `waystone_xp_payment.mixins.json` 保持 `required: true`：一旦我们的 mixin 再出问题，
  客户端会**崩溃/断开**而不是静默降级。想改成「失败即降级」需要拆成两个 config
  （核心 API mixin 必需、纯界面刷新 mixin 非必需），这属于结构性改动且本地无法验证，
  本提交不动它。
