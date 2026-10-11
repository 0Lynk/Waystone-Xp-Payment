# Verification — waystone_xp_payment

Every claim in `README.md` / `CHANGELOG.md` should be traceable to something below. Commands are the ones
actually run; the environment caveats that change how a command looks are stated where they matter.

**Current release: 0.2.0.** Section 0 below is the record for it; sections 1-4 were written for 0.1.0 and are
kept as they stand. Where the two disagree on a number (test count, jar size), section 0 wins.

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
