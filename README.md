# Waystone XP Payment

A NeoForge addon for [Waystones](https://github.com/TwelveIterations/Waystones) that pays a teleport's XP cost from
a **shared experience-fluid tank** placed directly below a teleport source, instead of from the travelling
player's own experience.

- Minecraft **1.21.1**, NeoForge **21.1.x**, Waystones **21.1.45 or newer** (built and tested against 21.1.46)
- One jar for both sides; every payment decision is made on the server

> **AI-generated project.** Nearly all of the code, documentation and commit messages here were produced by an AI
> coding agent under the repository owner's direction. See [Disclosure of AI-generated content](#disclosure-of-ai-generated-content).

## Why you want it

Waystones charges XP from the player who teleports. On a server that means every player has to build up their own
XP, and an XP farm cannot pay for anybody else's travel.

This mod turns teleport costs into a **shared, automatable resource**:

- one tank can fund a base, a teleport hub, or a whole shared waystone network;
- experience fluid from any tech mod — pipes, pumps, tanks — feeds it;
- who may draw from the pool is decided per player and per FTB team.

## Usage

1. Craft and place an **XP Payment Tank** (`waystone_xp_payment:xp_payment_tank`).
2. Put the teleport source **directly on top of it** — a waystone, warp plate, warp portal or sharestone all work.
3. Fill the tank with an accepted payment fluid: any pipe or pump can insert through the standard NeoForge fluid
   capability, or right-click it with a bucket.
4. Set the whitelist or blacklist for yourself, other players and teams (see [Access control](#access-control)).

After that, teleport as usual. The cost is paid in this order:

1. the tank **under the source** you are leaving,
2. whatever it cannot cover is paid by a tank **under the destination**,
3. whatever both miss is paid by the **player**,
4. if even that is not enough, the teleport is cancelled and **nothing** is consumed.

## Access control

Access control decides who may draw from the tank's XP pool. The list is either a whitelist or a blacklist, and its
entries are players or FTB Teams. With FTB Teams installed a whole team can be allowed or denied at once; without
it, team entries are unavailable and players are listed individually.

In game, right-click a tank to open its screen. `+ Add me` adds you, `+ Add teams` adds every team you are
currently in (disabled while you are in none), and the mode button switches between whitelist and blacklist. To add
somebody else, click the filter box and type: the list turns into candidates — online players (blue) and teams
(amber) — each carrying the UUID the server gave it. **A click only selects a row.** Acting on it means opening its
menu: right-click the row, or click the `···` in its corner, and pick *Add to list*, *Remove from list* or *View
team members*. `↑`/`↓` move the highlight, `Enter` activates the highlighted menu item, so nothing is destroyed by a
stray click. Typing only filters what the client was given — names never reach the server, so a candidate row is
the only way to add somebody other than yourself. `Esc` backs out one layer at a time (menu, member panel, filter)
and closes the screen last. The status line reports what the server answered for your last change (*Sending…*,
*Applied*, *Refused by the server*, *No answer from the server*). Hover a row to see its full UUID.

## Ownership

The owner of a tank is the player who placed it. Only the owner, and players sharing an FTB team with the owner, may
edit the list.

Everybody else opens the same screen in read-only mode: the rows stay readable, but the mode switch and the
add/remove controls are disabled, and the header shows whose tank it is.

A tank that predates the ownership rule — or was placed by something that is not a player — has no owner yet. It
stays editable by anyone until the first successful edit, and that edit claims it for whoever made it.

## Commands

Operators (permission level 2) can use these while looking at a tank, and from the console:

```
/waystonexppayment info
/waystonexppayment access mode <whitelist|blacklist>
/waystonexppayment access addplayer <name>
/waystonexppayment access removeplayer <name>
/waystonexppayment access addteam <name>
/waystonexppayment access removeteam <name>
```

The operator right is separate from ownership: it works regardless of who owns the tank.

## Installation

1. Install NeoForge 21.1.x for Minecraft 1.21.1.
2. Install [Waystones](https://modrinth.com/mod/waystones) 21.1.45 or newer (Balm comes with it).
3. Drop `waystone_xp_payment-<version>.jar` into your `mods` folder. The mod works on a server and on clients;
   installing it on both sides is recommended so tooltips and the tank screen match.

Optional: [FTB Teams](https://www.curseforge.com/minecraft/mc-mods/ftb-teams-forge) for team-based access.

## Configuration

`config/waystone_xp_payment-common.toml`:

```toml
[payment]
  skipPaymentInCreative = true

[tank]
  capacityMb = 16000               # 16 buckets, the ceiling; existing tanks keep their old capacity
  allowBucketInteraction = true
  # let the pool under the *destination* pay while its chunk is unloaded
  remoteDestinationPayments = true
  # chunk loads one teleport may spend looking for that destination pool (0-4)
  remotePaymentChunkLoadsPerTeleport = 1
```

Rates are **not** configured here any more: how much a fluid is worth is a datapack question.

## Registering fluids with a datapack or KubeJS

A fluid counts as experience only if a `waystone_xp_payment:xp_fluid` recipe declares it. The mod ships
declarations for the fluids it has been tested against, so ordinary setups need nothing.

```json
{ "type": "waystone_xp_payment:xp_fluid", "fluid": "create_enchantment_industry:experience", "xp_per_bucket": 1000 }
```
```js
// kubejs/server_scripts/waystone_xp_payment.js
ServerEvents.recipes(event => {
  event.custom({
    type: 'waystone_xp_payment:xp_fluid',
    fluid: 'create_enchantment_industry:experience',
    xp_per_bucket: 1000
  })
})
```

`xp_per_bucket` is how many XP one bucket (1000 mB) of the fluid is worth, so `1000` means 1 mB = 1 XP.
Declaring the same fluid again in a higher-priority pack **overrides** the earlier rate, which is how a pack
reprices a fluid; `"xp_per_bucket": 0` removes a builtin declaration instead. Only exact fluid ids are matched -
flowing variants follow their still id, and tags are not consulted. The full contract, including how packs
override each other and how a fluid that is declared nowhere is treated, is in
`docs/数据包契约-经验流体.md`. The three fluids shipped in the built-in table are listed there too; the procedure for
getting a new mod onto that list (confirm the exact id, then measure how much XP one bucket really is) is in
`docs/SOP-内置默认数据包.md`.

## Compatibility

- The tank exposes the standard NeoForge `Capabilities.FluidHandler.BLOCK`, so pipes and tanks from any tech mod
  (Create, Mekanism, Thermal, …) can fill it without a per-mod integration.
- The tank holds a single fluid at a time; a fluid that is not declared as experience is rejected.
- XP **level** requirements (`ExperienceLevelRequirement` in Waystones' `warpRequirements`) are not covered by the
  tank in this version; the player still pays those. The default Waystones configuration only uses XP points.

## Building from source

```bash
./gradlew build        # jar in build/libs
./gradlew runServer    # dev server smoke test (needs an eula.txt in run/)
./gradlew runClient
```

## Disclosure of AI-generated content

The project uses a fixed AI-assisted development workflow with explicit quality gates.

**Roles.** The repository owner makes every design and acceptance decision. The AI coding agent (DeepSeek
Harness) writes code, documentation and commit messages. Nothing merges without the owner reviewing it first.

**Code quality constraints.** A change has to pass four gates before it can merge: compilation, unit tests
(including a guard test that fails when a mixin `@Shadow` no longer matches its target — 140 tests across 22 suites
today), an RCON smoke test on a development server, and playtesting in a real modpack. Failing any gate rejects the
merge; "merge now, fix later" is not an option here.

**Project management discipline.** Commits are atomic and use conventional prefixes, and the documentation is
updated in the same change as the code it describes. Design and acceptance stay human decisions; the AI
implements, and a human reviews every change before it lands.

**Maintainability constraints.** Module boundaries stay explicit, and modules talk through events or narrow
interfaces. Configuration, data packs or KubeJS are preferred over hardcoded Java. Domain vocabulary and comments
stay consistent across the codebase. Changes to core logic ship with tests, and known technical debt is written
down instead of hidden.

## License

MIT. See [LICENSE](LICENSE).
