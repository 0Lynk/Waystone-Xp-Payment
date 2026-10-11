# Waystone XP Payment

A NeoForge addon for [Waystones](https://github.com/TwelveIterations/Waystones) that pays a teleport's XP
cost from a **shared experience-fluid tank** placed directly below the teleport source, instead of from the
player's own experience.

- Minecraft **1.21.1**, NeoForge **21.1.x**, Waystones **21.1.46+**
- Both sides (single jar); all payment logic runs on the server

> **AI-generated project.** Nearly all of the code, documentation and commit messages here were produced by an
> AI coding agent (DeepSeek Harness) under the repository owner's direction. There is no GitHub-wide switch that
> marks a repository as AI-generated, so the disclosure lives in [AI disclosure](#ai-disclosure) below and in
> the release notes.

## Why you'd want it

Waystones charges the *teleporting player* in XP. On a server that means every player has to keep their own
XP buffer, and an XP farm cannot pay for anybody else's travel.

This mod turns the payment into a **communal, automatable resource**:

- one tank can fund a whole base, a public hub, or a shared waystone network;
- XP fluid coming from any tech mod (pipes, pumps, tanks) feeds it;
- access to the pool is controlled per player and per team.

## Usage

1. Craft and place an **XP Payment Tank** (`waystone_xp_payment:xp_payment_tank`).
2. Put a teleport source **directly on top of it** — a waystone, warp plate, warp portal or sharestone.
3. Fill the tank with an accepted experience fluid: any modded pipe/pump can insert through the standard
   NeoForge fluid capability, or you can right-click with a bucket.
4. Teleport. The tank pays first; anything it cannot cover is paid by the player. If neither covers the cost,
   the teleport is cancelled and **nothing** is consumed.

Access control (who may draw from the pool) is a whitelist or blacklist of players and FTB Teams. With FTB
Teams installed a tank can grant or revoke a whole team at once; without it, team entries are simply
unavailable and players are listed individually.

- In-game: right-click a tank to open its screen. The access list is one clickable list; players and teams are
  interleaved and told apart by colour (`[P]` rows blue, `[T]` rows amber). `+ Add me` adds you, `+ Add teams`
  adds every team you are currently in, `×` on a row removes that entry, and the mode button switches between
  whitelist and blacklist. Hovering a row shows the full UUID.
- Operators, or a console: `/waystonexppayment info` and `/waystonexppayment access ...` while looking at a
  tank (op level 2).
- Config and datapack/KubeJS declarations, see below.

## Installation

1. Install NeoForge 21.1.x for Minecraft 1.21.1.
2. Install [Waystones](https://modrinth.com/mod/waystones) 21.1.46 or newer (Balm comes with it).
3. Drop `waystone_xp_payment-<version>.jar` into your `mods` folder. It works on a server and on clients;
   installing it on both sides is recommended so the tooltip/config match.

Optional: [FTB Teams](https://www.curseforge.com/minecraft/mc-mods/ftb-teams-forge) for team-based access.

## Configuration

`config/waystone_xp_payment-common.toml`:

```toml
[payment]
  # fallback rate for fluids without an explicit entry, in mB per 1 XP
  mbPerXp = 1
  skipPaymentInCreative = true
  acceptUndeclaredFluids = false
  # "<fluid id or #tag>=<mB per XP>"; recipe-declared fluids are added on top of this list
  fluidConversions = ["create_enchantment_industry:experience=1", "#c:experience=1"]

[tank]
  capacityMb = 4000        # 4 buckets
  allowBucketInteraction = true
```

The default rate is **1 mB = 1 XP**, matching Create: Enchantment Industry's own experience fluid.

## Registering fluids with a datapack or KubeJS

A fluid counts as experience only if it is declared — either in the config list above, or by a
`waystone_xp_payment:xp_fluid` recipe (datapack JSON, or KubeJS):

```json
{ "type": "waystone_xp_payment:xp_fluid", "fluid": "create_enchantment_industry:experience", "mb_per_xp": 1 }
```
```json
{ "type": "waystone_xp_payment:xp_fluid", "tag": "c:experience", "mb_per_xp": 1 }
```
```js
// kubejs/server_scripts/waystone_xp_payment.js
ServerEvents.recipes(event => {
  event.custom({
    type: 'waystone_xp_payment:xp_fluid',
    fluid: 'create_enchantment_industry:experience',
    mb_per_xp: 1
  })
})
```

Lookup order: config by exact fluid id -> config by tag -> recipe by exact fluid id -> recipe by tag.
Setting `acceptUndeclaredFluids = true` makes any fluid accepted at `mbPerXp`.

## Compatibility

- The tank exposes the standard NeoForge `Capabilities.FluidHandler.BLOCK`, so pipes and tanks from any tech
  mod (Create, Mekanism, Thermal, ...) can fill it without a per-mod integration.
- The tank holds a single fluid at a time; a fluid that is not declared as experience is rejected.
- XP **level** requirements (`ExperienceLevelRequirement` in Waystones' `warpRequirements`) are not covered by
  the tank in this version; the player still pays those. The default Waystones configuration only uses XP points.

## Building from source

```bash
./gradlew build        # jar in build/libs
./gradlew runServer    # dev server smoke test (needs a eula.txt in run/)
./gradlew runClient
```

## AI disclosure

GitHub has no repository-level "AI-generated" flag, so this section (plus the same note in the release notes) is
the disclosure:

- **What**: nearly all of the code, documentation and commit messages in this repository were generated by an AI
  coding agent (DeepSeek Harness) under the repository owner's direction.
- **How it was checked**: every change was compiled with Gradle, covered by unit tests (25 today), smoke-tested on
  a dev server over RCON, and play-tested in a real 1.21.1 modpack; the bugs found that way (a right-click
  disconnect, a greyed-out teleport button, a tank that stopped paying after an unrecognized fluid) drove the
  fixes that are in this release.
- **What that means for you**: the design decisions and the acceptance testing were human, but the code and its
  wording are largely machine-produced - review before relying on it in production.

## License

MIT. See [LICENSE](LICENSE).
