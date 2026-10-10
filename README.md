# Waystone XP Payment

A NeoForge addon for [Waystones](https://github.com/TwelveIterations/Waystones) that pays a teleport's XP
cost from a **shared experience-fluid tank** placed directly below the teleport source, instead of from the
player's own experience.

- Minecraft **1.21.1**, NeoForge **21.1.x**, Waystones **21.1.46+**
- Both sides (single jar); all payment logic runs on the server

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

Access control (who may draw from the pool) is a whitelist or blacklist of players and FTB Teams.
With the pools and teams mods installed it also works with FTB Teams; without it, team entries are simply
unavailable.

- In-game: `/waystonexppayment info` and `/waystonexppayment access ...` while looking at a tank (op level 2).
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

## License

MIT. See [LICENSE](LICENSE).
