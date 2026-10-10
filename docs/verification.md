# Verification — waystone_xp_payment 0.1.0

Every claim in `README.md` / `CHANGELOG.md` should be traceable to something below. Commands are the ones
actually run; the environment caveats that change how a command looks are stated where they matter.

## 1. Build

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

## 2. Unit tests

| runner | command | result |
|---|---|---|
| Gradle (canonical) | `gradle build` → `:test` | `FluidRateTableTest` 11/11, `PaymentSplitTest` 8/8 — **19 tests, 0 failures, 0 errors** (`build/test-results/test/*.xml`) |
| JUnit console (used while the repository was unreachable) | `java -jar junit-platform-console-standalone-1.10.2.jar execute --class-path "build\classes\java\main;build\classes\java\test" --select-package com.olynk.waystonexppayment` | 19 tests successful, 0 failed |

Both test classes are pure logic (no Minecraft types on their paths), which is why they can run either way.

## 3. Dev server smoke test — PASSED (with an explicit list of what it does *not* cover)

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

## 4. Retrying the whole thing

```powershell
$env:GRADLE_USER_HOME='D:\deepseek\.gradle-home'; $env:TEMP='D:\deepseek\.tmp'; $env:TMP='D:\deepseek\.tmp'
& 'C:\Users\Dogay\.gradle\wrapper\dists\gradle-8.14.1-bin\baw1sv0jfi8rxs14qo3h49cs\gradle-8.14.1\bin\gradle.bat' `
  build --console=plain --no-daemon          # tests included
& 'C:\Users\Dogay\.gradle\wrapper\dists\gradle-8.14.1-bin\baw1sv0jfi8rxs14qo3h49cs\gradle-8.14.1\bin\gradle.bat' `
  runServer --console=plain --no-daemon      # then query it over RCON on port 25575
```

Windows note: the build must not run inside the harness file sandbox (see section 1).
