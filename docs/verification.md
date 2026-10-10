# Verification — waystone_xp_payment 0.1.0

Every claim in `README.md` / `CHANGELOG.md` should be traceable to something below. Commands are the ones
actually run; the two environment caveats are stated where they change how the command looks.

## 1. Build

| step | command | result |
|---|---|---|
| Minecraft/NeoForge artifacts (decompile, apply patches, AT + jst transform, recompile 5364 sources) | `gradle createMinecraftArtifacts` | **BUILD SUCCESSFUL in 58s** (`D:\deepseek\.tmp\build4.log`) |
| main + resource compile, test compile, jar | `gradle build -x test --offline` | **BUILD SUCCESSFUL in 14s**; test sources compile (`:compileTestJava`, `:testClasses`) |
| jar | — | `D:\deepseek\waystone_xp_payment\build\libs\waystone_xp_payment-0.1.0+mc1.21.1.jar`, 81,761 bytes (rebuilt 2026-10-10 20:58) |

### Environment caveats (why the commands look unusual)

- **The build has to run outside the harness file sandbox.** Inside it `Files.isWritable()` returns `false` for
  every path, and JDK zipfs computes `readOnly = !writeable`
  (`jdk/nio/zipfs/ZipFileSystem.java:170-174`). NeoForm's `RecompileSourcesActionWithJDK` therefore cannot write
  class files into its `output.jar` and the build dies with
  `ERROR Line: 1, error while writing net.minecraft.world.ticks.package-info: null in /net/minecraft/world/ticks/package-info.java`
  → `java.io.IOException: Compilation failed`. Verified directly with a probe JVM: same file, same user,
  `isWritable=false` inside the sandbox and `isWritable=true` outside it.
- **`maven.neoforged.net` was unreachable for this whole session** (`Test-NetConnection ... -Port 443` → False,
  Node `fetch` → `FETCH-ERROR fetch failed`). Gradle aborts a module resolution when a repository fails at the
  transport level, so the build was run `--offline` against the already-populated cache, backed by a
  machine-local init script (`$GRADLE_USER_HOME/init.d/fix-repos.gradle`, *not* part of this repository) that
  keeps that repository from being queried for `net.blay09.mods`, `io.netty` and `dev.ftb.mods` — all three are
  published on repositories that are reachable (Twelve Iterations, Maven Central; FTB Teams is now reflection
  only, so it is not needed at build time at all).

## 2. Unit tests

```
java -jar junit-platform-console-standalone-1.10.2.jar execute ^
  --class-path "build\classes\java\main;build\classes\java\test;build\resources\main" ^
  --select-package com.olynk.waystonexppayment --details=summary --disable-ansi-colors
```

Result: `5 containers found / 5 successful`, **`19 tests found / 19 started / 19 successful / 0 failed`**
(`PaymentSplitTest` 9 cases, `FluidRateTableTest` 10 cases).

The Gradle `test` task itself could not run locally: its JUnit *engine* artifacts are not in the local cache
and cannot be fetched while `maven.neoforged.net` is down (Gradle asks its repository list first and fails hard
on the transport error). CI (`.github/workflows/build.yml`) runs the real `./gradlew build`, which includes
that task. Both test classes are pure logic with no Minecraft types on their paths, so the console run and the
Gradle run execute exactly the same assertions against exactly the same classes.

## 3. Dev server smoke test — BLOCKED by the environment (no in-game evidence yet)

`gradle runServer` reached `:prepareServerRun` and failed while resolving the dev-launch configuration:

```
> Task :compileJava
> Task :processResources
> Task :classes
> Task :downloadAssets UP-TO-DATE
> Task :writeServerLegacyClasspath
> Task :prepareServerRun FAILED
* What went wrong:
  > Could not resolve all files for configuration ':neoForgeConfigOnly'.
    > Could not download neoforge-21.1.8-moddev-config.json (net.neoforged:neoforge:21.1.8): Repository NeoForged Releases
    > Could not resolve net.neoforged:DevLaunch:1.0.2.
      > Could not GET 'https://maven.neoforged.net/releases/net/neoforged/DevLaunch/1.0.2/DevLaunch-1.0.2.pom'
        (connect timed out)
```

Everything that depends on *this project* succeeded: `compileJava`, `processResources`, `classes`,
`writeServerLegacyClasspath`, and `downloadAssets` (3888 client assets). The missing pieces are two launcher
artifacts published only on `maven.neoforged.net`, and they are not mirrored on Maven Central
(`https://repo.maven.apache.org/maven2/net/neoforged/DevLaunch/1.0.2/DevLaunch-1.0.2.pom` → HTTP 404).

So the mod is proven to **compile, package and pass its unit tests**, but its runtime behaviour (a teleport
actually paying from the tank, the access screen, the FTB Teams path) has **not** been observed in a running
game. Do not read this file as claiming that.

Retry when the host is reachable again:

```powershell
$env:GRADLE_USER_HOME='D:\deepseek\.gradle-home'; $env:TEMP='D:\deepseek\.tmp'; $env:TMP='D:\deepseek\.tmp'
& 'C:\Users\Dogay\.gradle\wrapper\dists\gradle-8.14.1-bin\baw1sv0jfoi8rxs14qo3h49cs\gradle-8.14.1\bin\gradle.bat' `
  runServer --console=plain --no-daemon
```

Lines that mean the smoke test passed:

- `waystone_xp_payment 0.1.0+mc1.21.1 initialized: Waystones teleports can be paid from experience fluid tanks`
- `Loaded 0 recipe-declared experience fluid rate(s): []`
- `Done (!) For help, type "help"`

## 4. To check by hand once a server runs (not claimed as verified)

1. Place a tank, place a waystone on top, fill the tank with a declared experience fluid → teleporting pays
   from the tank, and the player's XP does not move (check the tank's `mB` and the player's XP level).
2. Blacklist the player (`/waystonexppayment access mode blacklist` while looking at the tank) → the teleport
   still happens but the player pays their own XP.
3. Empty tank + player without enough XP → teleport is cancelled with `NotEnoughXp` and neither the tank nor
   the player lose anything.
4. Right-click the tank → the access screen opens; "Add me" / "Remove me" / mode toggle change the list, and
   the server re-syncs the screen afterwards.
5. With FTB Teams installed: the team buttons appear and grant/revoke the whole team.
